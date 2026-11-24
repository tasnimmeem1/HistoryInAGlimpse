package org.example.historyinaglimpse;

import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.function.Consumer;

/** Shared-story list, detail, and authoring screens. */
public final class StoryView extends StackPane {
    private static final String ALL_TAGS = "All tags";
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a", Locale.getDefault());

    private final TextField title = new TextField();
    private final TextArea content = new TextArea();
    private final TextField tags = new TextField();
    private final Label formMessage = new Label();
    private final Label listMessage = new Label();
    private final Button save = new Button();
    private final TextField search = new TextField();
    private final ComboBox<String> tagFilter = new ComboBox<>();
    private final VBox entries = new VBox(12);
    private List<Session.Story> stories = List.of();
    private Session.Story editing;

    public StoryView() {
        getStyleClass().add("form-page");
        title.setId("story-title");
        title.setPromptText("Title");
        content.setId("story-content");
        content.setPromptText("Write your story…");
        content.setWrapText(true);
        content.setPrefRowCount(9);
        tags.setId("story-tags");
        tags.setPromptText("Tags, separated by commas (optional)");
        save.setId("story-save");
        save.getStyleClass().add("primary-button");
        save.setOnAction(e -> submit());
        formMessage.setId("story-form-message");
        formMessage.setWrapText(true);
        formMessage.managedProperty().bind(formMessage.textProperty().isNotEmpty());
        formMessage.visibleProperty().bind(formMessage.managedProperty());
        listMessage.setWrapText(true);
        listMessage.getStyleClass().add("form-hint");
        listMessage.managedProperty().bind(listMessage.textProperty().isNotEmpty());
        listMessage.visibleProperty().bind(listMessage.managedProperty());
        search.setId("story-search");
        search.setPromptText("Search titles, stories, authors, or tags");
        search.textProperty().addListener((o, oldValue, newValue) -> renderEntries());
        tagFilter.setId("story-tag-filter");
        tagFilter.setPromptText("Filter by tag");
        tagFilter.setMaxWidth(Double.MAX_VALUE);
        tagFilter.setOnAction(e -> renderEntries());
        entries.setPadding(new Insets(0, 0, 20, 0));
        showList();
        refresh();
    }

