package net.entsvideoplayer.api;
import net.minecraft.server.network.ServerPlayerEntity;

public interface EntsVideoAPI {
    /**
     * Client-side: play a local path or a URL on the current client.
     * (Must run on client thread).
     */
    void playCutsceneLocal(String source, boolean isUrl, boolean disableMovement, boolean hideHud);

    /**
     * Server-side helper: request that the given server player start playing the cutscene.
     * The implementation should send a S2C packet to the player.
     */
    void requestPlayCutsceneForPlayer(ServerPlayerEntity player, String source, int type, boolean disableMovement, boolean hideHud);

    /**
     * @return global singleton (may be null early in init).
     */
    static EntsVideoAPI get() {
        return Holder.INSTANCE;
    }

    class Holder {
        // Implementation fills this field during mod initialization.
        public static EntsVideoAPI INSTANCE = null;
    }
}
