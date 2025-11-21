package net.entsvideoplayer.api;

import net.entsvideoplayer.EntsVideoPlayer;
import net.entsvideoplayer.network.PlayCutscenePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public class EntsVideoAPIImpl implements EntsVideoAPI{
    public static final Identifier CHANNEL = Identifier.of(EntsVideoPlayer.MOD_ID, "play_cutscene");

    @SuppressWarnings("deprecation")
    @Override
    public void playCutsceneLocal(String source, boolean isUrl, boolean disableMovement, boolean hideHud) {
        // client thread call expected: forward to CutsceneManager
        CutsceneManager.playCutscene(source, disableMovement, hideHud);
    }

    @Override
    public void requestPlayCutsceneForPlayer(ServerPlayerEntity player, String source, boolean isUrl, boolean disableMovement, boolean hideHud) {
        // Build PacketByteBuf
        PlayCutscenePayload packet = new PlayCutscenePayload(source, isUrl, disableMovement, hideHud);
        ServerPlayNetworking.send(player, packet);
    }
}
