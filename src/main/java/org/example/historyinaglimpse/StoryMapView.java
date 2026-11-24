package org.example.historyinaglimpse;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** World map with selectable pins for stories that have coordinates. */
final class StoryMapView extends StackPane {
    private static final double WIDTH = 1000;
    private static final double HEIGHT = 520;
    private final List<Session.Story> stories;
    private final Consumer<Session.Story> openStory;

    StoryMapView(List<Session.Story> stories, Consumer<Session.Story> openStory) {
        this.stories = List.copyOf(stories);
        this.openStory = openStory;
        Canvas canvas = new Canvas(WIDTH, HEIGHT);
        draw(canvas);
        canvas.setOnMouseClicked(event -> {
            Session.Story nearest = null;
            double distance = 15;
            for (Session.Story story : this.stories) {
                double[] point = project(story.longitude(), story.latitude());
                double dx = point[0] - event.getX();
                double dy = point[1] - event.getY();
                double candidate = Math.hypot(dx, dy);
                if (candidate < distance) {
                    nearest = story;
                    distance = candidate;
                }
            }
            if (nearest != null) this.openStory.accept(nearest);
        });
        canvas.setOnMouseMoved(event -> {
            Session.Story nearest = null;
            double distance = 15;
            for (Session.Story story : this.stories) {
                double[] point = project(story.longitude(), story.latitude());
                double candidate = Math.hypot(point[0] - event.getX(), point[1] - event.getY());
                if (candidate < distance) {
                    nearest = story;
                    distance = candidate;
                }
            }
            canvas.setCursor(nearest == null ? javafx.scene.Cursor.DEFAULT : javafx.scene.Cursor.HAND);
            canvas.setAccessibleText(nearest == null ? "World map" : nearest.title() + ", " + nearest.placeName());
        });
        ScrollPane scroll = new ScrollPane(canvas);
        scroll.setFitToWidth(true);
        scroll.setPannable(true);
        getChildren().add(scroll);
    }

    private void draw(Canvas canvas) {
        GraphicsContext graphics = canvas.getGraphicsContext2D();
        graphics.setFill(Color.web("#c6d6d4"));
        graphics.fillRect(0, 0, WIDTH, HEIGHT);
        graphics.setStroke(Color.web("#8ca4a0", .55));
        graphics.setLineWidth(.7);
        for (int longitude = -180; longitude <= 180; longitude += 30) {
            double x = project(longitude, 0)[0];
            graphics.strokeLine(x, 0, x, HEIGHT);
        }
        for (int latitude = -60; latitude <= 60; latitude += 30) {
            double y = project(0, latitude)[1];
            graphics.strokeLine(0, y, WIDTH, y);
        }
        for (double[] polygon : loadLand()) {
            int count = polygon.length / 2;
            double[] x = new double[count];
            double[] y = new double[count];
            for (int i = 0; i < count; i++) {
                double[] point = project(polygon[i * 2], polygon[i * 2 + 1]);
                x[i] = point[0];
                y[i] = point[1];
            }
            graphics.setFill(Color.web("#decd9e"));
            graphics.fillPolygon(x, y, count);
            graphics.setStroke(Color.web("#8c7959"));
            graphics.strokePolygon(x, y, count);
        }
        for (Session.Story story : stories) {
            double[] point = project(story.longitude(), story.latitude());
            graphics.setFill(Color.web("#9f382b"));
            graphics.fillOval(point[0] - 6, point[1] - 6, 12, 12);
            graphics.setStroke(Color.web("#fff8e9"));
            graphics.setLineWidth(2);
            graphics.strokeOval(point[0] - 6, point[1] - 6, 12, 12);
            graphics.setFill(Color.web("#493728"));
            graphics.setFont(Font.font("Georgia", 12));
            graphics.fillText(story.placeName(), Math.min(WIDTH - 120, point[0] + 9), point[1] - 8);
        }
        graphics.setStroke(Color.web("#7d7258"));
        graphics.setLineWidth(1);
        graphics.strokeRect(0, 0, WIDTH, HEIGHT);
    }

    private static double[] project(double longitude, double latitude) {
        double clamped = Math.max(-80, Math.min(80, latitude));
        double mercator = Math.log(Math.tan(Math.PI / 4 + Math.toRadians(clamped) / 2));
        double maxMercator = Math.log(Math.tan(Math.PI / 4 + Math.toRadians(80) / 2));
        return new double[]{
                (longitude + 180) / 360 * WIDTH,
                (maxMercator - mercator) / (maxMercator * 2) * HEIGHT
        };
    }

    private static List<double[]> loadLand() {
        List<double[]> polygons = new ArrayList<>();
        try (var input = StoryMapView.class.getResourceAsStream("/maps/land.tsv")) {
            if (input == null) throw new IllegalStateException("Missing world map geography data.");
            try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] pairs = line.split(";");
                    double[] polygon = new double[pairs.length * 2];
                    for (int i = 0; i < pairs.length; i++) {
                        String[] coordinates = pairs[i].split(",");
                        polygon[i * 2] = Double.parseDouble(coordinates[0]);
                        polygon[i * 2 + 1] = Double.parseDouble(coordinates[1]);
                    }
                    polygons.add(polygon);
                }
            }
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Cannot load world map geography.", ex);
        }
        return List.copyOf(polygons);
    }
}
