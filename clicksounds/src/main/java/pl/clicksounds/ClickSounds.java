package pl.clicksounds;

import javax.sound.sampled.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Dzwieki generowane w kodzie (bez plikow), wiec mod dziala od razu.
 * Ustaw glosnosc ponizej (0.0 - 1.0).
 */
public class ClickSounds {
    // ====== USTAWIENIA ======
    public static final float KEY_VOLUME = 1.0f;
    public static final float MOUSE_VOLUME = 1.0f;
    public static final int VARIANTS = 16;          // ile roznych dzwiekow na kazdy typ
    // ========================

    private static final float SR = 44100f;
    private static final AudioFormat FORMAT = new AudioFormat(SR, 16, 1, true, false);

    private static final List<byte[]> KEY_DOWN = new ArrayList<>();
    private static final List<byte[]> KEY_UP = new ArrayList<>();
    private static final List<byte[]> MOUSE_DOWN = new ArrayList<>();
    private static final List<byte[]> MOUSE_UP = new ArrayList<>();

    private static final ExecutorService EXEC = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "clicksounds");
        t.setDaemon(true);
        return t;
    });
    private static final Random RND = new Random();
    private static int lastKey = -1, lastMouse = -1;

    public static void init() {
        Random r = new Random(2024);
        for (int i = 0; i < VARIANTS; i++) {
            KEY_DOWN.add(gen(r, 110 + r.nextInt(170), 1800 + r.nextInt(2600), 0.085f, 1.0f));   // "plum/thock"
            KEY_UP.add(gen(r, 180 + r.nextInt(200), 2500 + r.nextInt(2500), 0.05f, 0.55f));
            MOUSE_DOWN.add(gen(r, 380 + r.nextInt(500), 3000 + r.nextInt(3000), 0.045f, 1.0f)); // ostrzejszy "klik"
            MOUSE_UP.add(gen(r, 500 + r.nextInt(500), 3500 + r.nextInt(3000), 0.03f, 0.5f));
        }
    }

    public static void keyboard(boolean press) {
        int i = pick(KEY_DOWN.size(), true);
        play((press ? KEY_DOWN : KEY_UP).get(i), KEY_VOLUME);
    }

    public static void mouse(boolean press) {
        int i = pick(MOUSE_DOWN.size(), false);
        play((press ? MOUSE_DOWN : MOUSE_UP).get(i), MOUSE_VOLUME);
    }

    private static int pick(int n, boolean key) {
        if (n == 0) return 0;
        int i;
        do { i = RND.nextInt(n); } while (n > 1 && i == (key ? lastKey : lastMouse));
        if (key) lastKey = i; else lastMouse = i;
        return i;
    }

    /** Pojedynczy dzwiek: gleboki "plum" + trzask + wysoki klik. */
    private static byte[] gen(Random r, double bodyHz, double clackHz, float length, float amp) {
        int n = (int) (SR * length);
        double[] s = new double[n];
        double decay = 35 + r.nextInt(40);
        double noiseAmt = 0.25 + r.nextDouble() * 0.35;
        double clackAmt = 0.2 + r.nextDouble() * 0.4;
        double peak = 0;
        for (int i = 0; i < n; i++) {
            double t = i / SR;
            double body = Math.sin(2 * Math.PI * bodyHz * t * (1 - 0.3 * t / length)) * Math.exp(-t * decay);
            double noise = (r.nextDouble() * 2 - 1) * Math.exp(-t * 700) * noiseAmt;
            double clack = Math.sin(2 * Math.PI * clackHz * t) * Math.exp(-t * 350) * clackAmt;
            s[i] = body + noise + clack;
            peak = Math.max(peak, Math.abs(s[i]));
        }
        byte[] out = new byte[n * 2];
        for (int i = 0; i < n; i++) {
            short v = (short) (s[i] / peak * 0.9 * amp * 32767);
            out[2 * i] = (byte) (v & 0xff);
            out[2 * i + 1] = (byte) ((v >> 8) & 0xff);
        }
        return out;
    }

    private static void play(byte[] data, float volume) {
        EXEC.execute(() -> {
            try {
                Clip clip = AudioSystem.getClip();
                clip.open(FORMAT, data, 0, data.length);
                if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl g = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                    float db = (float) (20 * Math.log10(Math.max(volume, 0.0001f)));
                    g.setValue(Math.max(g.getMinimum(), Math.min(g.getMaximum(), db)));
                }
                clip.addLineListener(e -> {
                    if (e.getType() == LineEvent.Type.STOP) clip.close();
                });
                clip.start();
            } catch (Throwable ignored) {
            }
        });
    }
}
