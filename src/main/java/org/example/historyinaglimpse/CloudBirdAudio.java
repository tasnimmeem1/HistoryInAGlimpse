package org.example.historyinaglimpse;

import javafx.application.Platform;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Downloads private cloud audio through the backend without blocking the UI.
 * Public methods and player state are confined to the JavaFX thread. */
final class CloudBirdAudio implements AmbientAudio {
    private final Consumer<String> status;
    private final String endpoint;
    private MediaPlayer player;
    private Path localFile;
    private boolean loading, closed, playing;
    private double volume = .65;

    CloudBirdAudio(Consumer<String> status) { this(status, "/api/scenes/stone-age/birds"); }

    CloudBirdAudio(Consumer<String> status, String endpoint) {
        this.status = status;
        this.endpoint = endpoint;
    }

    @Override public void play() {
        if (closed) return;
        playing = true;
        if (player != null) { player.play(); return; }
        if (loading) return;
        loading = true;
        CompletableFuture.supplyAsync(this::download).whenComplete((file, error) ->
            Platform.runLater(() -> {
                loading = false;
                if (closed) { delete(file); return; }
                if (error != null) { status.accept("Scene audio unavailable. Check the backend and reopen the scene."); return; }
                localFile = file;
                try {
                    Media media = new Media(file.toUri().toString());
                    player = new MediaPlayer(media);
                    player.setCycleCount(MediaPlayer.INDEFINITE);
                    player.setVolume(volume);
                    player.setOnError(this::failed);
                    media.setOnError(this::failed);
                    player.setOnReady(() -> { if (!closed && playing) player.play(); });
                } catch (RuntimeException ex) { failed(); }
            })
        );
    }

    private Path download() {
        HttpURLConnection connection = null;
        Path file = null;
        try {
            String backend = System.getProperty("history.animation.backend", "http://127.0.0.1:8080")
                    .replaceAll("/+$", "");
            connection = (HttpURLConnection) URI.create(backend + endpoint).toURL().openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(false);
            if (connection.getResponseCode() != 200) throw new IOException("Audio request failed");
            try (var input = connection.getInputStream()) {
                byte[] bytes = input.readNBytes(50 * 1024 * 1024 + 1);
                if (bytes.length == 0 || bytes.length > 50 * 1024 * 1024) throw new IOException("Invalid audio size");
                boolean wav = bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I'
                        && bytes[2] == 'F' && bytes[3] == 'F' && bytes[8] == 'W'
                        && bytes[9] == 'A' && bytes[10] == 'V' && bytes[11] == 'E';
                file = Files.createTempFile("history-scene-", wav ? ".wav" : ".mp3");
                file.toFile().deleteOnExit();
                Files.write(file, bytes);
                return file;
            }
        } catch (Exception ex) {
            delete(file);
            throw new java.util.concurrent.CompletionException(ex);
        } finally { if (connection != null) connection.disconnect(); }
    }

    @Override public void pause() {
        playing = false;
        if (player != null) player.pause();
    }
    @Override public void setVolume(double value) {
        volume = Math.max(0, Math.min(1, value));
        if (player != null) player.setVolume(volume);
    }
    private void failed() {
        if (closed) return;
        if (player != null) { player.dispose(); player = null; }
        delete(localFile); localFile = null;
        status.accept("Scene audio could not play. Check the sound file and audio device.");
    }
    @Override public void close() {
        closed = true; playing = false;
        if (player != null) { player.dispose(); player = null; }
        delete(localFile); localFile = null;
    }
    private static void delete(Path file) {
        if (file != null) try { Files.deleteIfExists(file); } catch (IOException ignored) { }
    }
}
