package org.example.historyinaglimpse;

import javafx.application.Platform;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Uses the installed Windows speech engine; no browser, API key or network. */
final class WindowsNarrator implements AutoCloseable {
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "adams-narrator"); t.setDaemon(true); return t;
    });
    private final Consumer<String> status;
    private final String story;
    private volatile Process process;
    private BufferedWriter commands;
    private volatile boolean closed;

    WindowsNarrator(Consumer<String> status, String story) { this.status = status; this.story = story; }
    void command(String command) {
        if (closed) return;
        worker.execute(() -> {
            if (closed) return;
            try {
                if (!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows")) {
                    report("Generated voice uses Windows speech. Animation remains available."); return;
                }
                if (process == null) {
                    String speechScript;
                    try (InputStream source = getClass().getResourceAsStream("/narration.ps1")) {
                        if (source == null) throw new IOException("Missing narration resources");
                        speechScript = new String(source.readAllBytes(), StandardCharsets.UTF_8);
                    }
                    ProcessBuilder builder = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                        "-Command", speechScript).redirectErrorStream(true);
                    builder.environment().put("ADAMS_STORY", story);
                    process = builder.start();
                    commands = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
                    Thread output = new Thread(() -> {
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.equals("READY")) report("Generated voice ready · Windows system voice");
                                if (line.startsWith("VOICE_ERROR")) report("Voice unavailable. Check Windows speech and PowerShell settings.");
                            }
                            if (!closed && process.waitFor() != 0) report("Voice unavailable. Check Windows speech and PowerShell settings.");
                        } catch (IOException | InterruptedException ex) {
                            if (!closed) report("Voice playback stopped unexpectedly.");
                        }
                    }, "adams-voice-status");
                    output.setDaemon(true); output.start();
                }
                commands.write(command); commands.newLine(); commands.flush();
            } catch (IOException ex) { report("Voice unavailable. Check Windows speech and PowerShell settings."); }
        });
    }
    private void report(String message) { Platform.runLater(() -> { if (!closed) status.accept(message); }); }
    @Override public void close() {
        closed = true;
        if (process != null) process.destroy();
        worker.execute(() -> {
            try {
                if (commands != null) { commands.write("STOP\n"); commands.flush(); commands.close(); }
                if (process != null && !process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly();
            } catch (IOException | InterruptedException ex) { if (process != null) process.destroyForcibly(); }
        });
        worker.shutdown();
    }
}
