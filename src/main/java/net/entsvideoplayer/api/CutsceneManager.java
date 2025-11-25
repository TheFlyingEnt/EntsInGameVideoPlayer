package net.entsvideoplayer.api;

import net.entsvideoplayer.client.CutsceneScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

public class CutsceneManager {
    private static CutsceneScreen currentCutscene = null;
    private static boolean playerMovementDisabled = false;
    private static boolean hideGui = false;

    /**
     * Play a cutscene from either a URL or local file
     * @param location The file path or URL to the video
     * @param type Whether this is a URL (0) or local file (1) or pack file (2)
     * @param disableMovement Whether to disable player movement during cutscene
     * @param hideHud Whether to hide the HUD during cutscene
     */
    public static void playCutscene(String location, int type, boolean disableMovement, boolean hideHud) throws FileNotFoundException {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        String videoPath;
        if (type == 0) {
            // Use URL directly
            videoPath = location.replace("\"", "");
        } else if (type == 1){
            // Get cutscene file path from config/cutscenes folder
            Path cutscenePath = Paths.get("config", "entsvideoplayer", location.replace("\"", ""));
            videoPath = cutscenePath.toString();
        } else if (type == 2) {
            // Get video from resource pack using namespace:path format
            // Example: test:testvideo.mp4 -> assets/test/videos/testvideo.mp4
            String cleanLocation = location.replace("\"", "");
            String[] parts = cleanLocation.split(":", 2);
            
            if (parts.length != 2) {
                // Invalid format, fallback or log error
                System.err.println("Invalid resource location format. Expected 'namespace:filename', got: " + cleanLocation);
                return;
            }
            
            String namespace = parts[0];
            String filename = parts[1];
            
            // Try to extract the resource to a temporary location
            try {
                Identifier resourceId = Identifier.of(namespace, "videos/" + filename);
                Optional<Resource> resourceOptional = client.getResourceManager().getResource(resourceId);
                
                if (resourceOptional.isPresent()) {
                    Resource resource = resourceOptional.get();
                    
                    // Create temp file to store the video
                    Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "entsvideoplayer");
                    Files.createDirectories(tempDir);
                    Path tempFile = tempDir.resolve(namespace + "_" + filename);
                    
                    // Copy resource to temp file
                    try (InputStream inputStream = resource.getInputStream()) {
                        Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
                    }
                    
                    videoPath = tempFile.toString();
                } else {
                    System.err.println("Resource not found: " + resourceId);
                    return;
                }
            } catch (Exception e) {
                System.err.println("Failed to load resource video: " + e.getMessage());
                e.printStackTrace();
                return;
            }
        } else {
            System.err.println("Invalid type: " + type);
            return;
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
     * Only Supports URL and Local
     * @throws FileNotFoundException 
     * @deprecated Use playCutscene(String, boolean, boolean, boolean) instead
     */
    @Deprecated
    public static void playCutscene(String filename, boolean disableMovement, boolean hideHud) throws FileNotFoundException {
        boolean isURL = filename.startsWith("https://") || filename.startsWith("rtmp://");
        int fix = 1;
        if (isURL) fix = 0;
        playCutscene(filename, fix, disableMovement, hideHud);
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