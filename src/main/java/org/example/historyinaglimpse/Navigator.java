package org.example.historyinaglimpse;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Switches screens in the main window and keeps the list of features shown
 * on the main menu.
 *
 * To add your screen, call {@link #addScreen} in
 * {@code HistoryInAGlimpseApplication.registerFeatures()}. It gets a toolbar with
 * a "Main menu" button, and the menu only lets in users with the role you name.
 */
public final class Navigator {
    /** One main-menu entry. {@code open} shows the feature's screen. */
    public record Feature(String id, String title, String description, Role minimum, Runnable open) { }

    private final BorderPane root;
    private final Runnable beforeChange;
    private final List<Feature> features = new ArrayList<>();

    /** {@code beforeChange} runs before every screen change, e.g. to stop animations and sound. */
    public Navigator(BorderPane root, Runnable beforeChange) {
        this.root = root;
        this.beforeChange = beforeChange;
    }

    /** Replaces the whole window content. */
    public void show(Node top, Node center) {
        beforeChange.run();
        root.setTop(top);
        root.setCenter(center);
        root.setBottom(null);
    }

    public void addFeature(Feature feature) { features.add(feature); }

    /** Registers a teammate's screen. The supplier builds a fresh screen each time it opens. */
    public void addScreen(String id, String title, String description, Role minimum, Supplier<? extends Node> screen) {
        addFeature(new Feature(id, title, description, minimum, () -> show(toolbar(title), screen.get())));
    }

    public List<Feature> features() { return Collections.unmodifiableList(features); }

    /** Opens a feature if the current user may use it, otherwise asks them to log in. */
    public void open(Feature feature) {
        if (Session.allows(feature.minimum())) feature.open().run();
        else if (!Session.loggedIn()) login("Log in to use " + feature.title() + ".");
        else mainMenu();
    }

    public void open(String id) {
        features.stream().filter(f -> f.id().equals(id)).findFirst().ifPresent(this::open);
    }

    public void login() { login(null); }
    public void login(String notice) { show(null, new LoginView(this, notice)); }
    public void register() { show(null, new RegisterView(this)); }
    public void mainMenu() { show(null, new MainMenuView(this)); }

    public void logout() {
        Session.logout();
        login("You have logged out.");
    }

    /** Standard toolbar: back to the main menu, the screen title, and who is signed in. */
    public ToolBar toolbar(String title) {
        Button back = new Button("← Main menu");
        back.setId("main-menu-button");
        back.setOnAction(e -> mainMenu());
        Label heading = new Label(title);
        heading.getStyleClass().add("toolbar-title");
        Pane spacer = new Pane();
        javafx.scene.layout.HBox.setHgrow(spacer, Priority.ALWAYS);
        Label who = new Label(Session.displayName() + " · " + Session.role().label());
        who.getStyleClass().add("signed-in-as");
        return new ToolBar(back, heading, spacer, who);
    }

    /** Stand-in for a screen a teammate has not built yet. */
    public static Node placeholder(String title, String owner) {
        Label heading = new Label(title);
        heading.getStyleClass().add("form-title");
        Label body = new Label(owner + " will build this screen. Register it with Navigator.addScreen(...) "
                + "in HistoryInAGlimpseApplication.registerFeatures().");
        body.setWrapText(true);
        body.getStyleClass().add("form-hint");
        VBox card = new VBox(16, heading, body);
        card.getStyleClass().add("form-card");
        card.setMaxWidth(560);
        card.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        card.setAlignment(Pos.CENTER_LEFT);
        StackPane page = new StackPane(card);
        page.getStyleClass().add("form-page");
        return page;
    }
}
