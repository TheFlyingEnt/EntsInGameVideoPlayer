package net.entsvideoplayer.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

public class CutsceneNetworkServer {

    public static void sendCutscene(ServerPlayerEntity player, String videoPath, int type, boolean disableMovement, boolean hideHud) {
        PlayCutscenePayload packet = new PlayCutscenePayload(videoPath, type, disableMovement, hideHud);

        // Send via the CustomPayload API
        ServerPlayNetworking.send(player, packet);
    }
}