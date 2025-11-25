package net.entsvideoplayer.network;

import java.io.FileNotFoundException;

import net.entsvideoplayer.api.CutsceneManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class CutsceneNetworkClient {

    public static void registerReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(
            PlayCutscenePayload.ID,
            (packet, context) -> {
                var client = context.client();
                client.execute(() -> {
                    try {
                        CutsceneManager.playCutscene(packet.videoPath(), packet.type(), packet.disableMovement(), packet.hideHud());
                    } catch (FileNotFoundException e) {
                        System.err.println("Failed to load resource video: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
            }
        );
    }
}
