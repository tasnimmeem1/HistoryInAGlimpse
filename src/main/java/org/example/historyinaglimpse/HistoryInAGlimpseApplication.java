package org.example.historyinaglimpse;

import javafx.animation.Animation;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

/** Native JavaFX desktop demo: an original timeline overview and animated story. */
public class HistoryInAGlimpseApplication extends Application {
    private final BorderPane root = new BorderPane();
    private final ScrollPane chartScroll = new ScrollPane();
    private final Pane chart = new Pane();
    private javafx.scene.layout.HBox timelineToolbar;
    private double zoom = .65;
    private ScenePlayback playback;
    private Animation storyAnimation;
    private NewtonLawsView newtonView;
    private WattCondenserView wattView;
    private ModernBatchView modernView;
    private Navigator navigator;

    private Image asset(String name) {
        var url = getClass().getResource("/images/" + name);
        if (url == null) throw new IllegalStateException("Missing image: " + name);
        Image image = new Image(url.toExternalForm());
        if (image.isError()) throw new IllegalStateException("Cannot read image: " + name, image.getException());
        return image;
    }

    @Override public void start(Stage stage) {
        navigator = new Navigator(root, this::stopAnimation);
        registerFeatures();
        buildOriginalChart();
        chartScroll.setContent(new Group(new Group(chart)));
        chartScroll.setPannable(true);
        chartScroll.viewportBoundsProperty().addListener((o, a, b) -> {
            if (root.getCenter() == chartScroll && b.getHeight() > 0) fitChartHeight();
        });
        chartScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        chartScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        showSplash();
        Scene scene = new Scene(root, 1200, 780);
        scene.getStylesheets().add(getClass().getResource("/chart.css").toExternalForm());
        stage.setTitle("History in a Glimpse");
        stage.setScene(scene);
        stage.setMinWidth(640);
        stage.setMinHeight(480);
        stage.setOnCloseRequest(e -> stopAnimation());
        stage.setMaximized(true);
        stage.show();
    }

    private void showSplash() {
        stopAnimation();
        Label title = new Label("History in a Glimpse");
        title.getStyleClass().add("splash-title");
        title.setWrapText(true); title.setMaxWidth(850);
        title.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        Label subtitle = new Label("Explore history through illustrated stories");
        subtitle.getStyleClass().add("splash-subtitle"); subtitle.setWrapText(true);
        subtitle.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        Separator divider = new Separator(); divider.setMaxWidth(240);
        Label credit = new Label("Inspired by Sebastian C. Adams");
        credit.getStyleClass().add("splash-credit"); credit.setWrapText(true);
        credit.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        Label chartName = new Label("Synchronological Chart of Universal History");
        chartName.setWrapText(true); chartName.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        for (Label label : java.util.List.of(title, subtitle, credit, chartName)) {
            label.setAlignment(javafx.geometry.Pos.CENTER);
            label.setMinWidth(0);
        }
        Hyperlink reference = new Hyperlink("View the historical inspiration");
        reference.setOnAction(e -> getHostServices().showDocument("https://www.davidrumsey.com/blog/2012/3/28/timeline-maps"));
        Button enter = button("Explore history", () -> {
            if (Session.loggedIn()) navigator.mainMenu(); else navigator.login();
        });
        enter.setId("enter-history"); enter.setDefaultButton(true); enter.getStyleClass().add("splash-enter");
        VBox content = new VBox(22, title, subtitle, divider, credit, chartName, reference, enter);
        content.setAlignment(javafx.geometry.Pos.CENTER); content.setPadding(new Insets(60));
        content.setMaxWidth(1000); content.getStyleClass().add("splash-card");
        StackPane splash = new StackPane(content); splash.getStyleClass().add("splash-root");
        splash.setPadding(new Insets(45));
        root.setTop(null); root.setBottom(null); root.setCenter(splash);
    }

    private void buildOriginalChart() {
        RecreatedChart.draw(chart, index -> {
            showEvent(EventCatalog.EVENTS.get(index));
        });
    }

