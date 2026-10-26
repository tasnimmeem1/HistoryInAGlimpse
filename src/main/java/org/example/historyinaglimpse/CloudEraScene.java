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

/** A private-cloud historical scene. Layout and timing come from the scene database. */
final class CloudEraScene extends StackPane {
    private static final String BACKEND = System.getProperty("history.animation.backend",
            "http://127.0.0.1:8080").replaceAll("/+$", "");
    private static final Map<String, CompletableFuture<SceneData>> SCENE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Image> IMAGE_CACHE = new ConcurrentHashMap<>();
    private final Group world = new Group();
    private final Timeline animation = new Timeline(new KeyFrame(Duration.seconds(60)));
    private final List<Actor> actors = new ArrayList<>();
    private final Label message = new Label("Loading the historical scene...");
    private final Button retry = new Button("Retry loading");
    private final VBox loading = new VBox(12, message, retry);
    private double canvasWidth = 1000, canvasHeight = 1000;
    private OriginalImageMotion originalMotion;
    private Animation waterMotion;
    private Animation atmosphereMotion;
    private boolean busy;
    private final String sceneId;
    private final boolean thumbnail;

    CloudEraScene(String sceneId) { this(sceneId, false); }
    CloudEraScene(String sceneId, boolean thumbnail) {
        this.sceneId = sceneId;
        this.thumbnail = thumbnail;
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
        busy = true; retry.setVisible(false); message.setText("Loading the historical scene...");
        SCENE_CACHE.computeIfAbsent(BACKEND + "/" + sceneId, key ->
                CompletableFuture.supplyAsync(this::downloadScene)).whenComplete((data, error) ->
            Platform.runLater(() -> {
                busy = false;
                if (error != null) { SCENE_CACHE.remove(BACKEND + "/" + sceneId); failed(error); return; }
                try { build(data); } catch (RuntimeException ex) { failed(ex); }
            })
        );
    }
    private void failed(Throwable error) {
        for (Actor actor : actors) actor.motion.stop();
        actors.clear(); world.getChildren().clear();
        getChildren().setAll(loading);
        message.setText("Could not load the historical scene. Check the connection and try again.");
        retry.setVisible(true);
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        System.err.println("Historical scene " + sceneId + " failed: " + cause.getMessage());
    }
    private SceneData downloadScene() {
        try {
            JsonNode scene = new ObjectMapper().readTree(request("", 256 * 1024));
            if (!sceneId.equals(scene.path("id").asText())) throw new IOException("Wrong scene");
            JsonNode settings = scene.path("settings");
            // Bundled corrections override animation layout while preserving cloud scenery/audio.
            var settingsCopy = settings.deepCopy();
            try (var patch = CloudEraScene.class.getResourceAsStream("/scene-fixes/" + sceneId + ".json")) {
                if (patch != null && settings.path("sceneRevision").asInt(0) < 20261025) {
                    var replacement = new ObjectMapper().readTree(patch);
                    ((com.fasterxml.jackson.databind.node.ObjectNode) settingsCopy).setAll(
                            (com.fasterxml.jackson.databind.node.ObjectNode) replacement);
                    ((com.fasterxml.jackson.databind.node.ObjectNode) settingsCopy).remove(
                            List.of("foreground_path", "foreground", "sourceMotion"));
                }
            }
            settings = settingsCopy;
            final JsonNode sceneSettings = settings;
            double width = number(settings, "canvasWidth", 1, 4096);
            double height = number(settings, "canvasHeight", 1, 4096);
            if (settings.path("sourceImage").asBoolean(false)) {
                Image original = image(scene, "background_path", "background");
                if (original.getWidth() != width || original.getHeight() != height)
                    throw new IOException("Original image dimensions do not match scene settings");
                return new SceneData(original, null, width, height, List.of(),
                        settings.path("water"), settings.path("atmosphere"), settings.path("sourceMotion"), null, null);
            }
            int checkedColumns = (int) number(settings, "atlasColumns", 3, 8);
            if (checkedColumns != 3 && checkedColumns != 5 && checkedColumns != 6 && checkedColumns != 7 && checkedColumns != 8) throw new IOException("Atlas columns");
            int columns = settings.path("atlasColumns").asInt();
            CompletableFuture<Image> foregroundLoad = CompletableFuture.supplyAsync(() -> sceneSettings.has("foreground_path") && (sceneSettings.has("foreground") || sceneSettings.path("foregroundActors").asBoolean(false))
                    ? imageForPath(sceneSettings.path("foreground_path").asText(), "foreground") : null);
            CompletableFuture<Image> backgroundLoad = CompletableFuture.supplyAsync(() -> image(scene, "background_path", "background"));
            CompletableFuture<Image> atlasLoad = CompletableFuture.supplyAsync(() ->
                    sceneSettings.has("atlasResource") ? null : image(scene, "characters_path", "characters"));
            Image background = settings.has("backgroundResource")
                    ? bundledImage(settings.path("backgroundResource").asText()) : backgroundLoad.join();
            Image originalAtlas = settings.has("originalAtlasResource")
                    ? bundledImage(settings.path("originalAtlasResource").asText()) : atlasLoad.join();
            Image atlas = settings.has("atlasResource")
                    ? bundledImage(settings.path("atlasResource").asText()) : originalAtlas;
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
                boolean foregroundActor = a.path("foregroundAtlas").asBoolean(false);
                Image actorAtlas = foregroundActor ? foregroundLoad.join()
                        : a.path("originalAtlas").asBoolean(false) ? originalAtlas : atlas;
                if (actorAtlas == null) throw new IOException("Missing actor atlas");
                int top = (int) number(a, "rowTop", 0, actorAtlas.getHeight() - 1);
                int end = (int) number(a, "rowEnd", top + 1, actorAtlas.getHeight());
                double x = number(a, "x", 0, width), y = number(a, "y", 0, height);
                double actorHeight = number(a, "height", 1, height);
                double cycle = number(a, "cycleSeconds", .5, 120);
                double phase = number(a, "phaseSeconds", 0, 120);
                double sway = number(a, "swayDegrees", 0, 2);
                int actorColumns = foregroundActor ? (int) number(settings,"foregroundAtlasColumns",3,8) : a.path("originalAtlas").asBoolean(false)
                        ? settings.path("originalAtlasColumns").asInt(scene.path("settings").path("atlasColumns").asInt()) : columns;
                specifications.add(new ActorData(name, actorAtlas, frames(actorAtlas, top, end, actorColumns, a.has("gridRows") ? (int) number(a,"gridRows",1,2) : 1), x, y,
                        actorHeight, cycle, a.has("poseCycleSeconds") ? number(a, "poseCycleSeconds", .5, 120) : cycle, phase, sway,
                        optional(a, "travelX", -2000, 2000), optional(a, "travelY", -2000, 2000), optional(a, "bobY", 0, 20),
                        a.path("animatePoses").asBoolean(true), a.path("flipX").asBoolean(false), a.path("faceTravel").asBoolean(false),
                        a.path("stationary").asBoolean(false), motionRegions(a), optional(a,"rotationDegrees",-30,30), a.path("oneWay").asBoolean(false), a.has("opacity") ? number(a,"opacity",0,1) : 1, optional(a,"approachScale",0,.5), a.path("poseStationary").asBoolean(false)));
            }
            return new SceneData(background, atlas, width, height, List.copyOf(specifications), settings.path("water"), settings.path("atmosphere"), null, settings.path("foregroundActors").asBoolean(false) ? null : foregroundLoad.join(), settings.path("foreground"));
        } catch (IOException ex) { throw new IllegalStateException("Historical scene unavailable", ex); }
    }
    /** Normalized masks restrict pose changes to hands/heads while props remain frame zero. */
    private static List<Rectangle2D> motionRegions(JsonNode actor) throws IOException {
        JsonNode regions = actor.path("motionRegions");
        if (regions.isMissingNode()) {
            if (actor.path("stationary").asBoolean(false)) throw new IOException("Stationary actor needs motion regions");
            return List.of();
        }
        if (!regions.isArray() || regions.isEmpty() || regions.size() > 8) throw new IOException("Invalid motion regions");
        List<Rectangle2D> result = new ArrayList<>();
        for (JsonNode region : regions) {
            double x = number(region, "x", 0, 1), y = number(region, "y", 0, 1);
            double w = number(region, "width", .001, 1), h = number(region, "height", .001, 1);
            if (x+w > 1.000001 || y+h > 1.000001) throw new IOException("Motion region outside actor");
            result.add(new Rectangle2D(x, y, w, h));
        }
        return List.copyOf(result);
    }
    private static Image bundledImage(String path) throws IOException {
        if (!path.matches("/scene-fixes/[a-z-]+\\.png")) throw new IOException("Invalid bundled atlas");
        try (var stream = CloudEraScene.class.getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Missing bundled atlas: " + path);
            Image image = new Image(stream);
            if (image.isError()) throw new IOException("Invalid bundled atlas: " + path);
            return image;
        }
    }
    static int[] poseSequence(int columns) {
        if (columns == 3) return new int[]{0,1,2,1};
        if (columns != 5 && (columns < 6 || columns > 8))
            throw new IllegalArgumentException("Unsupported pose count");
        return java.util.stream.IntStream.range(0,columns).toArray();
    }
    private static double number(JsonNode node, String name, double min, double max) throws IOException {
        JsonNode value = node.path(name);
        double n = value.asDouble(Double.NaN);
        if (!value.isNumber() || !Double.isFinite(n) || n < min || n > max)
            throw new IOException("Invalid scene setting: " + name);
        return n;
    }
    private static double optional(JsonNode node, String name, double min, double max) throws IOException {
        return node.has(name) ? number(node, name, min, max) : 0;
    }
    private Image image(JsonNode scene, String field, String route) {
        return imageForPath(scene.path(field).asText(), route);
    }
    private Image imageForPath(String path, String route) {
        if (path.isBlank()) throw new IllegalStateException("Missing asset path");
        return IMAGE_CACHE.computeIfAbsent(BACKEND + "/" + path, key -> {
            Image image = new Image(new ByteArrayInputStream(request("/" + route, 12 * 1024 * 1024)));
            if (image.isError() || image.getPixelReader() == null) throw new IllegalStateException("Invalid image");
            return image;
        });
    }
    private byte[] request(String route, int limit) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(BACKEND + "/api/era-scenes/" + sceneId + route)
                    .toURL().openConnection();
            connection.setConnectTimeout(10000); connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(false);
            if (connection.getResponseCode() != 200) throw new IOException("HTTP " + connection.getResponseCode() + " at " + sceneId + route);
            try (var input = connection.getInputStream()) {
                byte[] bytes = input.readNBytes(limit + 1);
                if (bytes.length == 0 || bytes.length > limit) throw new IOException("Invalid response size");
                return bytes;
            }
        } catch (IOException ex) { throw new IllegalStateException("Could not load scene media", ex); }
        finally { if (connection != null) connection.disconnect(); }
    }
    /** Inspect alpha once off the FX thread. All poses share one fixed content box. */
    private static Rectangle2D[] frames(Image atlas, int top, int end, int columns) throws IOException {
        return frames(atlas,top,end,columns,1);
    }
    private static Rectangle2D[] frames(Image atlas, int top, int end, int columns, int rows) throws IOException {
        if (columns*rows > 8 || (end-top)%rows != 0) throw new IOException("Invalid pose grid");
        int count=columns*rows;
        int[] left = new int[count], upper = new int[count]; int cell = Integer.MAX_VALUE;
        int cellHeight=(end-top)/rows;
        for (int frame=0;frame<count;frame++) {
            int col=frame%columns;
            left[frame]=(int)Math.round(col*atlas.getWidth()/columns);
            upper[frame]=top+(frame/columns)*cellHeight;
            cell=Math.min(cell,(int)Math.round((col+1)*atlas.getWidth()/columns)-left[frame]);
        }
        int minX=cell,maxX=-1,minY=cellHeight,maxY=-1;
        var pixels=atlas.getPixelReader();
        for (int frame=0;frame<count;frame++) for(int y=0;y<cellHeight;y++) for(int x=0;x<cell;x++) {
            if((pixels.getArgb(left[frame]+x,upper[frame]+y)>>>24)>80) {
                minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
            }
        }
        if(maxX<minX || maxY<minY)throw new IOException("Empty sprite row");
        Rectangle2D[] frames=new Rectangle2D[count];
        for(int frame=0;frame<count;frame++) frames[frame]=new Rectangle2D(left[frame]+minX,
                upper[frame]+minY,maxX-minX+1,maxY-minY+1);
        return frames;
    }
    private void build(SceneData data) {
        for (Actor actor : actors) actor.motion.stop();
        actors.clear();
        if (waterMotion != null) waterMotion.stop();
        if (atmosphereMotion != null) atmosphereMotion.stop();
        if (originalMotion != null) originalMotion.stop();
        waterMotion = null; atmosphereMotion = null; originalMotion = null;
        canvasWidth = data.width; canvasHeight = data.height;
        ImageView background = new ImageView(data.background);
        background.setId("era-background");
        background.setFitWidth(canvasWidth); background.setFitHeight(canvasHeight);
        background.setSmooth(true);
        if (data.sourceMotion != null) {
            originalMotion = new OriginalImageMotion(background, data.sourceMotion);
            world.getChildren().setAll(background);
            getChildren().setAll(world);
            requestLayout(); syncPlayback();
        if(thumbnail && sceneId.equals("wright-flight")) for(Actor actor:actors){
            actor.motion.play(); actor.motion.pause();
            actor.motion.jumpTo(actor.motion.getCycleDuration().multiply(.35));
        }
            return;
        }
        Pane layers = new Pane(); layers.setPrefSize(canvasWidth, canvasHeight);
        for (ActorData specification : data.actors) {
            Actor actor = new Actor(specification.image, specification);
            actors.add(actor); layers.getChildren().add(actor.layer);
        }
        if (sceneId.equals("egypt-old-kingdom")) {
            Actor sledge = actors.get(1);
            for (int n : new int[]{2, 3}) {
                Actor worker = actors.get(n);
                var rope = new javafx.scene.shape.Line();
                double width = worker.layer.getLayoutBounds().getWidth();
                double height = data.actors.get(n).height;
                rope.startXProperty().bind(worker.layer.layoutXProperty().add(worker.layer.translateXProperty()).add(width * .98));
                rope.startYProperty().bind(worker.layer.layoutYProperty().add(height * .40));
                rope.endXProperty().bind(sledge.layer.layoutXProperty().add(sledge.layer.translateXProperty()).add(42));
                rope.endYProperty().bind(sledge.layer.layoutYProperty().add(67));
                rope.setStroke(javafx.scene.paint.Color.web("#8b6939")); rope.setStrokeWidth(1.8);
                layers.getChildren().add(0, rope);
            }
        }
        Pane water = new Pane();
        water.setMouseTransparent(true);
        JsonNode area = data.water;
        if (area.isObject()) {
            double wx = area.path("x").asDouble(), wy = area.path("y").asDouble();
            double ww = area.path("width").asDouble(), wh = area.path("height").asDouble();
            if (wx < 0 || wy < 0 || ww <= 0 || wh <= 0 || wx + ww > canvasWidth || wy + wh > canvasHeight)
                throw new IllegalArgumentException("Invalid water region");
            water.setClip(new Rectangle(wx, wy, ww, wh));
            List<javafx.scene.shape.Line> ripples = new ArrayList<>();
            for (int n = 0; n < 18; n++) {
                double x = wx + (n * 137 % (int) ww), y = wy + (n * 47 % (int) wh);
                var line = new javafx.scene.shape.Line(x, y, x + 18 + n % 4 * 9, y);
                line.setStroke(javafx.scene.paint.Color.web("#f8e7ba", .25)); line.setStrokeWidth(1.3);
                ripples.add(line);
            }
            water.getChildren().addAll(ripples);
            waterMotion = new Transition() {
                { setCycleDuration(Duration.seconds(12)); setCycleCount(Animation.INDEFINITE); setInterpolator(Interpolator.LINEAR); }
                @Override protected void interpolate(double fraction) {
                    for (int n = 0; n < ripples.size(); n++) {
                        double wave = Math.sin(2 * Math.PI * fraction + n * .7);
                        ripples.get(n).setTranslateX(7 * wave);
                        ripples.get(n).setOpacity(.35 + .3 * wave);
                    }
                }
            };
        }
        if (data.foreground != null && data.foregroundSettings.path("atlasColumns").asInt() == 5) {
            JsonNode placement = data.foregroundSettings;
            try {
                if (data.foreground.getWidth() != number(placement, "atlasWidth", 1, 4096)
                        || data.foreground.getHeight() != number(placement, "atlasHeight", 1, 4096))
                    throw new IOException("Audience atlas dimensions");
                ActorData audienceData = new ActorData("audience", data.foreground,
                        frames(data.foreground, 0, (int) data.foreground.getHeight(), 5),
                        number(placement, "x", 0, canvasWidth), number(placement, "y", 0, canvasHeight),
                        number(placement, "height", 1, canvasHeight),
                        number(placement, "cycleSeconds", .5, 120), number(placement, "cycleSeconds", .5, 120),
                        0, 0, 0, 0, 0, true, false, false, false, motionRegions(placement), 0, false, 1, 0, false);
                Actor audience = new Actor(data.foreground, audienceData);
                actors.add(audience); layers.getChildren().add(audience.layer);
            } catch (IOException e) { throw new IllegalArgumentException("Audience placement", e); }
        } else if (data.foreground != null) {
            JsonNode placement = data.foregroundSettings;
            double fx, fy, fw, fh;
            try {
                fx = number(placement, "x", 0, canvasWidth);
                fy = number(placement, "y", 0, canvasHeight);
                fw = number(placement, "width", 1, canvasWidth);
                fh = number(placement, "height", 1, canvasHeight);
            } catch (IOException e) { throw new IllegalArgumentException("Foreground placement", e); }
            if (fx+fw > canvasWidth || fy+fh > canvasHeight)
                throw new IllegalArgumentException("Foreground outside canvas");
            ImageView foreground = new ImageView(data.foreground);
            foreground.setId("era-foreground"); foreground.setSmooth(true);
            foreground.setFitWidth(fw); foreground.setFitHeight(fh);
            foreground.setLayoutX(fx); foreground.setLayoutY(fy);
            layers.getChildren().add(foreground);
        }
        Pane atmosphere = atmosphere(data.atmosphere);
        world.getChildren().setAll(background, water, layers, atmosphere);
        getChildren().setAll(world);
        requestLayout(); syncPlayback();
        if(thumbnail && sceneId.equals("wright-flight")) for(Actor actor:actors){
            actor.motion.play(); actor.motion.pause();
            actor.motion.jumpTo(actor.motion.getCycleDuration().multiply(.35));
        }
    }
    /** Small atmospheric particles move independently; scenery pixels stay fixed. */
    private Pane atmosphere(JsonNode settings) {
        Pane pane = new Pane(); pane.setMouseTransparent(true);
        if (!settings.isArray()) return pane;
        List<javafx.scene.shape.Circle> dots = new ArrayList<>();
        List<double[]> regions = new ArrayList<>();
        for (JsonNode region : settings) {
            double x = region.path("x").asDouble(-1), y = region.path("y").asDouble(-1);
            double w = region.path("width").asDouble(), h = region.path("height").asDouble();
            if (x < 0 || y < 0 || w <= 0 || h <= 0 || x+w > canvasWidth || y+h > canvasHeight)
                throw new IllegalArgumentException("Invalid atmosphere region");
            String kind = region.path("kind").asText();
            if (!Set.of("dust", "smoke", "falling-water", "flash", "indicator").contains(kind))
                throw new IllegalArgumentException("Invalid atmosphere kind");
            for (int i = 0; i < (kind.equals("flash") || kind.equals("indicator") ? 1 : 18); i++) {
                boolean falling = kind.equals("falling-water");
                var dot = new javafx.scene.shape.Circle(falling ? 1.1 : kind.equals("smoke") ? 5+i%4 : kind.equals("flash") ? 12 : kind.equals("indicator") ? 1.6 : 1.3);
                dot.setCenterX(x+w/2); dot.setCenterY(y+h/2); dot.setOpacity(0);
                dot.setFill(javafx.scene.paint.Color.web(falling ? "#e3f3f7" : kind.equals("smoke") ? "#82766a" : kind.equals("flash") ? "#ffdb91" : kind.equals("indicator") ? "#ffdf85" : "#d7b580"));
                dot.setScaleX(falling ? .7 : 1); dot.setScaleY(falling ? 4 : 1);
                dots.add(dot); regions.add(new double[]{x,y,w,h,i/18.0,falling ? 1 : 0,kind.equals("indicator") ? -2 : kind.equals("flash") ? -1 : kind.equals("smoke") ? .18 : .35});
            }
        }
        pane.getChildren().addAll(dots);
        atmosphereMotion = new Transition() {
            { setCycleDuration(Duration.seconds(12)); setCycleCount(Animation.INDEFINITE); setInterpolator(Interpolator.LINEAR); }
            @Override protected void interpolate(double fraction) {
                for (int i=0;i<dots.size();i++) {
                    double[] r=regions.get(i); double t=(fraction*(r[5]==1 ? 3 : 1)+r[4])%1;
                    var dot=dots.get(i);
                    if(r[6]==-2){dot.setCenterX(r[0]);dot.setCenterY(r[1]);dot.setOpacity(.1+.9*Math.max(0,Math.sin(t*Math.PI*8+i)));continue;}
                    dot.setCenterX(r[0]+r[2]*(.12+.76*((i*0.618)%1)) + Math.sin(t*Math.PI*2+i)*Math.min(3,r[2]*.08));
                    dot.setCenterY(r[1]+r[3]*(r[5]==1 ? t : 1-t));
                    dot.setOpacity(r[6]<0 ? (t>.43 && t<.455 ? .8*Math.sin((t-.43)/.025*Math.PI) : 0) : r[6]*Math.sin(Math.PI*t));
                }
            }
        };
        return pane;
    }
    private void syncPlayback() {
        if (originalMotion != null) switch (animation.getStatus()) {
            case RUNNING -> originalMotion.play();
            case PAUSED -> originalMotion.pause();
            case STOPPED -> originalMotion.stop();
        }
        if (atmosphereMotion != null) switch (animation.getStatus()) {
            case RUNNING -> atmosphereMotion.play();
            case PAUSED -> atmosphereMotion.pause();
            case STOPPED -> atmosphereMotion.stop();
        }
        if (waterMotion != null) switch (animation.getStatus()) {
            case RUNNING -> waterMotion.play();
            case PAUSED -> waterMotion.pause();
            case STOPPED -> waterMotion.stop();
        }
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
    private record SceneData(Image background, Image atlas, double width, double height, List<ActorData> actors, JsonNode water, JsonNode atmosphere, JsonNode sourceMotion, Image foreground, JsonNode foregroundSettings) {}
    private record ActorData(String name, Image image, Rectangle2D[] frames, double x, double y, double height,
                             double cycle, double poseCycle, double phase, double sway, double travelX, double travelY, double bobY, boolean animatePoses, boolean flipX, boolean faceTravel, boolean stationary, List<Rectangle2D> regions, double rotation, boolean oneWay, double opacity, double approachScale, boolean poseStationary) {}
    private static final class Actor {
        final Group layer;
        final Animation motion;
        private static javafx.scene.shape.Shape regionMask(List<Rectangle2D> regions, double width, double height) {
            javafx.scene.shape.Shape shape = null;
            for (Rectangle2D r : regions) {
                Rectangle rectangle = new Rectangle(r.getMinX()*width, r.getMinY()*height,
                        r.getWidth()*width, r.getHeight()*height);
                shape = shape == null ? rectangle : javafx.scene.shape.Shape.union(shape, rectangle);
            }
            return shape;
        }
        Actor(Image atlas, ActorData a) {
            ImageView base = new ImageView(atlas), overlay = new ImageView(atlas);
            base.setId("era-" + a.name); overlay.setMouseTransparent(true);
            double width = a.height * a.frames[0].getWidth() / a.frames[0].getHeight();
            for (ImageView image : List.of(base, overlay)) {
                image.setViewport(a.frames[0]); image.setFitHeight(a.height); image.setFitWidth(width);
                image.setSmooth(true); image.setPreserveRatio(false);
            }
            overlay.setOpacity(0);
            layer = new Group(); layer.setOpacity(a.opacity);
            if(a.approachScale>0){var shadow=new javafx.scene.shape.Ellipse(width/2,a.height-1,width*.28,2);shadow.setFill(javafx.scene.paint.Color.web("#18252a",.16));layer.getChildren().add(shadow);}
            if (a.stationary && !a.poseStationary) {
                // Use only the original pose: generated alternate poses can shift the body.
                // Small independent part rotations leave every unmasked pixel unchanged.
                ImageView fixed = new ImageView(atlas);
                fixed.setId("era-fixed-" + a.name);
                fixed.setViewport(a.frames[0]); fixed.setFitHeight(a.height); fixed.setFitWidth(width);
                fixed.setSmooth(true); fixed.setPreserveRatio(false);
                fixed.setClip(javafx.scene.shape.Shape.subtract(new Rectangle(width, a.height),
                        regionMask(a.regions, width, a.height)));
                base.setOpacity(0); // Stable bounds and actor lookup; the visible parts are below.
                layer.getChildren().addAll(fixed, base);
                List<Rotate> parts = new ArrayList<>();
                for (Rectangle2D r : a.regions) {
                    ImageView part = new ImageView(atlas);
                    part.setViewport(a.frames[0]); part.setFitHeight(a.height); part.setFitWidth(width);
                    part.setSmooth(true); part.setPreserveRatio(false);
                    double rx=r.getMinX()*width, ry=r.getMinY()*a.height;
                    double rw=r.getWidth()*width, rh=r.getHeight()*a.height;
                    Rotate rotate = new Rotate(0, rx+rw/2, ry+rh*.85);
                    part.getTransforms().add(rotate); parts.add(rotate);
                    Group patch = new Group(part);
                    patch.setClip(new Rectangle(rx,ry,rw,rh));
                    layer.getChildren().add(patch);
                }
                if (a.flipX) layer.getTransforms().add(new Scale(-1,1,width/2,0));
                layer.setLayoutX(a.x); layer.setLayoutY(a.y);
                motion = new Transition() {
                    {setCycleDuration(Duration.seconds(a.poseCycle));setCycleCount(Animation.INDEFINITE);setInterpolator(Interpolator.LINEAR);}
                    @Override protected void interpolate(double fraction) {
                        double progress=(fraction+a.phase/a.poseCycle)%1;
                        for (int n=0;n<parts.size();n++)
                            parts.get(n).setAngle((n==0 ? 1.2 : 1.8)*Math.sin(2*Math.PI*progress+n*.6));
                    }
                };
                return;
            }
            if (!a.regions.isEmpty()) {
                ImageView fixed = new ImageView(atlas);
                fixed.setId("era-fixed-" + a.name);
                fixed.setViewport(a.frames[0]); fixed.setFitHeight(a.height); fixed.setFitWidth(width);
                fixed.setSmooth(true); fixed.setPreserveRatio(false);
                javafx.scene.shape.Shape union = regionMask(a.regions, width, a.height);
                fixed.setClip(javafx.scene.shape.Shape.subtract(new Rectangle(width, a.height), union));
                base.setClip(regionMask(a.regions, width, a.height));
                overlay.setClip(regionMask(a.regions, width, a.height));
                layer.getChildren().add(fixed);
            }
            layer.getChildren().addAll(base, overlay);
            layer.relocate(a.x, a.y);
            // Exclude a neighboring cell's tiny edge remnant from the audience strip.
            if (a.name.equals("audience") && a.frames.length == 5 && a.image.getWidth() == 1536)
                layer.setClip(new Rectangle(width * .06, 0, width * .94, a.height));
            Rotate sway = new Rotate(0, width / 2, a.height);
            Scale facing = new Scale(a.flipX ? -1 : 1, 1, width/2, 0);
            Scale depth=new Scale(1,1,width/2,a.height);
            layer.getTransforms().addAll(depth, new Rotate(a.rotation,width/2,a.height), facing, sway);
            int[] sequence = poseSequence(a.frames.length);
            motion = new Transition() {
                { setCycleDuration(Duration.seconds(a.cycle)); setCycleCount(Animation.INDEFINITE);
                  setInterpolator(Interpolator.LINEAR); }
                @Override protected void interpolate(double fraction) {
                    double progress = (fraction + a.phase / a.cycle) % 1;
                    double poseProgress = ((fraction * a.cycle + a.phase) % a.poseCycle) / a.poseCycle;
                    double position = poseProgress * sequence.length;
                    int step = Math.min(sequence.length - 1, (int) position);
                    base.setViewport(a.frames[a.animatePoses ? sequence[step] : 0]);
                    int next = sequence[(step + 1) % sequence.length];
                    overlay.setViewport(a.frames[next]);
                    double blendStart = a.frames.length >= 5 ? .82 : .72;
                    double blend = Math.max(0, Math.min(1, (position - step - blendStart) / (1-blendStart)));
                    overlay.setOpacity(a.animatePoses ? blend * blend * (3 - 2 * blend) : 0);
                    double travel=a.oneWay?progress:(1-Math.cos(2*Math.PI*progress))/2;
                    depth.setX(1+a.approachScale*travel);depth.setY(1+a.approachScale*travel);
                    if(a.oneWay)layer.setOpacity(a.opacity*Math.min(1,Math.min(progress/.015,(1-progress)/.015)));
                    // One-way vehicles fade at their endpoints; other actors ease into turns.
                    layer.setTranslateX((a.stationary ? 0 : a.travelX) * travel);
                    layer.setTranslateY((a.stationary ? 0 : a.travelY) * travel
                            + (a.stationary ? 0 : a.bobY) * Math.sin(4 * Math.PI * progress));
                    if (a.faceTravel && a.travelX != 0) {
                        boolean returning = !a.oneWay && Math.sin(2*Math.PI*progress) < 0;
                        facing.setX((a.flipX ? -1 : 1) * (returning ? -1 : 1));
                    }
                    sway.setAngle((a.stationary ? 0 : a.sway) * Math.sin(2 * Math.PI * progress));
                }
            };
        }
    }
}
