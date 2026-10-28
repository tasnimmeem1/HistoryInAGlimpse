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
                if (!(root.getCenter() instanceof LoginView)) throw new AssertionError("Login after splash");
                ((javafx.scene.control.Hyperlink) root.lookup("#register-link")).fire();
                if (!(root.getCenter() instanceof RegisterView)) throw new AssertionError("Register navigation");
                ((javafx.scene.control.Hyperlink) root.lookup("#back-to-login")).fire();
                ((javafx.scene.control.Hyperlink) root.lookup("#guest-button")).fire();
                if (!(root.getCenter() instanceof MainMenuView)) throw new AssertionError("Guest main menu");
                if (root.lookup("#menu-admin") != null) throw new AssertionError("Admin tile shown to guest");
                ((Button) root.lookup("#menu-stories")).fire();
                if (!(root.getCenter() instanceof LoginView)) throw new AssertionError("Guest locked out of stories");
                ((javafx.scene.control.Hyperlink) root.lookup("#guest-button")).fire();
                ((Button) root.lookup("#menu-timeline")).fire();
                if (!(root.getCenter() instanceof ScrollPane)) throw new AssertionError("Chart navigation");
                ((Button) root.lookup("#main-menu-button")).fire();
                if (!(root.getCenter() instanceof MainMenuView)) throw new AssertionError("Back to main menu");
                stage.close();
                result.complete(null);
            } catch (Throwable error) { result.completeExceptionally(error); }
        });
        try { result.get(30, TimeUnit.SECONDS); System.out.println("PASS: event maps, cloud bird selection, splash, login, main menu and timeline navigation"); }
        finally { Platform.exit(); }
    }
}
