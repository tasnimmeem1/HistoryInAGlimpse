package org.example.historyinaglimpse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Pos;
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
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** A private-cloud farming scene. Layout and timing come from the scene database. */
final class LivingFarming extends StackPane {
    private static final String BACKEND = System.getProperty("history.animation.backend",
            "http://127.0.0.1:8080").replaceAll("/+$", "");
    private static final Map<String, Image> IMAGE_CACHE = new ConcurrentHashMap<>();
    private final Group world = new Group();
    private final Timeline animation = new Timeline(new KeyFrame(Duration.seconds(60)));
    private final List<Actor> actors = new ArrayList<>();
    private final Label message = new Label("Loading the farming scene...");
    private final Button retry = new Button("Retry loading");
    private final VBox loading = new VBox(12, message, retry);
    private double canvasWidth = 1000, canvasHeight = 1000;
    private boolean busy;

    LivingFarming() {
        setMinSize(0, 0); setPrefSize(650, 650);
        setStyle("-fx-background-color: #ead6ab;");
        loading.setAlignment(Pos.CENTER);
        message.setWrapText(true); message.setMaxWidth(400);
        retry.setOnAction(e -> load());
        world.setManaged(false);
        getChildren().add(loading);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty()); clip.heightProperty().bind(heightProperty());
        setClip(clip);
        animation.setCycleCount(Animation.INDEFINITE);
        animation.statusProperty().addListener((o, before, after) -> syncPlayback());
        load();
    }
    Animation animation() { return animation; }

    private void load() {
        if (busy) return;
        busy = true; retry.setVisible(false); message.setText("Loading the farming scene...");
        CompletableFuture.supplyAsync(LivingFarming::downloadScene).whenComplete((data, error) ->
            Platform.runLater(() -> {
                busy = false;
                if (error != null) { failed(error); return; }
                try { build(data); } catch (RuntimeException ex) { failed(ex); }
            })
        );
    }
    private void failed(Throwable error) {
        for (Actor actor : actors) actor.motion.stop();
        actors.clear(); world.getChildren().clear();
        getChildren().setAll(loading);
        message.setText("Could not load the farming scene. Check the connection and try again.");
        retry.setVisible(true);
        System.err.println("Farming scene load failed: " + error.getClass().getSimpleName());
    }
    private static SceneData downloadScene() {
        try {
            JsonNode scene = new ObjectMapper().readTree(request("", 256 * 1024));
            if (!"early-farming".equals(scene.path("id").asText())) throw new IOException("Wrong scene");
            JsonNode settings = scene.path("settings");
            double width = number(settings, "canvasWidth", 1, 4096);
            double height = number(settings, "canvasHeight", 1, 4096);
            if (number(settings, "atlasColumns", 1, 3) != 3) throw new IOException("Atlas columns");
            Image background = image(scene, "background_path", "background");
            Image atlas = image(scene, "characters_path", "characters");
            if (atlas.getWidth() != number(settings, "atlasWidth", 1, 4096)
                    || atlas.getHeight() != number(settings, "atlasHeight", 1, 4096))
                throw new IOException("Unexpected atlas dimensions");
            JsonNode entries = settings.path("actors");
            if (!entries.isArray() || entries.isEmpty() || entries.size() > 20) throw new IOException("Actors missing");
            List<ActorData> specifications = new ArrayList<>();
            Set<String> names = new HashSet<>();
            for (JsonNode a : entries) {
                String name = a.path("name").asText();
                if (name.isBlank() || !names.add(name)) throw new IOException("Actor name");
                int top = (int) number(a, "rowTop", 0, atlas.getHeight() - 1);
                int end = (int) number(a, "rowEnd", top + 1, atlas.getHeight());
                double x = number(a, "x", 0, width), y = number(a, "y", 0, height);
                double actorHeight = number(a, "height", 1, height);
                double cycle = number(a, "cycleSeconds", .5, 120);
                double phase = number(a, "phaseSeconds", 0, 120);
                double sway = number(a, "swayDegrees", 0, 2);
                specifications.add(new ActorData(name, frames(atlas, top, end), x, y,
                        actorHeight, cycle, phase, sway));
            }
            return new SceneData(background, atlas, width, height, List.copyOf(specifications));
        } catch (IOException ex) { throw new IllegalStateException("Farming scene unavailable", ex); }
    }
    private static double number(JsonNode node, String name, double min, double max) throws IOException {
        JsonNode value = node.path(name);
        double n = value.asDouble(Double.NaN);
        if (!value.isNumber() || !Double.isFinite(n) || n < min || n > max)
            throw new IOException("Invalid scene setting: " + name);
        return n;
    }
    private static Image image(JsonNode scene, String field, String route) {
        String path = scene.path(field).asText();
        if (path.isBlank()) throw new IllegalStateException("Missing asset path");
        return IMAGE_CACHE.computeIfAbsent(BACKEND + "/" + path, key -> {
            Image image = new Image(new ByteArrayInputStream(request("/" + route, 12 * 1024 * 1024)));
            if (image.isError() || image.getPixelReader() == null) throw new IllegalStateException("Invalid image");
            return image;
        });
    }
    private static byte[] request(String route, int limit) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(BACKEND + "/api/scenes/early-farming" + route)
                    .toURL().openConnection();
            connection.setConnectTimeout(10000); connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(false);
            if (connection.getResponseCode() != 200) throw new IOException("Scene request failed");
            try (var input = connection.getInputStream()) {
                byte[] bytes = input.readNBytes(limit + 1);
                if (bytes.length == 0 || bytes.length > limit) throw new IOException("Invalid response size");
                return bytes;
            }
        } catch (IOException ex) { throw new IllegalStateException("Could not load farming media", ex); }
        finally { if (connection != null) connection.disconnect(); }
    }
    /** Inspect alpha once off the FX thread. All poses share one fixed content box. */
    private static Rectangle2D[] frames(Image atlas, int top, int end) throws IOException {
        int[] left = new int[3]; int cell = Integer.MAX_VALUE;
        for (int col = 0; col < 3; col++) {
            left[col] = (int) Math.round(col * atlas.getWidth() / 3);
            cell = Math.min(cell, (int) Math.round((col + 1) * atlas.getWidth() / 3) - left[col]);
        }
        int minX = cell, maxX = -1, minY = end - top, maxY = -1;
        var pixels = atlas.getPixelReader();
        for (int col = 0; col < 3; col++) for (int y = top; y < end; y++) for (int x = 0; x < cell; x++) {
            if ((pixels.getArgb(left[col] + x, y) >>> 24) > 80) {
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y - top); maxY = Math.max(maxY, y - top);
            }
        }
        if (maxX < minX || maxY < minY) throw new IOException("Empty sprite row");
        Rectangle2D[] frames = new Rectangle2D[3];
        for (int col = 0; col < 3; col++) frames[col] = new Rectangle2D(left[col] + minX,
                top + minY, maxX - minX + 1, maxY - minY + 1);
        return frames;
    }
    private void build(SceneData data) {
        canvasWidth = data.width; canvasHeight = data.height;
        ImageView background = new ImageView(data.background);
        background.setId("farming-background");
        background.setFitWidth(canvasWidth); background.setFitHeight(canvasHeight);
        background.setSmooth(true);
        Pane layers = new Pane(); layers.setPrefSize(canvasWidth, canvasHeight);
        for (ActorData specification : data.actors) {
            Actor actor = new Actor(data.atlas, specification);
            actors.add(actor); layers.getChildren().add(actor.layer);
        }
        world.getChildren().setAll(background, layers);
        getChildren().setAll(world);
        requestLayout(); syncPlayback();
    }
    private void syncPlayback() {
        for (Actor actor : actors) switch (animation.getStatus()) {
            case RUNNING -> actor.motion.play();
            case PAUSED -> actor.motion.pause();
            case STOPPED -> actor.motion.stop();
        }
    }
    @Override protected void layoutChildren() {
        super.layoutChildren();
        double scale = Math.max(0, Math.min(getWidth() / canvasWidth, getHeight() / canvasHeight));
        world.getTransforms().setAll(new Scale(scale, scale, 0, 0));
        world.relocate((getWidth() - canvasWidth * scale) / 2, (getHeight() - canvasHeight * scale) / 2);
    }
    private record SceneData(Image background, Image atlas, double width, double height, List<ActorData> actors) {}
    private record ActorData(String name, Rectangle2D[] frames, double x, double y, double height,
                             double cycle, double phase, double sway) {}
    private static final class Actor {
        final Group layer;
        final Animation motion;
        Actor(Image atlas, ActorData a) {
            ImageView base = new ImageView(atlas), overlay = new ImageView(atlas);
            base.setId("farming-" + a.name); overlay.setMouseTransparent(true);
            double width = a.height * a.frames[0].getWidth() / a.frames[0].getHeight();
            for (ImageView image : List.of(base, overlay)) {
                image.setViewport(a.frames[0]); image.setFitHeight(a.height); image.setFitWidth(width);
                image.setSmooth(true); image.setPreserveRatio(false);
            }
            overlay.setOpacity(0);
            layer = new Group(base, overlay); layer.relocate(a.x, a.y);
            Rotate sway = new Rotate(0, width / 2, a.height);
            layer.getTransforms().add(sway);
            int[] sequence = {0, 1, 2, 1};
            motion = new Transition() {
                { setCycleDuration(Duration.seconds(a.cycle)); setCycleCount(Animation.INDEFINITE);
                  setInterpolator(Interpolator.LINEAR); }
                @Override protected void interpolate(double fraction) {
                    double progress = (fraction + a.phase / a.cycle) % 1;
                    double position = progress * sequence.length;
                    int step = Math.min(sequence.length - 1, (int) position);
                    base.setViewport(a.frames[sequence[step]]);
                    int next = sequence[(step + 1) % sequence.length];
                    overlay.setViewport(a.frames[next]);
                    double blend = Math.max(0, Math.min(1, (position - step - .72) / .28));
                    overlay.setOpacity(blend * blend * (3 - 2 * blend));
                    sway.setAngle(a.sway * Math.sin(2 * Math.PI * progress));
                }
            };
        }
    }
}
