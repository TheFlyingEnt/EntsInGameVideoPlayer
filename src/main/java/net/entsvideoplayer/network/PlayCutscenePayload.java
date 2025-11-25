package net.entsvideoplayer.network;

import net.entsvideoplayer.EntsVideoPlayer;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record PlayCutscenePayload(String videoPath, int type, boolean disableMovement, boolean hideHud) implements CustomPayload {
    public static final CustomPayload.Id<PlayCutscenePayload> ID =
        new CustomPayload.Id<>(Identifier.of(EntsVideoPlayer.MOD_ID, "play_cutscene"));
    
    public static final PacketCodec<RegistryByteBuf, PlayCutscenePayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.STRING, PlayCutscenePayload::videoPath,
            PacketCodecs.INTEGER, PlayCutscenePayload::type,
            PacketCodecs.BOOLEAN, PlayCutscenePayload::disableMovement,
            PacketCodecs.BOOLEAN, PlayCutscenePayload::hideHud,
            PlayCutscenePayload::new
        );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