    private void showList() {
        Label heading = new Label("Community stories");
        heading.getStyleClass().add("form-title");
        Label hint = new Label("Search and filter stories, or open one to read it in full.");
        hint.getStyleClass().add("form-hint");
        hint.setWrapText(true);
        Button create = new Button("Write a story");
        create.getStyleClass().add("primary-button");
        create.setOnAction(e -> showEditor(null, false));
        HBox filters = new HBox(10, search, tagFilter);
        HBox.setHgrow(search, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(tagFilter, javafx.scene.layout.Priority.ALWAYS);
        VBox page = new VBox(16, heading, hint, create, filters, listMessage, new Separator(), entries);
        page.setPadding(new Insets(10));
        page.setMaxWidth(920);
        showScrollable(page);
        renderEntries();
    }

    private void refresh() {
        listMessage.setText("Loading stories…");
        Session.async(Session::listStories, result -> {
                    stories = result.stream()
                            .sorted(Comparator.comparing(Session.Story::createdAt,
                                    Comparator.nullsLast(Comparator.reverseOrder())))
                            .toList();
                    updateTagChoices();
                    listMessage.setText("");
                    renderEntries();
                },
                error -> showError(listMessage, error));
    }

    private void updateTagChoices() {
        String selected = tagFilter.getValue();
        TreeSet<String> available = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        stories.forEach(story -> available.addAll(parseTags(story.tags())));
        tagFilter.getItems().setAll(ALL_TAGS);
        tagFilter.getItems().addAll(available);
        tagFilter.setValue(available.stream().filter(tag -> tag.equalsIgnoreCase(selected)).findFirst()
                .orElse(ALL_TAGS));
    }

    private void renderEntries() {
        if (entries == null) return;
        entries.getChildren().clear();
        String query = search.getText() == null ? "" : search.getText().strip().toLowerCase(Locale.ROOT);
        String selectedTag = tagFilter.getValue();
        List<Session.Story> filtered = stories.stream()
                .filter(story -> query.isEmpty() || searchableText(story).contains(query))
                .filter(story -> selectedTag == null || ALL_TAGS.equals(selectedTag)
                        || parseTags(story.tags()).stream().anyMatch(tag -> tag.equalsIgnoreCase(selectedTag)))
                .toList();
        if (filtered.isEmpty()) {
            entries.getChildren().add(note(stories.isEmpty()
                    ? "No stories have been shared yet."
                    : "No stories match your search and tag filter."));
            return;
        }
        filtered.forEach(story -> entries.getChildren().add(storyCard(story)));
    }

    private VBox storyCard(Session.Story story) {
        Label heading = new Label(story.title());
        heading.getStyleClass().add("form-section");
        heading.setWrapText(true);
        String author = authorName(story);
        VBox card = new VBox(8,
                heading,
                note("By " + author + " · " + formatTimestamp(story.createdAt())),
                note(excerpt(story.content())));
        card.getStyleClass().add("form-card");
        card.setMaxWidth(860);
        card.getChildren().get(1).getStyleClass().add("form-hint");
        card.getChildren().add(tagLabels(story.tags()));
        Button read = new Button("Read story");
        read.setOnAction(e -> showDetail(story));
        HBox actions = new HBox(8, read);
        if (isAuthor(story)) {
            Button edit = new Button("Edit");
            edit.setOnAction(e -> showEditor(story, true));
            Button delete = new Button("Delete");
            delete.setOnAction(e -> confirmDelete(story));
            actions.getChildren().addAll(edit, delete);
        }
        card.getChildren().add(actions);
        return card;
    }

    private void showDetail(Session.Story story) {
        Label heading = new Label(story.title());
        heading.getStyleClass().add("form-title");
        heading.setWrapText(true);
        Label byline = note("By " + authorName(story));
        byline.getStyleClass().add("form-hint");
        VBox timestamps = new VBox(5,
                note("Published " + formatTimestamp(story.createdAt())),
                note("Last updated " + formatTimestamp(story.updatedAt())));
        timestamps.getStyleClass().add("form-hint");
        VBox card = new VBox(14, heading, byline, timestamps, tagLabels(story.tags()),
                new Separator(), note(story.content()));
        card.getStyleClass().add("form-card");
        card.setMaxWidth(860);
        Button back = new Button("← All stories");
        back.setOnAction(e -> showList());
        HBox actions = new HBox(8, back);
        if (isAuthor(story)) {
            Button edit = new Button("Edit story");
            edit.setOnAction(e -> showEditor(story, true));
            Button delete = new Button("Delete story");
            delete.setOnAction(e -> confirmDelete(story));
            actions.getChildren().addAll(edit, delete);
        }
        VBox page = new VBox(16, actions, card);
        page.setPadding(new Insets(10));
        page.setMaxWidth(920);
        showScrollable(page);
    }

    private void showEditor(Session.Story story, boolean returnToDetail) {
        editing = story;
        Label heading = new Label(story == null ? "Share a story" : "Edit story");
        heading.getStyleClass().add("form-title");
        Label hint = new Label("Share a historical story with the community. Use commas between tags.");
        hint.getStyleClass().add("form-hint");
        hint.setWrapText(true);
        title.setText(story == null ? "" : story.title());
        content.setText(story == null ? "" : story.content());
        tags.setText(story == null || story.tags() == null ? "" : story.tags());
        save.setText(story == null ? "Share story" : "Save changes");
        formMessage.setText("");
        Button cancel = new Button("Cancel");
        cancel.setOnAction(e -> {
            if (returnToDetail && story != null) showDetail(story);
            else showList();
        });
        VBox card = new VBox(12, heading, hint, title, content, tags, formMessage,
                new HBox(10, save, cancel));
        card.getStyleClass().add("form-card");
        card.setMaxWidth(780);
        card.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        StackPane page = new StackPane(card);
        page.setPadding(new Insets(10));
        showScrollable(page);
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
                    save.setDisable(false);
                    stories = new ArrayList<>(stories);
                    stories.removeIf(existing -> existing.id().equals(story.id()));
                    stories.add(0, story);
                    updateTagChoices();
                    listMessage.setText("");
                    showDetail(story);
                },
                error -> {
                    showError(formMessage, error);
                    save.setDisable(false);
                });
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
                    stories = stories.stream().filter(existing -> !existing.id().equals(story.id())).toList();
                    updateTagChoices();
                    showList();
                    showNotice(listMessage, "The story was deleted.");
                },
                error -> {
                    showList();
                    showError(listMessage, error);
                });
    }

    private boolean isAuthor(Session.Story story) {
        return Session.user() != null && Session.user().username().equals(story.authorUsername());
    }

    private static String authorName(Session.Story story) {
        return story.authorDisplayName() == null || story.authorDisplayName().isBlank()
                ? story.authorUsername() : story.authorDisplayName();
    }

    private static String searchableText(Session.Story story) {
        return (story.title() + " " + story.content() + " " + authorName(story) + " "
                + story.authorUsername() + " " + (story.tags() == null ? "" : story.tags()))
                .toLowerCase(Locale.ROOT);
    }

    private static List<String> parseTags(String value) {
        if (value == null || value.isBlank()) return List.of();
        return java.util.Arrays.stream(value.split(","))
                .map(String::strip).filter(tag -> !tag.isEmpty()).distinct().toList();
    }

    private static VBox tagLabels(String value) {
        List<String> values = parseTags(value);
        if (values.isEmpty()) return new VBox();
        HBox chips = new HBox(6);
        chips.setFillHeight(true);
        values.forEach(tag -> {
            Label chip = new Label("#" + tag);
            chip.getStyleClass().add("story-tag");
            chips.getChildren().add(chip);
        });
        return new VBox(chips);
    }

    private static String excerpt(String value) {
        String text = value == null ? "" : value.strip();
        if (text.length() <= 280) return text;
        return text.substring(0, 277).stripTrailing() + "…";
    }

    private static String formatTimestamp(String value) {
        if (value == null || value.isBlank()) return "Unknown date";
        try {
            return TIMESTAMP.withZone(ZoneId.systemDefault()).format(Instant.parse(value));
        } catch (RuntimeException ignored) {
            try {
                return TIMESTAMP.withZone(ZoneId.systemDefault()).format(OffsetDateTime.parse(value));
            } catch (RuntimeException invalid) {
                return value;
            }
        }
    }

    private void showScrollable(javafx.scene.Node page) {
        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        getChildren().setAll(scroll);
    }

    private static Label note(String text) {
        Label label = new Label(text == null ? "" : text);
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
