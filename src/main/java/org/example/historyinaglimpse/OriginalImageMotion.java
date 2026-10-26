package org.example.historyinaglimpse;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.Transition;
import javafx.scene.effect.DisplacementMap;
import javafx.scene.effect.FloatMap;
import javafx.scene.image.ImageView;
import javafx.util.Duration;
import java.util.ArrayList;
import java.util.List;

/** Gentle local motion in the supplied photograph or illustration, without replacement artwork. */
final class OriginalImageMotion extends Transition {
    private final FloatMap map;
    private final double sourceWidth, sourceHeight;
    private final List<Sample> samples = new ArrayList<>();
    private record Part(double x, double y, double rx, double ry,
                        double dx, double dy, double seconds) {}
    private record Sample(int x, int y, double weight, Part part) {}

    OriginalImageMotion(ImageView image, JsonNode regions) {
        double width = image.getImage().getWidth(), height = image.getImage().getHeight();
        sourceWidth = width; sourceHeight = height;
        if (!regions.isArray() || regions.size() > 20)
            throw new IllegalArgumentException("Invalid original-image motion regions");
        int gridWidth = Math.min(512, (int) width);
        int gridHeight = Math.min(512, (int) height);
        map = new FloatMap(gridWidth, gridHeight);
        boolean[] occupied = new boolean[gridWidth * gridHeight];
        for (JsonNode region : regions) {
            Part p = new Part(value(region,"x",0,width), value(region,"y",0,height),
                    value(region,"radiusX",1,width), value(region,"radiusY",1,height),
                    value(region,"amplitudeX",-4,4), value(region,"amplitudeY",-4,4),
                    value(region,"seconds",4,60));
            if (p.x-p.rx < 0 || p.x+p.rx > width || p.y-p.ry < 0 || p.y+p.ry > height)
                throw new IllegalArgumentException("Motion region outside original image");
            for (int y=0; y<gridHeight; y++) for (int x=0; x<gridWidth; x++) {
                double u = ((x+.5)*width/gridWidth-p.x)/p.rx;
                double v = ((y+.5)*height/gridHeight-p.y)/p.ry;
                double distance = u*u+v*v;
                if (distance >= 1) continue;
                if (occupied[y*gridWidth+x])
                    throw new IllegalArgumentException("Original-image motion regions overlap");
                occupied[y*gridWidth+x] = true;
                // Zero value and zero slope at the edge avoid rectangular seams or holes.
                double weight = Math.pow(1-distance, 3);
                samples.add(new Sample(x,y,weight,p));
            }
        }
        if (!samples.isEmpty()) {
            DisplacementMap effect = new DisplacementMap(map);
            effect.setWrap(false);
            image.setEffect(effect);
        }
        setCycleDuration(Duration.seconds(60));
        setCycleCount(Animation.INDEFINITE);
        setInterpolator(Interpolator.LINEAR);
        interpolate(0);
    }
    private static double value(JsonNode n, String key, double min, double max) {
        JsonNode field = n.path(key);
        double value = field.asDouble(Double.NaN);
        if (!field.isNumber() || !Double.isFinite(value) || value < min || value > max)
            throw new IllegalArgumentException("Invalid original-image motion: " + key);
        return value;
    }
    @Override protected void interpolate(double fraction) {
        double time = fraction*60;
        // A one-second envelope returns to the exact source at loop boundaries.
        double envelope = Math.min(1, Math.min(time, 60-time));
        envelope = Math.max(0, envelope);
        envelope = envelope*envelope*(3-2*envelope);
        for (Sample sample : samples) {
            Part p = sample.part;
            double wave = Math.sin(2*Math.PI*time/p.seconds)*sample.weight*envelope;
            // DisplacementMap samples are normalized to the image dimensions.
            map.setSamples(sample.x,sample.y,(float)(p.dx*wave/sourceWidth),
                    (float)(p.dy*wave/sourceHeight));
        }
    }
}
