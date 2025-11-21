package net.entsvideoplayer.api;

import net.entsvideoplayer.client.CutsceneScreen;
import net.minecraft.client.MinecraftClient;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CutsceneManager {
    private static CutsceneScreen currentCutscene = null;
    private static boolean playerMovementDisabled = false;
    private static boolean hideGui = false;

    /**
     * Play a cutscene from either a URL or local file
     * @param pathOrUrl The file path or URL to the video
     * @param isURL Whether this is a URL (true) or local file (false)
     * @param disableMovement Whether to disable player movement during cutscene
     * @param hideHud Whether to hide the HUD during cutscene
     */
    public static void playCutscene(String pathOrUrl, boolean isURL, boolean disableMovement, boolean hideHud) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        String videoPath;
        if (isURL) {
            // Use URL directly
            videoPath = pathOrUrl.replace("\"", "");
        } else {
            // Get cutscene file path from config/cutscenes folder
            Path cutscenePath = Paths.get("config", "cutscenes", pathOrUrl.replace("\"", ""));
            videoPath = cutscenePath.toString();
        }
        
        playerMovementDisabled = disableMovement;
        hideGui = hideHud;

        // Create and show cutscene screen
        client.execute(() -> {
            currentCutscene = new CutsceneScreen(videoPath, disableMovement, hideHud);
            client.setScreen(currentCutscene);
        });
    }

    /**
     * Legacy method - auto-detects if path is URL
     * @deprecated Use playCutscene(String, boolean, boolean, boolean) instead
     */
    @Deprecated
    public static void playCutscene(String filename, boolean disableMovement, boolean hideHud) {
        boolean isURL = filename.startsWith("https://") || filename.startsWith("rtmp://");
        playCutscene(filename, isURL, disableMovement, hideHud);
    }

    public static void stopCutscene() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && currentCutscene != null) {
            CutsceneScreen screenToClose = currentCutscene;
            currentCutscene = null; // Clear reference FIRST to prevent recursion
            
            client.execute(() -> {
                if (screenToClose != null) {
                    screenToClose.cleanup(); // Use cleanup instead of close
                }
                client.setScreen(null);
            });
        }
    }

    public static boolean isPlaying() {
        return currentCutscene != null;
    }

    public static boolean isPlayerMovementDisabled() {
        return playerMovementDisabled && isPlaying();
    }

    public static boolean shouldHideGui() {
        return hideGui && isPlaying();
    }
}