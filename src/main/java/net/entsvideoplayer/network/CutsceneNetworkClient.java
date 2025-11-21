package net.entsvideoplayer.network;

import net.entsvideoplayer.api.CutsceneManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class CutsceneNetworkClient {

    public static void registerReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(
            PlayCutscenePayload.ID,
            (packet, context) -> {
                var client = context.client();
                client.execute(() -> {
                    CutsceneManager.playCutscene(packet.videoPath(), packet.isURL(), packet.disableMovement(), packet.hideHud());
                });
            }
        );
    }
}
