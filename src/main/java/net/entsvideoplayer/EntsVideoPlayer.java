package net.entsvideoplayer;

import net.entsvideoplayer.api.CutSceneCommand;
import net.entsvideoplayer.network.PlayCutscenePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EntsVideoPlayer implements ModInitializer {
	public static final String MOD_ID = "entsvideoplayer";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Staring Ent's Video Player");

		PayloadTypeRegistry.playS2C().register(
			PlayCutscenePayload.ID,
			PlayCutscenePayload.CODEC
		);

		CutSceneCommand.register();

		
		
	}
}