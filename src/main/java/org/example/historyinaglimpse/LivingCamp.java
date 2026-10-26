package org.example.historyinaglimpse;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.util.Duration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Downloads camp images through the Spring Boot backend
 * and animates each figure independently.
 */
final class LivingCamp extends StackPane {

    private static final double SIZE = 1000;

    private static final String BACKEND = System.getProperty(
            "history.animation.backend",
            "http://127.0.0.1:8080"
    ).replaceAll("/+$", "");

    private static final Map<String, Image> CACHE =
            new ConcurrentHashMap<>();

    private static final Map<String, String> ROUTES = Map.of(
            "hunter-camp-background.png", "background",
            "hunter-individual-poses.png", "characters",
            "hunter-fire-poses.png", "fire"
    );

    private final Group world = new Group();

    private final Timeline animation = new Timeline(
            new KeyFrame(Duration.seconds(60))
    );

    private final List<Sprite> actors = new ArrayList<>();

    private final Label status = new Label(
            "Loading Stone Age images from cloud storage..."
    );

    private final Button retry = new Button("Retry loading");

    private final VBox loading = new VBox(12, status, retry);

    private boolean busy;

    LivingCamp() {
        setMinSize(0, 0);
        setPrefSize(650, 650);
        setStyle("-fx-background-color: #d7b58a;");

        loading.setAlignment(javafx.geometry.Pos.CENTER);

        status.setWrapText(true);
        status.setMaxWidth(420);

        retry.setOnAction(event -> load());

        getChildren().add(loading);

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);

        animation.setCycleCount(Animation.INDEFINITE);

        animation.statusProperty().addListener(
                (observable, before, after) -> syncPlayback()
        );

