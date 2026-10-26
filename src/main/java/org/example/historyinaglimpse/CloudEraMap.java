package org.example.historyinaglimpse;

import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import java.io.ByteArrayInputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.concurrent.CompletableFuture;

/** Displays original user-supplied maps, preserving all embedded credits and labels. */
final class CloudEraMap {
    static VBox create(String id) {
        Label state = new Label("Loading the era map...");
        state.setWrapText(true);
        VBox box = new VBox(12, state); box.setMinWidth(0);
        box.setStyle("-fx-padding: 20; -fx-background-color: #efdfbb;");
        Button retry = new Button("Retry map");
        Runnable load = () -> {
            retry.setDisable(true); state.setText("Loading the era map...");
            CompletableFuture.supplyAsync(() -> download(id)).whenComplete((image, error) -> Platform.runLater(() -> {
                retry.setDisable(false);
                if (error != null) { state.setText("Could not load the map. Check the backend and connection."); return; }
                ImageView view = new ImageView(image); view.setPreserveRatio(true); view.setSmooth(true);
                Slider zoom = new Slider(1, 4, 1);
                view.fitWidthProperty().bind(box.widthProperty().subtract(40).multiply(zoom.valueProperty()));
                view.fitHeightProperty().bind(zoom.valueProperty().multiply(570));
                ScrollPane scroll = new ScrollPane(new Group(view)); scroll.setPannable(true);
                scroll.setPrefViewportHeight(590);
                Label caption = new Label(caption(id)); caption.setWrapText(true);
                box.getChildren().setAll(new Label("Era map · zoom to inspect labels"), zoom, scroll, caption);
            }));
        };
        retry.setOnAction(e -> load.run()); box.getChildren().add(retry); load.run(); return box;
    }
    private static String caption(String id) {
        return switch (id) {
            case "sumerian-urban" -> "Fertile Crescent reference map supplied by the user, World History Encyclopedia. Early agriculture context; it does not depict Sumerian city boundaries. Original image and credits preserved.";
            case "egypt-old-kingdom" -> "Old Kingdom reference map · Catette, Wikimedia, September 2021 · CC BY 4.0. Original credits preserved. Map dates differ slightly from the timeline's approximate chronology.";
            case "indus-valley" -> "Indus Valley reference map · © 2012 Encyclopaedia Britannica, Inc., as supplied. Original credits preserved.";
            case "akkadian-empire" -> "Akkadian reference map supplied by the user. Original Google / TerraMetrics imagery credits preserved. Shading is a schematic reconstruction; frontiers and Akkad's location are uncertain.";
            case "egypt-middle-kingdom" -> "Middle Kingdom reference map · Simeon Netchev / World History Encyclopedia · CC BY-NC-ND. https://www.worldhistory.org/image/19956/map-of-the-middle-kingdom-of-egypt-c-2000-bce/ Original image unchanged. Includes a separately labelled later Hyksos overlay (c. 1640–1530 BCE); that overlay does not depict c. 2000 BCE.";
            case "hittite-kingdom" -> "Hittite reference map supplied by the user; original labels and credits preserved. Late Bronze Age borders are approximate.";
            case "shang-dynasty" -> "Shang reference map supplied by the user, with TimeMaps credits preserved. Shading represents approximate regional influence.";
            case "egypt-new-kingdom" -> "New Kingdom reference map supplied by the user. This Nile geography map locates Egyptian and Kushite sites; it does not show the full empire at its greatest extent.";
            case "phoenician-network" -> "Phoenician trading network reference map supplied by the user. Routes are schematic connections rather than territorial borders; original credits preserved.";
            case "zhou-dynasty" -> "Zhou reference map supplied by the user. Regional control and boundaries changed during the long Zhou period; original labels preserved.";
            case "neo-assyrian-empire" -> "Neo-Assyrian reference map supplied by the user, depicting the empire and clients around 630 BCE; original credits preserved.";
            case "archaic-greece" -> "Greek reference map supplied by the user, depicting the Persian Wars, 500\u2013479 BCE. It bridges the end of the Archaic period and the start of the Classical period; original labels preserved.";
            case "neo-babylonian-period" -> "Neo-Babylonian reference map supplied by the user, around 580 BCE. The garden scene is an artistic reconstruction; the location and appearance of the famous Hanging Gardens are uncertain.";
            case "achaemenid-persia" -> "Achaemenid reference map, around 500 BCE, supplied by the user. Thomas Lessman / World History Maps credits remain in the original image.";
            case "classical-greece" -> "Classical Greece reference map supplied by the user, centered on the Peloponnesian War from 431 BCE. Some battle labels refer to later events in that war; original credits preserved.";
            case "mauryan-empire" -> "Mauryan reference map supplied by the user, with World History Encyclopedia credits preserved. The map distinguishes successive phases of expansion; these are not all simultaneous borders.";
            case "parthian-empire" -> "Parthian reference map supplied by the user, labelled 1 CE. WorldHistoryMaps.info credits remain in the original image. This is one selected date within the longer Parthian era.";
            case "qin-dynasty" -> "Qin reference map supplied by the user, with World History Encyclopedia credits preserved. It combines the Warring States, conquest dates and later expansion; it is not a single snapshot of 250 BCE.";
            case "han-dynasty" -> "Han geographic reference map supplied by the user. Boundaries are schematic; the original image has no visible date. The domestic scene is a reconstruction, not a photograph from the Han period.";
            case "western-imperial-rome" -> "Western Roman reference map supplied by the user, World History Encyclopedia, showing the fall of the western empire around 480 CE and earlier territorial outlines. The timeline ends at the conventional 476 CE marker.";
            case "aksumite-empire" -> "Aksum reference map supplied by the user. Arrows represent trading connections rather than territorial borders; original image preserved.";
            case "sasanian-empire" -> "Sasanian reference map supplied by the user. Modern country outlines are geographic context, not ancient administrative divisions. The shaded extent has no visible date.";
            case "classic-maya" -> "Maya regional reference map supplied by the user. It locates sites across different periods, not one unified empire. The plaza is an artistic Classic-period reconstruction.";
            case "eastern-roman-empire" -> "Eastern Roman reference map supplied by the user, labelled 565 CE. Thomas Lessman credits remain in the original image. It depicts one selected date within the much longer eastern Roman chronology.";
            case "ghana-wagadu" -> "Ghana / Wagadu reference map supplied by the user. Encarta / Microsoft credits are preserved. Modern country outlines are geographic context; medieval Wagadu was distinct from modern Ghana.";
            case "gupta-empire" -> "Gupta reference map supplied by the user. Shading is a schematic representation of influence and campaigns; it should not be read as uniform direct administration over every shaded area. The courtyard is an artistic reconstruction.";
            case "royal-society" -> "Modern location reference for London, not England’s borders in 1660. The supplied heritage-site legend does not designate the Royal Society as a World Heritage Site. Original map and credits preserved.";
            default -> "Sumerian geographic context";
        };
    }
    private static Image download(String id) {
        HttpURLConnection connection = null;
        try {
            String backend = System.getProperty("history.animation.backend", "http://127.0.0.1:8080").replaceAll("/+$", "");
            connection = (HttpURLConnection) URI.create(backend + "/api/era-scenes/" + id + "/map").toURL().openConnection();
            connection.setConnectTimeout(10000); connection.setReadTimeout(30000); connection.setInstanceFollowRedirects(false);
            if (connection.getResponseCode() != 200) throw new IllegalStateException("Map request failed");
            byte[] bytes;
            try (var in = connection.getInputStream()) { bytes = in.readNBytes(12 * 1024 * 1024 + 1); }
            if (bytes.length == 0 || bytes.length > 12 * 1024 * 1024) throw new IllegalStateException("Invalid map size");
            Image image = new Image(new ByteArrayInputStream(bytes));
            if (image.isError()) throw new IllegalStateException("Invalid map image");
            return image;
        } catch (Exception ex) { throw new IllegalStateException("Map unavailable", ex); }
        finally { if (connection != null) connection.disconnect(); }
    }
    private CloudEraMap() {}
}