    private void showEvent(HistoricalEvent event) {
        stopAnimation();
        if (event.title().equals("Newton's Principia") || event.title().equals("Newton’s laws of motion")) {
            newtonView = new NewtonLawsView(this::showChart);
            root.setTop(null); root.setBottom(null); root.setCenter(newtonView);
            return;
        }
        if(event.title().equals("Watt condenser patent")) {
            wattView=new WattCondenserView(this::showChart);
            root.setTop(null);root.setBottom(null);root.setCenter(wattView);return;
        }
        String modernId=BatchCatalog.id(event);
        if(!modernId.isBlank()){modernView=new ModernBatchView(modernId,event.title(),this::showChart);root.setTop(null);root.setBottom(null);root.setCenter(modernView);return;}
        boolean campScene = EventCatalog.EVENTS.indexOf(event) == 11;
        boolean farmingScene = EventCatalog.EVENTS.indexOf(event) == 13;
        String cloudSceneId = CloudEraCatalog.sceneId(event);
        boolean animatedScene = campScene || farmingScene || !cloudSceneId.isBlank();

        Button description = new Button("Description");
        description.setId("description-button");
        description.setTooltip(new Tooltip("Read the description aloud. Click again to stop."));
        description.setAccessibleHelp("Read the description aloud. Click again to stop.");
        Slider volume = new Slider(0, 1, .65);
        volume.setPrefWidth(95);
        volume.setTooltip(new Tooltip("Scene sound volume"));
        Label heading = new Label(event.title());
        heading.setMaxWidth(230);
        root.setTop(new ToolBar(button("← Back to chart", this::showChart), heading,
                description, new Label("Volume"), volume));

        // Reserved for the teammate responsible for historical content.
        StackPane reading = new StackPane();
        reading.setId("reserved-history-panel");
        reading.setMinWidth(220);
        reading.setStyle("-fx-background-color:#f4ead3;");
        description.setDisable(true);
        description.setTooltip(new Tooltip("Available when historical content is added."));

        StackPane picture;
        if (campScene) {
            LivingCamp camp = new LivingCamp();
            picture = camp;
            storyAnimation = camp.animation();
        } else if (!cloudSceneId.isBlank()) {
            CloudEraScene scene = new CloudEraScene(cloudSceneId);
            picture = scene;
            storyAnimation = scene.animation();
        } else if (farmingScene) {
            LivingFarming farming = new LivingFarming();
            picture = farming;
            storyAnimation = farming.animation();
        } else {
            if (event.illustrationSheet().isBlank()) {
                Label name = new Label(event.title()); name.getStyleClass().add("story-title"); name.setWrapText(true);
                VBox inscription = new VBox(28, name, note(event.date())); inscription.setPadding(new Insets(48));
                picture = new StackPane(inscription);
            } else {
                Image atlas = asset(event.illustrationSheet());
                int index = event.illustrationCell() >= 0 ? event.illustrationCell() : EventCatalog.EVENTS.indexOf(event);
                int columns = event.illustrationSheet().equals("prehistory-vignettes.png") ? 3 : 4;
                int rows = event.illustrationSheet().equals("prehistory-vignettes.png") ? 2 : 3;
                ImageView art = new ImageView(atlas);
                double w = atlas.getWidth()/columns, h = atlas.getHeight()/rows;
                art.setViewport(new Rectangle2D((index%columns)*w, (index/columns)*h, w, h));
                art.setPreserveRatio(true); picture = new StackPane(art);
                art.fitWidthProperty().bind(picture.widthProperty().subtract(40));
                art.fitHeightProperty().bind(picture.heightProperty().subtract(40));
            }
            picture.getStyleClass().add("original-chart");
        }
        // Animated stories have one animation view; still stories retain a scene view.
        Tab sceneTab = new Tab(animatedScene ? "Animation" : "Scene", picture);
        sceneTab.setClosable(false);
        ScrollPane mapView = new ScrollPane();
        mapView.setFitToWidth(true);
        Tab mapTab = new Tab("Era map", mapView);
        mapTab.setClosable(false);
        mapTab.setOnSelectionChanged(e -> {
            if (mapTab.isSelected() && mapView.getContent() == null)
                mapView.setContent(EraMap.create(event, url -> getHostServices().showDocument(url)));
        });
        TabPane visuals = new TabPane(sceneTab, mapTab);

        // Routine playback status is not shown in the scene. Keep errors available
        // in the audio controls' tooltips without adding an extra status strip.
        java.util.function.Consumer<String> report = message -> {
            if (message.contains("unavailable") || message.contains("could not")
                    || message.contains("uses Windows speech") || message.contains("unexpectedly")) {
                description.setTooltip(new Tooltip(message));
                volume.setTooltip(new Tooltip(message));
            }
        };
        ScenePlayback current = new ScenePlayback(event, storyAnimation, report);
        playback = current;
        description.setOnAction(e -> {
            boolean enabled = !current.isNarrationEnabled();
            current.setNarrationEnabled(enabled);
            description.pseudoClassStateChanged(
                    javafx.css.PseudoClass.getPseudoClass("narrating"), enabled);
            description.setAccessibleHelp(enabled
                    ? "Stop reading the description." : "Read the description aloud.");
            description.setTooltip(new Tooltip(enabled
                    ? "Stop reading the description." : "Read the description aloud."));
        });
        volume.valueProperty().addListener((o, a, value) -> current.setVolume(value.doubleValue()));
        visuals.getSelectionModel().selectedItemProperty().addListener((o, oldTab, selected) -> {
            boolean showingScene = selected == sceneTab;
            description.setDisable(true);
            current.setActive(showingScene);
        });
        SplitPane split = new SplitPane(visuals, reading);
        split.setDividerPositions(.58);
        root.setCenter(split);
        root.setBottom(null);
        current.setActive(true);
    }

