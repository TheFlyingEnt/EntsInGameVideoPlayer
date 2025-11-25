package net.entsvideoplayer.api;

import java.io.FileNotFoundException;

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
        try {
            CutsceneManager.playCutscene(source, disableMovement, hideHud);
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override
    public void requestPlayCutsceneForPlayer(ServerPlayerEntity player, String source, int type, boolean disableMovement, boolean hideHud) {
        // Build PacketByteBuf
        PlayCutscenePayload packet = new PlayCutscenePayload(source, type, disableMovement, hideHud);
        ServerPlayNetworking.send(player, packet);
    }
}
