package org.example.historyinaglimpse;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Main menu: one tile per registered feature, locked by role. */
public final class MainMenuView extends BorderPane {
    public MainMenuView(Navigator navigator) {
        getStyleClass().add("menu-page");

        Label title = new Label("History in a Glimpse");
        title.getStyleClass().add("menu-title");
        Label greeting = new Label("Welcome, " + Session.displayName());
        greeting.setId("menu-greeting");
        greeting.getStyleClass().add("form-hint");
        VBox heading = new VBox(4, title, greeting);

        Pane spacer = new Pane();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label role = new Label(Session.role().label());
        role.setId("menu-role");
        role.getStyleClass().add("role-badge");
        Button account = new Button(Session.loggedIn() ? "Log out" : "Log in");
        account.setId(Session.loggedIn() ? "logout-button" : "menu-login-button");
        account.setOnAction(e -> {
            if (Session.loggedIn()) navigator.logout();
            else navigator.login();
        });
        HBox header = new HBox(14, heading, spacer, role, account);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(28, 40, 12, 40));
        setTop(header);

        FlowPane tiles = new FlowPane(20, 20);
        tiles.setPadding(new Insets(20, 40, 40, 40));
        for (Navigator.Feature feature : navigator.features()) {
            // Admin tools are hidden from everyone else; other locked tiles show why.
            if (feature.minimum() == Role.ADMIN && !Session.allows(Role.ADMIN)) continue;
            tiles.getChildren().add(tile(navigator, feature));
        }
        setCenter(tiles);
    }

    private static Button tile(Navigator navigator, Navigator.Feature feature) {
        Label name = new Label(feature.title());
        name.getStyleClass().add("menu-tile-title");
        name.setWrapText(true);
        name.setMinHeight(Region.USE_PREF_SIZE);
        Label about = new Label(feature.description());
        about.setWrapText(true);
        about.setMinHeight(Region.USE_PREF_SIZE);
        about.getStyleClass().add("menu-tile-text");
        VBox content = new VBox(8, name, about);
        content.setPrefWidth(244);
        boolean allowed = Session.allows(feature.minimum());
        if (!allowed) {
            Label lock = new Label("Log in to use this");
            lock.getStyleClass().add("menu-tile-lock");
            content.getChildren().add(lock);
        }
        Button tile = new Button();
        tile.setGraphic(content);
        tile.setId("menu-" + feature.id());
        tile.getStyleClass().add("menu-tile");
        // Fixed size so wrapped text lays out the same in every tile.
        tile.setMinSize(280, 170);
        tile.setPrefSize(280, 170);
        tile.setMaxSize(280, 170);
        tile.setAlignment(Pos.TOP_LEFT);
        if (!allowed) {
            tile.getStyleClass().add("menu-tile-locked");
            tile.setTooltip(new Tooltip("Requires an account"));
        }
        tile.setOnAction(e -> navigator.open(feature));
        return tile;
    }
}