    private void showEventLibrary() {
        stopAnimation();
        TextField search = new TextField(); search.setPromptText("Search rulers, inventions, events or civilizations");
        VBox entries = new VBox(10); entries.setPadding(new Insets(20));
        Runnable update = () -> {
            entries.getChildren().clear();
            String query = search.getText().toLowerCase(java.util.Locale.ROOT).trim();
            EventCatalog.EVENTS.stream().filter(TimelineSearch::included).filter(e -> (e.title() + " " + e.date() + " " + e.description()).toLowerCase(java.util.Locale.ROOT).contains(query))
                .sorted(java.util.Comparator.comparingInt(HistoricalEvent::year)).forEach(event -> {
                    Button entry = button(event.title() + " • " + event.date(), () -> showEvent(event));
                    entry.setWrapText(true); entry.setMaxWidth(Double.MAX_VALUE); entries.getChildren().add(entry);
                });
            if (entries.getChildren().isEmpty()) entries.getChildren().add(note("No matching history entries."));
        };
        search.textProperty().addListener((o, a, b) -> update.run()); update.run();
        ScrollPane list = new ScrollPane(entries); list.setFitToWidth(true);
        root.setTop(new VBox(new ToolBar(button("← Back to chart", this::showChart), new Label("Historical events")), search));
        root.setCenter(list); root.setBottom(null);
    }


    /**
     * Main-menu entries. Teammates replace their placeholder with their own
     * screen, e.g. navigator.addScreen("stories", ..., Role.USER, StoryListView::new).
     */
    private void registerFeatures() {
        navigator.addFeature(new Navigator.Feature("timeline", "Explore the timeline",
                "Browse the illustrated chart of world history and open animated scenes.",
                Role.GUEST, this::showChart));
        navigator.addScreen("stories", "Shared stories",
                "Read, search and share stories with tags and timestamps.",
                Role.USER, () -> Navigator.placeholder("Shared stories"));
        navigator.addScreen("saved", "Saved history & reports",
                "Bookmarks, saved selections, and reports you can preview and export.",
                Role.USER, () -> Navigator.placeholder("Saved history & reports"));
        navigator.addScreen("admin", "Manage accounts",
                "Review users and moderate shared stories.",
                Role.ADMIN, () -> Navigator.placeholder("Manage accounts"));
    }

    private Button button(String text, Runnable action) {
        Button b = new Button(text);
        b.setOnAction(e -> action.run());
        return b;
    }

    private void showChart() {
        stopAnimation();
        if (timelineToolbar == null) {
            timelineToolbar = TimelineSearch.toolbar(chartScroll, chart, this::showEvent);
            Button menu = button("← Main menu", navigator::mainMenu);
            menu.setId("main-menu-button");
            timelineToolbar.getChildren().add(0, menu);
        }
        root.setTop(timelineToolbar);
        root.setBottom(null);
        root.setCenter(chartScroll);
        fitChartHeight();
        javafx.application.Platform.runLater(this::fitChartHeight);
    }

    /** Fit the full chart and its year ruler, retaining horizontal navigation. */
    private void fitChartHeight() {
        double height = chartScroll.getViewportBounds().getHeight();
        if (height <= 0) return;
        zoom = Math.max(.01, (height - 4) / chart.getPrefHeight());
        // Origin-based scaling avoids displaced bounds when the window shrinks.
        chart.getTransforms().setAll(new javafx.scene.transform.Scale(zoom, zoom, 0, 0));
        chartScroll.setVvalue(0);
    }

    private Label note(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setPadding(new Insets(12));
        return l;
    }

    private void stopAnimation() {
        if(modernView!=null){modernView.close();modernView=null;}
        if(wattView!=null){wattView.close();wattView=null;}
        if (newtonView != null) { newtonView.close(); newtonView = null; }
        if (storyAnimation != null) { storyAnimation.stop(); storyAnimation = null; }
        if (playback != null) { playback.close(); playback = null; }
    }

    public static void main(String[] args) { launch(args); }
}
