package org.example.historyinaglimpse;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

/** A paused copy of the real scene. Wait for a visible chart card before loading it. */
final class CloudEraThumbnail extends StackPane {
    private final String id;
    private boolean started;
    private final AnimationTimer visibility = new AnimationTimer() {
        @Override public void handle(long now) { checkVisible(); }
    };
    CloudEraThumbnail(String id) {
        this.id = id;
        setManaged(false);
        setMouseTransparent(true);
        setStyle("-fx-background-color: #ead6ab;");
        getChildren().add(new Label("Scene preview"));
        sceneProperty().addListener((o, before, after) -> {
            if (after == null || started) visibility.stop();
            else visibility.start();
        });
    }
    private void checkVisible() {
        if (started || getScene() == null || getWidth() <= 0
                || getScene().getWidth() <= 0 || getScene().getHeight() <= 0) return;
        Bounds bounds = localToScene(getBoundsInLocal());
        if (bounds.getMaxX() < 0 || bounds.getMinX() > getScene().getWidth()
                || bounds.getMaxY() < 0 || bounds.getMinY() > getScene().getHeight()) return;
        started = true;
        visibility.stop();
        Platform.runLater(() -> getChildren().setAll(new CloudEraScene(id,true)));
    }
}
