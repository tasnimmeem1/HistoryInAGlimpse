package org.example.historyinaglimpse;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Login screen, with links to registration and guest access. */
public final class LoginView extends StackPane {
    public LoginView(Navigator navigator, String notice) {
        getStyleClass().add("form-page");

        Label title = new Label("Welcome back");
        title.getStyleClass().add("form-title");
        Label subtitle = new Label("Log in to share stories and save your history.");
        subtitle.getStyleClass().add("form-hint");
        subtitle.setWrapText(true);

        TextField username = new TextField();
        username.setId("login-username");
        username.setPromptText("Username");
        PasswordField password = new PasswordField();
        password.setId("login-password");
        password.setPromptText("Password");

        Label message = new Label(notice == null ? "" : notice);
        message.setId("login-message");
        message.getStyleClass().add(notice == null ? "form-error" : "form-notice");
        message.setWrapText(true);
        message.managedProperty().bind(message.textProperty().isNotEmpty());
        message.visibleProperty().bind(message.managedProperty());

        Button login = new Button("Log in");
        login.setId("login-button");
        login.setDefaultButton(true);
        login.getStyleClass().add("primary-button");
        login.setMaxWidth(Double.MAX_VALUE);
        login.setOnAction(e -> {
            if (username.getText().isBlank() || password.getText().isEmpty()) {
                showError(message, "Enter your username and password.");
                return;
            }
            login.setDisable(true);
            login.setText("Logging in…");
            Session.async(() -> Session.login(username.getText(), password.getText()),
                    user -> navigator.mainMenu(),
                    error -> {
                        showError(message, error);
                        password.clear();
                        login.setDisable(false);
                        login.setText("Log in");
                    });
        });

        Hyperlink register = new Hyperlink("Create an account");
        register.setId("register-link");
        register.setOnAction(e -> navigator.register());
        Hyperlink guest = new Hyperlink("Continue as guest");
        guest.setId("guest-button");
        guest.setOnAction(e -> navigator.mainMenu());
        HBox links = new HBox(12, register, new Label("·"), guest);
        links.setAlignment(Pos.CENTER);

        VBox card = new VBox(14, title, subtitle, message, username, password, login, links);
        card.getStyleClass().add("form-card");
        card.setMaxWidth(420);
        card.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        getChildren().add(card);
    }

    static void showError(Label message, String text) {
        message.getStyleClass().setAll("label", "form-error");
        message.setText(text);
    }
}
