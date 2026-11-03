package org.example.historyinaglimpse;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Lets a logged-in user change their display name and password. */
public final class MyAccountView extends StackPane {
    public MyAccountView() {
        getStyleClass().add("form-page");

        Label title = new Label("My account");
        title.getStyleClass().add("form-title");
        Label who = new Label("Username: " + Session.user().username() + " · " + Session.role().label()
                + (Session.user().google() ? " · Linked to Google" : ""));
        who.getStyleClass().add("form-hint");

        Label nameHeading = new Label("Display name");
        nameHeading.getStyleClass().add("form-section");
        TextField displayName = new TextField(Session.displayName());
        displayName.setId("account-display-name");
        Label nameMessage = message("account-name-message");
        Button saveName = new Button("Save name");
        saveName.setId("account-save-name");
        saveName.getStyleClass().add("primary-button");
        saveName.setOnAction(e -> {
            if (displayName.getText().isBlank()) { LoginView.showError(nameMessage, "Enter a display name."); return; }
            saveName.setDisable(true);
            Session.async(() -> Session.updateDisplayName(displayName.getText()),
                    user -> { showNotice(nameMessage, "Saved."); saveName.setDisable(false); },
                    error -> { LoginView.showError(nameMessage, error); saveName.setDisable(false); });
        });

        Label passwordHeading = new Label("Change password");
        passwordHeading.getStyleClass().add("form-section");
        PasswordField current = new PasswordField();
        current.setId("account-current-password");
        current.setPromptText("Current password");
        PasswordField next = new PasswordField();
        next.setId("account-new-password");
        next.setPromptText("New password (at least 8 characters)");
        PasswordField confirm = new PasswordField();
        confirm.setId("account-confirm-password");
        confirm.setPromptText("Confirm new password");
        Label passwordMessage = message("account-password-message");
        Button savePassword = new Button("Change password");
        savePassword.setId("account-save-password");
        savePassword.getStyleClass().add("primary-button");
        // Accounts made with Google have no password until the user adds one.
        Label googleHint = new Label("You sign in with Google. Add a password to also log in with your username.");
        googleHint.setId("account-google-hint");
        googleHint.setWrapText(true);
        googleHint.getStyleClass().add("form-hint");
        googleHint.managedProperty().bind(googleHint.visibleProperty());
        current.managedProperty().bind(current.visibleProperty());
        Runnable passwordMode = () -> {
            boolean has = Session.user().hasPassword();
            passwordHeading.setText(has ? "Change password" : "Add a password");
            savePassword.setText(has ? "Change password" : "Add password");
            current.setVisible(has);
            googleHint.setVisible(!has);
        };
        passwordMode.run();
        savePassword.setOnAction(e -> {
            boolean adding = !Session.user().hasPassword();
            if (!adding && current.getText().isEmpty()) { LoginView.showError(passwordMessage, "Enter your current password."); return; }
            if (next.getText().length() < 8) { LoginView.showError(passwordMessage, "Passwords need at least 8 characters."); return; }
            if (!next.getText().equals(confirm.getText())) { LoginView.showError(passwordMessage, "The new passwords do not match."); return; }
            savePassword.setDisable(true);
            Session.async(() -> { Session.changePassword(adding ? null : current.getText(), next.getText()); return true; },
                    done -> {
                        current.clear(); next.clear(); confirm.clear();
                        passwordMode.run();
                        showNotice(passwordMessage, adding ? "Password added. You can now log in with your username too."
                                : "Password changed.");
                        savePassword.setDisable(false);
                    },
                    error -> { LoginView.showError(passwordMessage, error); savePassword.setDisable(false); });
        });

        VBox card = new VBox(12, title, who, nameHeading, displayName, nameMessage, saveName, new Separator(),
                passwordHeading, googleHint, current, next, confirm, passwordMessage, savePassword);
        card.getStyleClass().add("form-card");
        card.setMaxWidth(460);
        card.setMaxHeight(Region.USE_PREF_SIZE);
        getChildren().add(card);
    }

    private static Label message(String id) {
        Label label = new Label();
        label.setId(id);
        label.setWrapText(true);
        label.managedProperty().bind(label.textProperty().isNotEmpty());
        label.visibleProperty().bind(label.managedProperty());
        return label;
    }

    static void showNotice(Label message, String text) {
        message.getStyleClass().setAll("label", "form-notice");
        message.setText(text);
    }
}
