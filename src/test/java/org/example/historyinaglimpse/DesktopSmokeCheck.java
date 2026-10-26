package org.example.historyinaglimpse;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Standalone smoke check for the current splash and timeline. */
public final class DesktopSmokeCheck {
    public static void main(String[] args) throws Exception {
        CompletableFuture<Void> result = new CompletableFuture<>();
        Platform.startup(() -> {
            try {
                if (EventCatalog.EVENTS.isEmpty()) throw new AssertionError("Missing events");
                for (HistoricalEvent event : EventCatalog.EVENTS) {
                    EraMap.create(event, url -> {});
                    if (!event.source().startsWith("https://")) throw new AssertionError("Missing source");
                }
                if (!Soundscapes.forEvent(EventCatalog.EVENTS.get(11)).file().equals("cloud-birds"))
                    throw new AssertionError("Stone Age still uses old sound");
                Stage stage = new Stage();
                var app = new HistoryInAGlimpseApplication();
                app.start(stage);
                BorderPane root = (BorderPane) stage.getScene().getRoot();
                ((Button) root.lookup("#enter-history")).fire();
                if (!(root.getCenter() instanceof ScrollPane)) throw new AssertionError("Chart navigation");
                stage.close();
                result.complete(null);
            } catch (Throwable error) { result.completeExceptionally(error); }
        });
        try { result.get(30, TimeUnit.SECONDS); System.out.println("PASS: event maps, cloud bird selection, splash and timeline navigation"); }
        finally { Platform.exit(); }
    }
}
