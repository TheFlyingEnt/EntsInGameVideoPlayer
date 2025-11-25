package net.entsvideoplayer.client;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class FFmpegNativeLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger("FFmpegNativeLoader");
    private static final AtomicBoolean LOADED = new AtomicBoolean(false);
    private static final AtomicBoolean LOADING = new AtomicBoolean(false);
    
    // Configuration
    private static final String FFMPEG_VERSION = "6.1.1-1.5.10";
    private static final String MAVEN_REPO = "https://repo1.maven.org/maven2";
    private static final String GROUP_PATH = "org/bytedeco/ffmpeg";
    
    private final Path libDir;
    private final String platform;
    
    public FFmpegNativeLoader() {
        // Get mod config directory
        Path configDir = FabricLoader.getInstance().getConfigDir();
        this.libDir = configDir.getParent().resolve("mods").resolve("entsvideoplayer-lib");
        this.platform = detectPlatform();
        
        try {
            Files.createDirectories(libDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create lib directory", e);
        }
    }
    
    /**
     * Load FFmpeg natives synchronously (blocks until complete)
     */
    public boolean loadSync() {
        if (LOADED.get()) {
            return true;
        }
        
        if (!LOADING.compareAndSet(false, true)) {
            // Already loading in another thread, wait for it
            while (LOADING.get()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return LOADED.get();
        }
        
        try {
            LOGGER.info("Loading FFmpeg natives for platform: {}", platform);
            
            if (platform == null) {
                LOGGER.error("Unsupported platform");
                return false;
            }
            
            // Download all required JARs
            Path javacppJar = getOrDownloadJavaCppJar();
            Path javacvJar = getOrDownloadJavaCvJar();
            Path ffmpegJar = getOrDownloadFfmpegJar();
            Path ffmpegPlatformJar = getOrDownloadFfmpegPlatformJar();
            
            if (javacppJar == null || javacvJar == null || ffmpegJar == null || ffmpegPlatformJar == null) {
                return false;
            }
            
            // CRITICAL: Extract natives BEFORE adding to classpath
            if (!extractAndConfigureNatives(ffmpegPlatformJar)) {
                return false;
            }
            
            // Now add all JARs to classpath
            addToClasspath(javacppJar);
            addToClasspath(javacvJar);
            addToClasspath(ffmpegJar);
            addToClasspath(ffmpegPlatformJar);
            
            LOADED.set(true);
            LOGGER.info("FFmpeg loaded successfully");
            return true;
            
        } catch (Exception e) {
            LOGGER.error("Failed to load FFmpeg natives", e);
            return false;
        } finally {
            LOADING.set(false);
        }
    }
    
    /**
     * Load FFmpeg natives asynchronously
     */
    public CompletableFuture<Boolean> loadAsync(Consumer<LoadProgress> progressCallback) {
        return CompletableFuture.supplyAsync(() -> {
            if (LOADED.get()) {
                return true;
            }
            
            if (!LOADING.compareAndSet(false, true)) {
                while (LOADING.get()) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
                return LOADED.get();
            }
            
            try {
                progressCallback.accept(new LoadProgress("Checking FFmpeg natives...", 0));
                
                if (platform == null) {
                    LOGGER.error("Unsupported platform");
                    return false;
                }
                
                progressCallback.accept(new LoadProgress("Downloading JavaCPP...", 10));
                Path javacppJar = getOrDownloadJavaCppJar();
                if (javacppJar == null) return false;
                
                progressCallback.accept(new LoadProgress("Downloading JavaCV...", 20));
                Path javacvJar = getOrDownloadJavaCvJar();
                if (javacvJar == null) return false;
                
                progressCallback.accept(new LoadProgress("Downloading FFmpeg...", 35));
                Path ffmpegJar = getOrDownloadFfmpegJar();
                if (ffmpegJar == null) return false;
                
                progressCallback.accept(new LoadProgress("Downloading FFmpeg natives...", 50));
                Path ffmpegPlatformJar = getOrDownloadFfmpegPlatformJar();
                if (ffmpegPlatformJar == null) return false;
                
                progressCallback.accept(new LoadProgress("Extracting natives...", 65));
                if (!extractAndConfigureNatives(ffmpegPlatformJar)) {
                    return false;
                }
                
                progressCallback.accept(new LoadProgress("Loading into classpath...", 80));
                addToClasspath(javacppJar);
                addToClasspath(javacvJar);
                addToClasspath(ffmpegJar);
                addToClasspath(ffmpegPlatformJar);
                
                progressCallback.accept(new LoadProgress("Complete!", 100));
                LOADED.set(true);
                return true;
                
            } catch (Exception e) {
                LOGGER.error("Failed to load FFmpeg natives", e);
                return false;
            } finally {
                LOADING.set(false);
            }
        });
    }
    
    private Path getOrDownloadJavaCppJar() {
        String jarName = "javacpp-1.5.10.jar";
        Path jarPath = libDir.resolve(jarName);
        
        if (Files.exists(jarPath)) {
            LOGGER.info("JavaCPP JAR already exists: {}", jarPath);
            return jarPath;
        }
        
        String downloadUrl = "https://repo1.maven.org/maven2/org/bytedeco/javacpp/1.5.10/javacpp-1.5.10.jar";
        LOGGER.info("Downloading JavaCPP from: {}", downloadUrl);
        
        try {
            downloadFile(downloadUrl, jarPath);
            return jarPath;
        } catch (IOException e) {
            LOGGER.error("Failed to download JavaCPP JAR", e);
            return null;
        }
    }
    
    private Path getOrDownloadJavaCvJar() {
        String jarName = "javacv-1.5.10.jar";
        Path jarPath = libDir.resolve(jarName);
        
        if (Files.exists(jarPath)) {
            LOGGER.info("JavaCV JAR already exists: {}", jarPath);
            return jarPath;
        }
        
        String downloadUrl = "https://repo1.maven.org/maven2/org/bytedeco/javacv/1.5.10/javacv-1.5.10.jar";
        LOGGER.info("Downloading JavaCV from: {}", downloadUrl);
        
        try {
            downloadFile(downloadUrl, jarPath);
            return jarPath;
        } catch (IOException e) {
            LOGGER.error("Failed to download JavaCV JAR", e);
            return null;
        }
    }
    
    private Path getOrDownloadFfmpegJar() {
        String jarName = String.format("ffmpeg-%s.jar", FFMPEG_VERSION);
        Path jarPath = libDir.resolve(jarName);
        
        if (Files.exists(jarPath)) {
            LOGGER.info("FFmpeg JAR already exists: {}", jarPath);
            return jarPath;
        }
        
        String downloadUrl = String.format("%s/%s/%s/ffmpeg-%s.jar",
            MAVEN_REPO, GROUP_PATH, FFMPEG_VERSION, FFMPEG_VERSION);
        
        LOGGER.info("Downloading FFmpeg JAR from: {}", downloadUrl);
        
        try {
            downloadFile(downloadUrl, jarPath);
            return jarPath;
        } catch (IOException e) {
            LOGGER.error("Failed to download FFmpeg JAR", e);
            return null;
        }
    }
    
    private Path getOrDownloadFfmpegPlatformJar() {
        String jarName = String.format("ffmpeg-%s-%s.jar", FFMPEG_VERSION, platform);
        Path jarPath = libDir.resolve(jarName);
        
        if (Files.exists(jarPath)) {
            LOGGER.info("FFmpeg platform JAR already exists: {}", jarPath);
            return jarPath;
        }
        
        String downloadUrl = String.format("%s/%s/%s/ffmpeg-%s-%s.jar",
            MAVEN_REPO, GROUP_PATH, FFMPEG_VERSION, FFMPEG_VERSION, platform);
        
        LOGGER.info("Downloading FFmpeg platform JAR from: {}", downloadUrl);
        
        try {
            downloadFile(downloadUrl, jarPath);
            return jarPath;
        } catch (IOException e) {
            LOGGER.error("Failed to download FFmpeg platform JAR", e);
            return null;
        }
    }
    
    private void downloadFile(String url, Path destination) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestProperty("User-Agent", "Minecraft-Fabric-Mod");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);
        
        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("Failed to download: HTTP " + responseCode);
        }
        
        long fileSize = conn.getContentLengthLong();
        LOGGER.info("Downloading {} bytes...", fileSize);
        
        try (InputStream in = conn.getInputStream();
             OutputStream out = Files.newOutputStream(destination)) {
            
            byte[] buffer = new byte[8192];
            long downloaded = 0;
            int read;
            
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                downloaded += read;
                
                if (fileSize > 0 && downloaded % (1024 * 1024) == 0) {
                    LOGGER.info("Downloaded: {} MB / {} MB", 
                        downloaded / (1024 * 1024), fileSize / (1024 * 1024));
                }
            }
        }
        
        LOGGER.info("Download complete: {}", destination);
    }
    
    private boolean extractAndConfigureNatives(Path jarPath) {
        try {
            Path nativesDir = libDir.resolve("natives").resolve(platform);
            Files.createDirectories(nativesDir);
            
            LOGGER.info("Configuring JavaCPP to use natives from: {}", nativesDir);
            
            // CRITICAL: Set these BEFORE any FFmpeg classes load
            // JavaCPP will look for natives in cachedir/platform/
            System.setProperty("org.bytedeco.javacpp.cachedir", nativesDir.getParent().toString());
            System.setProperty("org.bytedeco.javacpp.cachesubdir", platform);
            
            // Also set the platform explicitly
            System.setProperty("org.bytedeco.javacpp.platform", platform);
            
            // Tell JavaCPP to look in our custom location first
            System.setProperty("org.bytedeco.javacpp.pathsFirst", nativesDir.toAbsolutePath().toString());
            
            LOGGER.info("Extracting natives to: {}", nativesDir);
            
            // Extract natives from the JAR
            try (FileSystem jarFs = FileSystems.newFileSystem(jarPath, (ClassLoader) null)) {
                boolean foundNatives = false;
                
                for (Path rootDir : jarFs.getRootDirectories()) {
                    Files.walk(rootDir)
                        .filter(p -> {
                            String name = p.toString().toLowerCase();
                            return name.endsWith(".dll") || 
                                   name.endsWith(".so") || 
                                   name.endsWith(".dylib") ||
                                   name.endsWith(".jnilib");
                        })
                        .forEach(nativePath -> {
                            try {
                                String fileName = nativePath.getFileName().toString();
                                Path targetPath = nativesDir.resolve(fileName);
                                
                                if (!Files.exists(targetPath)) {
                                    Files.copy(nativePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                                    LOGGER.info("Extracted native: {}", fileName);
                                }
                            } catch (IOException e) {
                                LOGGER.error("Failed to extract native: {}", nativePath, e);
                            }
                        });
                    
                    try (var stream = Files.list(nativesDir)) {
                        if (stream.findAny().isPresent()) {
                            foundNatives = true;
                        }
                    }
                }
                
                if (!foundNatives) {
                    LOGGER.error("No native libraries found in JAR!");
                    return false;
                }
            }
            
            // Update java.library.path for good measure
            String nativePath = nativesDir.toAbsolutePath().toString();
            String existingPath = System.getProperty("java.library.path", "");
            
            if (existingPath.isEmpty()) {
                System.setProperty("java.library.path", nativePath);
            } else if (!existingPath.contains(nativePath)) {
                System.setProperty("java.library.path", nativePath + File.pathSeparator + existingPath);
            }
            
            // Force reload of java.library.path
            try {
                var sysPathsField = ClassLoader.class.getDeclaredField("sys_paths");
                sysPathsField.setAccessible(true);
                sysPathsField.set(null, null);
            } catch (Exception e) {
                LOGGER.warn("Could not reset sys_paths", e);
            }
            
            LOGGER.info("=== Native Configuration Complete ===");
            LOGGER.info("Natives directory: {}", nativePath);
            LOGGER.info("java.library.path: {}", System.getProperty("java.library.path"));
            LOGGER.info("javacpp.cachedir: {}", System.getProperty("org.bytedeco.javacpp.cachedir"));
            LOGGER.info("javacpp.platform: {}", System.getProperty("org.bytedeco.javacpp.platform"));
            LOGGER.info("javacpp.pathsFirst: {}", System.getProperty("org.bytedeco.javacpp.pathsFirst"));
            
            // List all DLL files found
            LOGGER.info("Native files extracted:");
            try (var stream = Files.list(nativesDir)) {
                stream.forEach(p -> LOGGER.info("  - {}", p.getFileName()));
            }
            
            return true;
            
        } catch (Exception e) {
            LOGGER.error("Failed to extract and configure natives", e);
            return false;
        }
    }
    
    private void addToClasspath(Path jarPath) throws Exception {
        // Get Fabric's KnotClassLoader
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        
        // Try to find addURL method in the classloader hierarchy
        Class<?> loaderClass = loader.getClass();
        
        try {
            // Fabric's KnotClassLoader has addURL method
            var addUrlMethod = loaderClass.getMethod("addURL", java.net.URL.class);
            addUrlMethod.invoke(loader, jarPath.toUri().toURL());
            LOGGER.info("Added to classpath via {}: {}", loaderClass.getSimpleName(), jarPath);
        } catch (NoSuchMethodException e) {
            // Fallback: try to find it in parent classloaders
            ClassLoader current = loader;
            boolean added = false;
            
            while (current != null && !added) {
                try {
                    var addUrlMethod = current.getClass().getDeclaredMethod("addURL", java.net.URL.class);
                    addUrlMethod.setAccessible(true);
                    addUrlMethod.invoke(current, jarPath.toUri().toURL());
                    LOGGER.info("Added to classpath via {}: {}", current.getClass().getSimpleName(), jarPath);
                    added = true;
                } catch (NoSuchMethodException ex) {
                    current = current.getParent();
                }
            }
            
            if (!added) {
                throw new RuntimeException("Could not find a way to add JAR to classpath");
            }
        }
    }
    
    private String detectPlatform() {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();
        
        // Normalize architecture
        if (arch.equals("amd64") || arch.equals("x86_64")) {
            arch = "x86_64";
        } else if (arch.equals("aarch64") || arch.equals("arm64")) {
            arch = "arm64";
        }
        
        if (os.contains("win")) {
            return "windows-" + arch;
        } else if (os.contains("mac")) {
            return "macosx-" + arch;
        } else if (os.contains("linux")) {
            return "linux-" + arch;
        }
        
        LOGGER.warn("Unknown platform: {} {}", os, arch);
        return null;
    }
    
    public boolean isLoaded() {
        return LOADED.get();
    }
    
    public boolean isLoading() {
        return LOADING.get();
    }
    
    /**
     * Progress information for async loading
     */
    public record LoadProgress(String message, int percentage) {}
}