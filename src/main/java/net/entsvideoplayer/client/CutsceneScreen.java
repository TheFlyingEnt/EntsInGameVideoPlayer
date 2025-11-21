package net.entsvideoplayer.client;

import net.entsvideoplayer.EntsVideoPlayer;
import net.entsvideoplayer.api.CutsceneManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import javax.sound.sampled.*;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class CutsceneScreen extends Screen {
    private static final Identifier TEXTURE_ID = Identifier.of(EntsVideoPlayer.MOD_ID, "cutscene_frame");

    private final String videoPath;
    private final boolean disableMovement;
    private final boolean hideHud;

    private FFmpegFrameGrabber grabber;
    private NativeImageBackedTexture videoTexture;
    private Thread videoThread;
    private volatile boolean running = true;
    private volatile boolean hasFinished = false;
    private volatile boolean firstVideoFrameSeen = false;
    
    @SuppressWarnings("unused")
    private long startClockNanos = -1;
    @SuppressWarnings("unused")
    private long videoStartTimestampUs = -1;

    private int videoWidth = 1920;
    private int videoHeight = 1080;

    private SourceDataLine audioLine;

    // Queue of Frames decoded by the worker thread (holds up to 3 frames)
    //private final BlockingQueue<Frame> frameQueue = new LinkedBlockingQueue<>(3);
    private BlockingQueue<RawFrame> frameQueue = new LinkedBlockingQueue<>(3);

    // Timing / scheduling fields (time-based scheduler)
    private volatile long frameDurationNano = 33_333_333L; // default ~30 FPS
    private volatile long nextFrameTimeNano = 0L; // when to show next frame (nano)
    private volatile RawFrame lastFrame = null; // last frame that was displayed

    public CutsceneScreen(String videoPath, boolean disableMovement, boolean hideHud) {
        super(Text.literal("Cutscene"));
        this.videoPath = videoPath;
        this.disableMovement = disableMovement;
        this.hideHud = hideHud;

    }

    @Override
    protected void init() {
        super.init();

        try {
            // Check if videoPath is a URL or local file
            boolean isURL = videoPath.startsWith("https://") || videoPath.startsWith("rtmp://");
            
            if (!isURL) {
                // Local file - check if it exists
                File videoFile = new File(videoPath);
                if (!videoFile.exists()) {
                    EntsVideoPlayer.LOGGER.error("Video file not found: " + videoPath);
                    close();
                    return;
                }
                EntsVideoPlayer.LOGGER.info("Loading local video: " + videoPath);
            } else {
                EntsVideoPlayer.LOGGER.info("Streaming video from URL: " + videoPath);
            }

            // Initialize FFmpeg grabber

            grabber = new FFmpegFrameGrabber(videoPath);

            // Force BGR24 so converter gives BufferedImage in a consistent RGB form
            grabber.setPixelFormat(org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGR24);

            grabber.start();

            videoWidth = grabber.getImageWidth();
            videoHeight = grabber.getImageHeight();

            double fps = grabber.getFrameRate();
            if (fps <= 0 || Double.isNaN(fps) || Double.isInfinite(fps)) {
                fps = 30.0; // fallback
            }
            frameDurationNano = (long) (1_000_000_000.0 / fps);
            // nextFrameTimeNano is initialized when first frame arrives (below)

            EntsVideoPlayer.LOGGER.info("Video info: {}x{} @ {}fps, {} audio channels @ {}Hz",
                    videoWidth, videoHeight, fps,
                    grabber.getAudioChannels(), grabber.getSampleRate());

            //converter = new Java2DFrameConverter();

            // Create texture for video frames with proper format
            videoTexture = new NativeImageBackedTexture(() -> "cutscene_frame", videoWidth, videoHeight, false);
            MinecraftClient.getInstance().getTextureManager().registerTexture(TEXTURE_ID, videoTexture);

            // Initialize with black frame
            NativeImage nativeImage = videoTexture.getImage();
            if (nativeImage != null) {
                for (int y = 0; y < videoHeight; y++) {
                    for (int x = 0; x < videoWidth; x++) {
                        nativeImage.setColor(x, y, 0xFF000000); // Black with full alpha
                    }
                }
                videoTexture.upload();
            }

            EntsVideoPlayer.LOGGER.info("Created texture: {}x{}", videoWidth, videoHeight);

            // Setup audio if available
            if (grabber.getAudioChannels() > 0) {
                try {
                    AudioFormat audioFormat = new AudioFormat(
                            (float) grabber.getSampleRate(),
                            16,
                            grabber.getAudioChannels(),
                            true,
                            false
                    );
                    DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
                    audioLine = (SourceDataLine) AudioSystem.getLine(info);
                    audioLine.open(audioFormat);
                    audioLine.start();
                    EntsVideoPlayer.LOGGER.info("Audio initialized: {} channels @ {} Hz",
                            grabber.getAudioChannels(), grabber.getSampleRate());
                } catch (Exception e) {
                    EntsVideoPlayer.LOGGER.error("Failed to initialize audio", e);
                    audioLine = null;
                }
            }

            // Start video playback thread
            videoThread = new Thread(this::playVideo, "Cutscene-Video-Thread");
            videoThread.setDaemon(true);
            videoThread.start();

        } catch (Exception e) {
            EntsVideoPlayer.LOGGER.error("Failed to initialize cutscene", e);
            close();
        }
    }

    private void playVideo() {
        try {
            Frame frame;
            int frameCount = 0;

            long startClockNanos = -1;
            long videoStartTimestampUs = -1;

            

            while (running && (frame = grabber.grab()) != null) {
                frameCount++;

                if (frame.timestamp >= 0) {
                    if (videoStartTimestampUs < 0) {
                        videoStartTimestampUs = frame.timestamp;
                        startClockNanos = System.nanoTime();
                    }

                    // Sync clocks
                    long frameTimestampUs = frame.timestamp - videoStartTimestampUs;
                    long elapsedUs = (System.nanoTime() - startClockNanos) / 1000;

                    // --------------------------
                    // 2. If video is ahead → wait
                    // --------------------------
                    if (frameTimestampUs > elapsedUs) {
                        long sleepUs = frameTimestampUs - elapsedUs;

                        // Cap sleep to avoid giant pauses due to network jitter
                        if (sleepUs > 30000) sleepUs = 30000;

                        try {
                            Thread.sleep(sleepUs / 1000, (int)(sleepUs % 1000) * 1000);
                        } catch (InterruptedException ignored) {}
                    }
                    // (If video is behind, we just show it immediately)
                }

                // Process video frame
                if (frame.image != null) {
                    ByteBuffer src = (ByteBuffer) frame.image[0];

                    RawFrame raw = new RawFrame();
                    raw.width = frame.imageWidth;
                    raw.height = frame.imageHeight;

                    // Determine stride (bytes per row). FFmpegFrameGrabber sometimes supplies this.
                    int stride = -1;
                    try {
                        stride = frame.imageStride;
                    } catch (Throwable ignored) {}

                    if (stride <= 0) {
                        // conservative default: assume 3 bytes per pixel tightly packed
                        stride = raw.width * 3;
                    }
                    raw.stride = stride;

                    // Copy only the byte buffer (BGR24) into a direct buffer we own
                    // We allocate exactly stride * height to be safe
                    int expected = stride * raw.height;
                    ByteBuffer copy = ByteBuffer.allocateDirect(expected);

                    // Save/restore position of src and copy
                    int oldPos = src.position();
                    src.limit(Math.min(src.limit(), src.position() + expected)); // defensive
                    copy.put(src);
                    copy.flip();
                    src.position(oldPos); // restore for FFmpeg

                    raw.buffer = copy;

                    try {
                        frameQueue.put(raw); // blocks if full (backpressure)
                        firstVideoFrameSeen = true;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                // Process audio frame
                if (firstVideoFrameSeen && frame.samples != null && audioLine != null) {
                    //playAudioFrame(frame);
                    playAudioFrameSynced(frame, startClockNanos, videoStartTimestampUs);
                }
            }

            EntsVideoPlayer.LOGGER.info("Video finished. Processed {} frames", frameCount);
            hasFinished = true;

        } catch (Exception e) {
            EntsVideoPlayer.LOGGER.error("Error playing video", e);
            hasFinished = true;
        }
    }

    private void playAudioFrameSynced(Frame frame, long startClockNanos, long videoStartTimestampUs) {
        if (startClockNanos < 0 || videoStartTimestampUs < 0) {
            // timestamps not ready yet — play immediately (rare)
            playAudioFrame(frame);
            return;
        }

        long audioTimestampUs = frame.timestamp - videoStartTimestampUs;
        long elapsedUs = (System.nanoTime() - startClockNanos) / 1000;

        // Audio is ahead → wait  
        if (audioTimestampUs > elapsedUs) {
            long sleepUs = audioTimestampUs - elapsedUs;
            if (sleepUs > 30000) sleepUs = 30000;

            try {
                Thread.sleep(sleepUs / 1000, (int)(sleepUs % 1000) * 1000);
            } catch (InterruptedException ignored) {}
        }

        playAudioFrame(frame);
    }

    /**
     * Convert Frame (BGR24 ByteBuffer in frame.image[0]) into NativeImage texture.
     * This MUST be called from the render thread (we call it there).
     */
    private void updateTexture(RawFrame frame) {
        if (frame == null || videoTexture == null) return;

        try {
            NativeImage nativeImage = videoTexture.getImage();
            if (nativeImage == null) return;

            ByteBuffer buffer = frame.buffer;
            buffer.rewind();

            int w = Math.min(frame.width, nativeImage.getWidth());
            int h = Math.min(frame.height, nativeImage.getHeight());
            int stride = frame.stride;
            if (stride < w * 3) {
                // Defensive: if stride is unexpectedly small, treat rows as tightly packed
                stride = w * 3;
            }

            // If the copy buffer contains exactly stride*h bytes we can index by row
            // We'll not change buffer.position permanently (use absolute get).
            for (int y = 0; y < h; y++) {
                int rowStart = y * stride;
                for (int x = 0; x < w; x++) {
                    int idx = rowStart + x * 3;
                    // Use absolute get to avoid changing buffer.position unexpectedly
                    int b = buffer.get(idx) & 0xFF;
                    int g = buffer.get(idx + 1) & 0xFF;
                    int r = buffer.get(idx + 2) & 0xFF;
                    int abgr = 0xFF000000 | (b << 16) | (g << 8) | r;
                    nativeImage.setColor(x, y, abgr);
                }
            }

            // Upload will happen on render thread (this method is called from render thread)
            videoTexture.upload();
        } catch (Exception e) {
            EntsVideoPlayer.LOGGER.error("Error updating texture", e);
        }
    }

    private void playAudioFrame(Frame frame) {
        try {
            if (audioLine == null || frame.samples == null) return;

            int channels = frame.samples.length;
            int sampleCount = ((java.nio.ShortBuffer) frame.samples[0]).remaining();

            byte[] audioData = new byte[sampleCount * channels * 2];
            int offset = 0;

            // frame.samples[...] are ShortBuffers per channel
            for (int i = 0; i < sampleCount; i++) {
                for (int ch = 0; ch < channels; ch++) {
                    java.nio.ShortBuffer channelBuffer = (java.nio.ShortBuffer) frame.samples[ch];
                    short sample = channelBuffer.get(i);
                    audioData[offset++] = (byte) (sample & 0xFF);
                    audioData[offset++] = (byte) ((sample >> 8) & 0xFF);
                }
            }

            audioLine.write(audioData, 0, audioData.length);
        } catch (Exception e) {
            EntsVideoPlayer.LOGGER.error("Error playing audio", e);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (hasFinished) {
            close();
            return;
        }

        // Getting Screen Size


        final long now = System.nanoTime();

        // If we haven't started timing yet (no frames decoded yet), set start to now
        if (nextFrameTimeNano == 0L) nextFrameTimeNano = now;

        // Advance frame(s) as many times as needed to catch up to "now".
        // This will consume frames from the queue and call updateTexture(...) on render thread.
        while (now >= nextFrameTimeNano) {
            RawFrame polled = frameQueue.poll(); // non-blocking
            if (polled != null) {
                lastFrame = polled;
                updateTexture(lastFrame);
            }
            nextFrameTimeNano += frameDurationNano;

            // If no frames were available this pass, break (we'll try again next render)
            if (polled == null) break;
        }

        // Render black background
        context.fill(0, 0, width, height, 0xFF000000);

        // Draw the last uploaded texture (could be the initial black frame or last decoded)
        if (videoTexture != null) {
            var client = MinecraftClient.getInstance();
            var window = client.getWindow();

            int guiWidth = window.getScaledWidth();
            int guiHeight = window.getScaledHeight();

            NativeImage texImg = videoTexture.getImage();
            int texW = texImg != null ? texImg.getWidth() : videoWidth;
            int texH = texImg != null ? texImg.getHeight() : videoHeight;

            float videoAspect = (float) videoWidth / videoHeight;
            float screenAspect = (float) guiWidth / height;

            int renderWidth, renderHeight, renderX, renderY;

            if (screenAspect > videoAspect) {
                renderHeight = guiHeight;
                renderWidth  = (int) (renderHeight * videoAspect);
                renderX = (guiWidth - renderWidth) / 2;
                renderY = 0;
            } else {
                renderWidth  = guiWidth;
                renderHeight = (int) (renderWidth / videoAspect);
                renderX = 0;
                renderY = (guiHeight - renderHeight) / 2;
            }

            EntsVideoPlayer.LOGGER.debug("Updated GUI scaled: {}x{}, tex: {}x{}, render: {}x{} @ ({},{}) screenAspect={}, videoAspect={}",
                guiWidth, guiHeight, texW, texH, renderWidth, renderHeight, renderX, renderY, screenAspect, videoAspect);

            /*context.drawTexture(
                    RenderPipelines.GUI_TEXTURED,
                    TEXTURE_ID,
                    renderX, renderY,
                    0.0f, 0.0f,
                    renderWidth, renderHeight,
                    videoWidth, videoHeight
            );*/

            context.drawTexturedQuad(
                TEXTURE_ID,
                // Texture Location
                renderX,
                renderY,
                 // Size of Each Square
                renderX + renderWidth, // Size of Each Square (with Window Size)
                renderY + renderHeight, //Size of Each Square (with Window Size)
                // Screen Scale
                0.0F, 
                1,
                0.0F,
                1
            );
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.isEscape()) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        cleanup();
        if (client != null) {
            client.setScreen(null);
        }
        CutsceneManager.stopCutscene();
    }

    public void cleanup() {
        running = false;

        if (videoThread != null && videoThread.isAlive()) {
            try {
                videoThread.interrupt();
                videoThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (grabber != null) {
            try {
                grabber.stop();
                grabber.release();
            } catch (Exception e) {
                EntsVideoPlayer.LOGGER.error("Error stopping grabber", e);
            }
        }

        /*if (converter != null) {
            converter.close();
        }*/

        if (audioLine != null) {
            audioLine.drain();
            audioLine.stop();
            audioLine.close();
        }

        if (videoTexture != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(TEXTURE_ID);
            videoTexture.close();
            videoTexture = null;
        }

        frameQueue.clear();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    public boolean isPlayerMovementDisabled() {
        return disableMovement;
    }

    public boolean shouldHideHud() {
        return hideHud;
    }

    private static class RawFrame {
        ByteBuffer buffer;
        int width, height;
        int stride;
    }


}

