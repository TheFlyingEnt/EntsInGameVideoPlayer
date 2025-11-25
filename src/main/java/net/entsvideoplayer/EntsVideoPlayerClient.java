package net.entsvideoplayer;

import org.lwjgl.glfw.GLFW;

import net.entsvideoplayer.api.CutsceneManager;
import net.entsvideoplayer.client.FFmpegNativeLoader;
import net.entsvideoplayer.network.CutsceneNetworkClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;

public class EntsVideoPlayerClient implements ClientModInitializer {

    private static KeyBinding skipKeyBinding;

    @Override
    public void onInitializeClient() {

        FFmpegNativeLoader loader = new FFmpegNativeLoader();
    
        if (loader.loadSync()) {
            EntsVideoPlayer.LOGGER.info("FFmpeg loaded successfully");
        } else {
            EntsVideoPlayer.LOGGER.error("Failed to load FFmpeg - some features may not work");
        }

        skipKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.entcutscene.skip",
            GLFW.GLFW_KEY_ESCAPE,
            KeyBinding.Category.MISC
        ));

        // Client tick event for handling key presses
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (skipKeyBinding.wasPressed()) {
                if (CutsceneManager.isPlaying()) {
                    CutsceneManager.stopCutscene();
                }
            }
        });

        CutsceneNetworkClient.registerReceiver();
    }
    
}
