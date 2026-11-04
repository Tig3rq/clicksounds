package pl.clicksounds;

import net.fabricmc.api.ClientModInitializer;

public class ClickSoundsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClickSounds.init();
    }
}