        load();
    }

    Animation animation() {
        return animation;
    }

    /**
     * Downloads and decodes the images without blocking
     * the JavaFX application thread.
     */
    private void load() {
        if (busy) {
            return;
        }

        busy = true;
        retry.setVisible(false);

        status.setText(
                "Loading Stone Age images from cloud storage..."
        );

        CompletableFuture.supplyAsync(() -> {
            Map<String, Image> images = new HashMap<>();

            ROUTES.forEach((name, route) ->
                    images.put(
                            name,
                            CACHE.computeIfAbsent(
                                    name,
                                    key -> download(route)
                            )
                    )
            );

            return images;

        }).whenComplete((images, error) ->
                Platform.runLater(() -> {
                    busy = false;

                    if (error != null) {
                        status.setText(
                                "Could not load the camp images. "
                                        + "Check your internet connection "
                                        + "and keep AnimationBackendApplication "
                                        + "running, then retry."
                        );

                        retry.setVisible(true);
                        return;
                    }

                    try {
                        buildScene(images);

                    } catch (RuntimeException failure) {
                        actors.clear();
                        world.getChildren().clear();
                        getChildren().setAll(loading);

                        status.setText(
                                "The downloaded images do not "
                                        + "match the camp sprite layout."
                        );

                        CACHE.clear();
                        retry.setVisible(true);
                    }
                })
        );
    }

    /**
     * Requests an image from the local backend.
     * No Supabase secret key belongs in this JavaFX class.
     */
    private static Image download(String route) {
        HttpURLConnection connection = null;

        try {
            URI uri = URI.create(
                    BACKEND + "/api/scenes/stone-age/" + route
            );

            connection = (HttpURLConnection)
                    uri.toURL().openConnection();

            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(false);

            if (connection.getResponseCode() != 200) {
                throw new IOException("Image endpoint failed.");
            }

            int maximumBytes = 12 * 1024 * 1024;
            byte[] bytes;

            try (var input = connection.getInputStream()) {
                bytes = input.readNBytes(maximumBytes + 1);
            }

            if (bytes.length == 0 || bytes.length > maximumBytes) {
                throw new IOException("Invalid image size.");
            }

            Image image = new Image(
                    new ByteArrayInputStream(bytes)
            );

            if (image.isError()
                    || image.getPixelReader() == null) {
                throw new IOException("Invalid image.");
            }

            return image;

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Cannot download camp image.",
                    exception
            );

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void buildScene(Map<String, Image> images) {
        ImageView background = new ImageView(
                images.get("hunter-camp-background.png")
        );

        background.fitWidthProperty().bind(widthProperty());
        background.fitHeightProperty().bind(heightProperty());
        background.setPreserveRatio(false);
        background.setSmooth(true);

        Image people = images.get(
                "hunter-individual-poses.png"
        );

        Sprite father = new Sprite(
                "father", people,
                0, 398,
                10, 370, 510,
                6.7, 0, false
        );

        Sprite mother = new Sprite(
                "mother", people,
                398, 737,
                650, 470, 390,
                5.3, 0.9, false
        );

        Sprite child = new Sprite(
                "child", people,
                737, 986,
                430, 430, 290,
                4.1, 1.7, false
        );

        Sprite girl = new Sprite(
                "girl", people,
                986, 1448,
                620, 135, 500,
                8.9, 2.4, false
        );

        Image flames = images.get("hunter-fire-poses.png");

        Sprite fire = new Sprite(
                "fire", flames,
                0, flames.getHeight(),
                220, 550, 330,
                0.87, 0.13, true
        );

        actors.clear();
        actors.addAll(
                List.of(father, mother, child, girl, fire)
        );

        Pane layers = new Pane(
                girl.layer,
                child.layer,
                father.layer,
                mother.layer,
                fire.layer
        );

        layers.setPrefSize(SIZE, SIZE);
        layers.resize(SIZE, SIZE);

        world.getChildren().setAll(layers);

        getChildren().setAll(background, world);

        requestLayout();
        syncPlayback();
    }

    private void syncPlayback() {
        for (Sprite actor : actors) {
            switch (animation.getStatus()) {
                case RUNNING -> actor.motion.play();
                case PAUSED -> actor.motion.pause();
                case STOPPED -> actor.motion.stop();
            }
        }
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();

        double scale = Math.max(
                0,
                Math.min(
                        getWidth() / SIZE,
                        getHeight() / SIZE
                )
        );

        world.getTransforms().setAll(
                new Scale(scale, scale, 0, 0)
        );

        world.relocate(
                (getWidth() - SIZE * scale) / 2,
                (getHeight() - SIZE * scale) / 2
        );
    }

    /**
     * Each sprite has independent timing, pose blending,
     * breathing, and sway.
     */
    private static final class Sprite {

        final Group layer;
        final Animation motion;

        final ImageView base;
        final ImageView overlay;

        final Rectangle2D[] frames = new Rectangle2D[3];

        int shown = -1;
        int next = -1;

        Sprite(
                String name,
                Image atlas,
                double top,
                double end,
                double x,
                double y,
                double height,
                double seconds,
                double phase,
                boolean flame
        ) {
            double cellWidth = atlas.getWidth() / 3;

            Rectangle2D bounds = contentBounds(
                    atlas, top, end, cellWidth
            );

            base = new ImageView(atlas);
            overlay = new ImageView(atlas);

            base.setId("camp-" + name);
            overlay.setMouseTransparent(true);

            for (ImageView view : List.of(base, overlay)) {
                view.setFitHeight(height);

                view.setFitWidth(
                        height * bounds.getWidth()
                                / bounds.getHeight()
                );

                view.setPreserveRatio(false);
                view.setSmooth(true);
            }

            for (int index = 0; index < 3; index++) {
                frames[index] = new Rectangle2D(
                        index * cellWidth + bounds.getMinX(),
                        top + bounds.getMinY(),
                        bounds.getWidth(),
                        bounds.getHeight()
                );
            }

            overlay.setOpacity(0);

            layer = new Group(base, overlay);
            layer.setLayoutX(x);
            layer.setLayoutY(y);

            double pivotX = base.getFitWidth() / 2;

            Scale breathe = new Scale(
                    1, 1, pivotX, height
            );

            Rotate sway = new Rotate(
                    0, pivotX, height
            );

            layer.getTransforms().addAll(breathe, sway);

            int[] sequence = {0, 1, 2, 1};

            motion = new Transition() {
                {
                    setCycleDuration(
                            Duration.seconds(seconds)
                    );

                    setCycleCount(Animation.INDEFINITE);
                    setInterpolator(Interpolator.LINEAR);
                }

                @Override
                protected void interpolate(double fraction) {
                    double progress =
                            (fraction + phase / seconds) % 1;

                    if (flame) {
                        // Keep stones, spit, and chicken still.
                        show(0);
                        breathe.setX(1);
                        breathe.setY(1);
                        sway.setAngle(0);
                        layer.setOpacity(1);

                        // Animate only the upper flames.
                        if (overlay.getClip() == null) {
                            overlay.setClip(new Rectangle(
                                    0,
                                    0,
                                    overlay.getFitWidth(),
                                    overlay.getFitHeight() * 0.30
                            ));
                        }

                        int[] fireFrames = {0, 1, 2, 1};

                        double firePosition =
                                progress * fireFrames.length;

                        int fireStep = Math.min(
                                fireFrames.length - 1,
                                (int) firePosition
                        );

                        int fireFrame = fireFrames[fireStep];

                        if (fireFrame != next) {
                            overlay.setViewport(frames[fireFrame]);
                            next = fireFrame;
                        }

                        double fireBlend = firePosition - fireStep;

                        overlay.setOpacity(
                                0.75 * Math.sin(Math.PI * fireBlend)
                        );

                        return;
                    }

                    // Animate each person independently.
                    double position = progress * sequence.length;

                    int step = Math.min(
                            sequence.length - 1,
                            (int) position
                    );

                    double withinStep = position - step;

                    show(sequence[step]);

                    int following =
                            sequence[(step + 1) % sequence.length];

                    if (following != next) {
                        overlay.setViewport(frames[following]);
                        next = following;
                    }

                    double blend = (withinStep - 0.68) / 0.32;
                    blend = Math.max(0, Math.min(1, blend));

                    overlay.setOpacity(
                            blend * blend * (3 - 2 * blend)
                    );

                    breathe.setY(
                            1 + 0.0035 * Math.sin(4 * Math.PI * progress)
                    );

                    sway.setAngle(
                            0.28 * Math.sin(2 * Math.PI * progress)
                    );
                }
            };

            show(0);

            motion.statusProperty().addListener(
                    (observable, before, after) -> {
                        if (after == Animation.Status.STOPPED) {
                            show(0);
                            overlay.setOpacity(0);

                            breathe.setX(1);
                            breathe.setY(1);

                            sway.setAngle(0);
                            layer.setOpacity(1);
                        }
                    }
            );
        }

        void show(int value) {
            if (shown != value) {
                base.setViewport(frames[value]);
                shown = value;
            }
        }

        private static Rectangle2D contentBounds(
                Image image,
                double top,
                double end,
                double cellWidth
        ) {
            if (top < 0
                    || end > image.getHeight()
                    || end <= top) {
                throw new IllegalArgumentException(
                        "Invalid atlas row."
                );
            }

            int minX = (int) cellWidth;
            int minY = (int) (end - top);
            int maxX = -1;
            int maxY = -1;

            var pixels = image.getPixelReader();

            for (int column = 0; column < 3; column++) {
                for (int y = (int) top; y < (int) end; y++) {
                    for (int x = 0; x < (int) cellWidth; x++) {

                        int alpha = (
                                pixels.getArgb(
                                        (int) (column * cellWidth)
                                                + x,
                                        y
                                ) >>> 24
                        ) & 255;

                        if (alpha > 80) {
                            minX = Math.min(minX, x);
                            maxX = Math.max(maxX, x);

                            minY = Math.min(
                                    minY, y - (int) top
                            );

                            maxY = Math.max(
                                    maxY, y - (int) top
                            );
                        }
                    }
                }
            }

            if (maxX < minX || maxY < minY) {
                throw new IllegalArgumentException(
                        "Empty sprite row."
                );
            }

            return new Rectangle2D(
                    minX,
                    minY,
                    maxX - minX + 1,
                    maxY - minY + 1
            );
        }
    }
}