package org.example.historyinaglimpse;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Registration screen. Creates the account, logs in, and opens the main menu. */
public final class RegisterView extends StackPane {
    public RegisterView(Navigator navigator) {
        getStyleClass().add("form-page");

        Label title = new Label("Create an account");
        title.getStyleClass().add("form-title");
        title.setWrapText(true);
        title.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);

        TextField username = new TextField();
        username.setId("register-username");
        username.setPromptText("Username (3–30 letters, numbers or _)");
        TextField displayName = new TextField();
        displayName.setId("register-display-name");
        displayName.setPromptText("Display name (optional)");
        PasswordField password = new PasswordField();
        password.setId("register-password");
        password.setPromptText("Password (at least 8 characters)");
        PasswordField confirm = new PasswordField();
        confirm.setId("register-confirm");
        confirm.setPromptText("Confirm password");

        Label message = new Label();
        message.setId("register-message");
        message.getStyleClass().add("form-error");
        message.setWrapText(true);
        message.managedProperty().bind(message.textProperty().isNotEmpty());
        message.visibleProperty().bind(message.managedProperty());

        Button create = new Button("Create account");
        create.setId("register-button");
        create.setDefaultButton(true);
        create.getStyleClass().add("primary-button");
        create.setMaxWidth(Double.MAX_VALUE);
        create.setOnAction(e -> {
            String problem = problem(username.getText(), password.getText(), confirm.getText());
            if (problem != null) { LoginView.showError(message, problem); return; }
            create.setDisable(true);
            create.setText("Creating account…");
            Session.async(() -> Session.register(username.getText(), displayName.getText(), password.getText()),
                    user -> navigator.mainMenu(),
                    error -> {
                        LoginView.showError(message, error);
                        create.setDisable(false);
                        create.setText("Create account");
                    });
        });

        Hyperlink back = new Hyperlink("I already have an account");
        back.setId("back-to-login");
        back.setOnAction(e -> navigator.login());

        VBox card = new VBox(14, title, message, username, displayName, password, confirm, create, back);
        card.getStyleClass().add("form-card");
        card.setMaxWidth(420);
        card.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        card.setAlignment(Pos.TOP_LEFT);
        getChildren().add(card);
    }

    /** Same rules as the backend, checked first so users get instant feedback. */
    static String problem(String username, String password, String confirm) {
        String name = username.strip().toLowerCase(java.util.Locale.ROOT);
        if (!name.matches("[a-z0-9_]{3,30}")) return "Usernames are 3 to 30 letters, numbers or underscores.";
        if (password.length() < 8) return "Passwords need at least 8 characters.";
        if (!password.equals(confirm)) return "The passwords do not match.";
        return null;
    }
}
