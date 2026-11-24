package org.example.historyinaglimpse;

import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import java.util.Comparator;
import java.util.List;

/** Lets signed-in users publish stories and manage their own submissions. */
public final class StoryView extends StackPane {
    private final TextField title = new TextField();
    private final TextArea content = new TextArea();
    private final TextField tags = new TextField();
    private final Label formMessage = new Label();
    private final Label listMessage = new Label();
    private final Button save = new Button("Share story");
    private final VBox entries = new VBox(12);
    private Session.Story editing;

    public StoryView() {
        getStyleClass().add("form-page");

        Label heading = new Label("Share a story");
        heading.getStyleClass().add("form-title");
        Label hint = new Label("Stories are shared with signed-in members. You can edit or remove your own stories.");
        hint.getStyleClass().add("form-hint");
        hint.setWrapText(true);

        title.setId("story-title");
        title.setPromptText("Title");
        title.setMaxWidth(Double.MAX_VALUE);
        content.setId("story-content");
        content.setPromptText("Write your story…");
        content.setWrapText(true);
        content.setPrefRowCount(7);
        tags.setId("story-tags");
        tags.setPromptText("Tags, separated by commas (optional)");
        save.setId("story-save");
        save.getStyleClass().add("primary-button");
        save.setOnAction(e -> submit());
        Button cancel = new Button("Cancel edit");
        cancel.setOnAction(e -> clearForm());
        cancel.visibleProperty().bind(save.textProperty().isEqualTo("Save changes"));
        cancel.managedProperty().bind(cancel.visibleProperty());
        formMessage.setId("story-form-message");
        formMessage.setWrapText(true);
        formMessage.managedProperty().bind(formMessage.textProperty().isNotEmpty());
        formMessage.visibleProperty().bind(formMessage.managedProperty());

        VBox form = new VBox(10, heading, hint, title, content, tags, formMessage,
                new HBox(10, save, cancel));
        form.getStyleClass().add("form-card");
        form.setMaxWidth(780);

        Label listHeading = new Label("Community stories");
        listHeading.getStyleClass().add("form-section");
        listMessage.setWrapText(true);
        listMessage.getStyleClass().add("form-hint");
        listMessage.managedProperty().bind(listMessage.textProperty().isNotEmpty());
        listMessage.visibleProperty().bind(listMessage.managedProperty());
        entries.setPadding(new Insets(0, 0, 20, 0));
        VBox page = new VBox(18, form, new Separator(), listHeading, listMessage, entries);
        page.setPadding(new Insets(4));
        page.setMaxWidth(900);
        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        getChildren().add(scroll);
        refresh();
    }

    private void submit() {
        if (title.getText().isBlank()) {
            showError(formMessage, "Enter a story title.");
            return;
        }
        if (content.getText().isBlank()) {
            showError(formMessage, "Write your story before sharing it.");
            return;
        }
        save.setDisable(true);
        Session.Story current = editing;
        Session.async(() -> current == null
                        ? Session.createStory(title.getText(), content.getText(), tags.getText())
                        : Session.updateStory(current.id(), title.getText(), content.getText(), tags.getText()),
                story -> {
                    clearForm();
                    StoryView.showNotice(listMessage, current == null ? "Your story has been shared." : "Your changes have been saved.");
                    save.setDisable(false);
                    refresh();
                },
                error -> {
                    showError(formMessage, error);
                    save.setDisable(false);
                });
    }

    private void refresh() {
        listMessage.setText("Loading stories…");
        Session.async(Session::listStories, stories -> {
                    entries.getChildren().clear();
                    List<Session.Story> ordered = stories.stream()
                            .sorted(Comparator.comparing(Session.Story::createdAt,
                                    Comparator.nullsLast(Comparator.reverseOrder())))
                            .toList();
                    if (ordered.isEmpty()) {
                        entries.getChildren().add(note("No stories have been shared yet."));
                    } else {
                        ordered.forEach(story -> entries.getChildren().add(storyCard(story)));
                    }
                    listMessage.setText("");
                },
                error -> {
                    entries.getChildren().clear();
                    showError(listMessage, error);
                });
    }

    private VBox storyCard(Session.Story story) {
        Label heading = new Label(story.title());
        heading.getStyleClass().add("form-section");
        heading.setWrapText(true);
        String author = story.authorDisplayName() == null || story.authorDisplayName().isBlank()
                ? story.authorUsername() : story.authorDisplayName();
        Label byline = note("By " + author + (story.createdAt() == null ? "" : " · " + story.createdAt()));
        byline.getStyleClass().add("form-hint");
        Label body = note(story.content());
        Label storyTags = note(story.tags() == null || story.tags().isBlank() ? "" : "Tags: " + story.tags());
        storyTags.getStyleClass().add("form-hint");
        storyTags.managedProperty().bind(storyTags.textProperty().isNotEmpty());
        storyTags.visibleProperty().bind(storyTags.managedProperty());
        VBox card = new VBox(8, heading, byline, body, storyTags);
        card.getStyleClass().add("form-card");
        card.setMaxWidth(780);
        if (Session.user() != null && Session.user().username().equals(story.authorUsername())) {
            Button edit = new Button("Edit");
            edit.setOnAction(e -> beginEdit(story));
            Button delete = new Button("Delete");
            delete.setOnAction(e -> confirmDelete(story));
            card.getChildren().add(new HBox(8, edit, delete));
        }
        return card;
    }

    private void beginEdit(Session.Story story) {
        editing = story;
        title.setText(story.title());
        content.setText(story.content());
        tags.setText(story.tags() == null ? "" : story.tags());
        save.setText("Save changes");
        formMessage.setText("");
        title.requestFocus();
    }

    private void confirmDelete(Session.Story story) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Delete story");
        confirmation.setHeaderText("Delete “" + story.title() + "”?");
        confirmation.setContentText("This cannot be undone.");
        if (confirmation.showAndWait().orElse(null) != javafx.scene.control.ButtonType.OK) return;
        Session.async(() -> {
                    Session.deleteStory(story.id());
                    return true;
                },
                done -> {
                    if (editing != null && editing.id().equals(story.id())) clearForm();
                    StoryView.showNotice(listMessage, "The story was deleted.");
                    refresh();
                },
                error -> showError(listMessage, error));
    }

    private void clearForm() {
        editing = null;
        title.clear();
        content.clear();
        tags.clear();
        formMessage.setText("");
        save.setText("Share story");
    }

    private static Label note(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private static void showError(Label label, String text) {
        label.getStyleClass().setAll("label", "form-error");
        label.setText(text);
    }

    private static void showNotice(Label label, String text) {
        label.getStyleClass().setAll("label", "form-notice");
        label.setText(text);
    }
}
