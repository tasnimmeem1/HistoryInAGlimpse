package org.example.historyinaglimpse;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import java.util.List;

/** Admin screen: everyone with an account, and a role picker for each. */
public final class ManageAccountsView extends StackPane {
    private final GridPane table = new GridPane();
    private final Label message = new Label();

    public ManageAccountsView() {
        getStyleClass().add("form-page");

        Label title = new Label("Manage accounts");
        title.getStyleClass().add("form-title");
        Label hint = new Label("Administrators can see every account and change roles. "
                + "You cannot change your own role.");
        hint.setWrapText(true);
        hint.getStyleClass().add("form-hint");

        Button refresh = new Button("Refresh");
        refresh.setId("accounts-refresh");
        refresh.setOnAction(e -> { message.setText(""); load(); });
        Pane spacer = new Pane();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(12, title, spacer, refresh);
        header.setAlignment(Pos.CENTER_LEFT);

        message.setId("accounts-message");
        message.setWrapText(true);
        message.managedProperty().bind(message.textProperty().isNotEmpty());
        message.visibleProperty().bind(message.managedProperty());

        table.setId("accounts-table");
        table.setHgap(16);
        table.setVgap(10);
        ColumnConstraints grow = new ColumnConstraints();
        grow.setHgrow(Priority.ALWAYS);
        table.getColumnConstraints().addAll(grow, grow, new ColumnConstraints());
        ScrollPane scroll = new ScrollPane(table);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(360);

        VBox card = new VBox(14, header, hint, message, scroll);
        card.getStyleClass().add("form-card");
        card.setMaxWidth(760);
        card.setMaxHeight(Region.USE_PREF_SIZE);
        getChildren().add(card);
        load();
    }

    private void load() {
        table.getChildren().clear();
        table.add(new Label("Loading accounts…"), 0, 0);
        Session.async(Session::listUsers, this::show, error -> {
            table.getChildren().clear();
            LoginView.showError(message, error);
        });
    }

    private void show(List<Session.User> users) {
        table.getChildren().clear();
        String[] headings = {"Name", "Username", "Role"};
        for (int c = 0; c < headings.length; c++) {
            Label heading = new Label(headings[c]);
            heading.getStyleClass().add("form-section");
            table.add(heading, c, 0);
        }
        int row = 1;
        for (Session.User user : users) {
            boolean self = user.username().equals(Session.user().username());
            String shown = user.displayName() == null || user.displayName().isBlank() ? user.username() : user.displayName();
            table.add(new Label(self ? shown + " (you)" : shown), 0, row);
            table.add(new Label(user.username()), 1, row);
            table.add(rolePicker(user, self), 2, row);
            row++;
        }
        if (users.isEmpty()) table.add(new Label("No accounts yet."), 0, 1);
    }

    private ComboBox<Role> rolePicker(Session.User user, boolean self) {
        ComboBox<Role> picker = new ComboBox<>();
        picker.setId("role-" + user.username());
        picker.getItems().addAll(Role.USER, Role.ADMIN);
        picker.setValue(user.role());
        picker.setDisable(self);
        picker.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Role role) { return role == null ? "" : role.label(); }
            @Override public Role fromString(String text) { return null; }
        });
        picker.setOnAction(e -> {
            Role chosen = picker.getValue();
            if (chosen == null || chosen == user.role()) return;
            picker.setDisable(true);
            Session.async(() -> Session.setRole(user.username(), chosen),
                    changed -> {
                        MyAccountView.showNotice(message, user.username() + " is now " + changed.role().label() + ".");
                        load();
                    },
                    error -> {
                        LoginView.showError(message, error);
                        load();
                    });
        });
        return picker;
    }
}
