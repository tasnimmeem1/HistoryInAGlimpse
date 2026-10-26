package org.example.historyinaglimpse;

import javafx.application.Platform;
import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Loads a bundled soundscape off the UI thread and keeps pause/resume positions. */
final class EnvironmentAudio implements AmbientAudio {
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "adams-scene-sound"); thread.setDaemon(true); return thread;
    });
    private final String name;
    private final Consumer<String> status;
    private Clip clip;
    private volatile boolean closed;
    private double volume = .65;

    EnvironmentAudio(String name, Consumer<String> status) { this.name=name; this.status=status; }

    public void play() {
        if (closed) return;
        worker.execute(() -> {
            if (closed) return;
            try {
                if (clip == null) {
                    var resource = getClass().getResourceAsStream("/sounds/" + name + ".wav");
                    if (resource == null) throw new IOException("Missing soundscape");
                    try (var input = AudioSystem.getAudioInputStream(new BufferedInputStream(resource))) {
                        clip = AudioSystem.getClip(); clip.open(input); clip.setLoopPoints(0, -1);
                    }
                    applyVolume();
                }
                if (!closed && !clip.isRunning()) clip.loop(Clip.LOOP_CONTINUOUSLY);
            } catch (IOException | UnsupportedAudioFileException | LineUnavailableException | IllegalArgumentException | IllegalStateException ex) {
                if (clip != null) { clip.close(); clip=null; }
                report("Scene sound unavailable on this audio device. The map and scene remain available.");
            }
        });
    }
    public void pause() {
        if (closed) return;
        worker.execute(() -> { if (!closed && clip != null) clip.stop(); });
    }
    public void setVolume(double value) {
        if (closed) return;
        worker.execute(() -> { volume=Math.max(0,Math.min(1,value)); if (!closed) applyVolume(); });
    }
    private void applyVolume() {
        if (clip == null) return;
        if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl gain=(FloatControl)clip.getControl(FloatControl.Type.MASTER_GAIN);
            float decibels=volume<=0 ? gain.getMinimum() : (float)(20*Math.log10(volume));
            gain.setValue(Math.max(gain.getMinimum(),Math.min(gain.getMaximum(),decibels)));
        }
        if (clip.isControlSupported(BooleanControl.Type.MUTE))
            ((BooleanControl)clip.getControl(BooleanControl.Type.MUTE)).setValue(volume<=0);
    }
    private void report(String message) { Platform.runLater(() -> { if (!closed) status.accept(message); }); }
    @Override public void close() {
        if (closed) return;
        closed=true;
        worker.execute(() -> { if (clip != null) { clip.stop(); clip.close(); clip=null; } });
        worker.shutdown();
    }
}
