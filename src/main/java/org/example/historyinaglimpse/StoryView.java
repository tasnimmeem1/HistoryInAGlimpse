package org.example.historyinaglimpse;

import javafx.geometry.Insets;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.canvas.Canvas;
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
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/** Shared-story list, detail, and authoring screens. */
public final class StoryView extends StackPane {
    private static final String ALL_TAGS = "All tags";
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a", Locale.getDefault());

    private final TextField title = new TextField();
    private final TextArea content = new TextArea();
    private final TextField tags = new TextField();
    private final TextField historicalDate = new TextField();
    private final TextField placeName = new TextField();
    private final TextField latitude = new TextField();
    private final TextField longitude = new TextField();
    private final Label imageName = new Label("No image selected");
    private final ImageView imagePreview = new ImageView();
    private final Label formMessage = new Label();
    private final Label listMessage = new Label();
    private final Button save = new Button();
    private final TextField search = new TextField();
    private final ComboBox<String> tagFilter = new ComboBox<>();
    private final VBox entries = new VBox(12);
    private List<Session.Story> stories = List.of();
    private Session.Story editing;
    private byte[] pendingImage;
    private String pendingImageType;

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
        historicalDate.setId("story-historical-date");
        historicalDate.setPromptText("Historical date or time (e.g. c. 1200 BCE)");
        placeName.setId("story-place");
        placeName.setPromptText("Place name for map pin (optional)");
        latitude.setId("story-latitude");
        latitude.setPromptText("Latitude (−90 to 90)");
        longitude.setId("story-longitude");
        longitude.setPromptText("Longitude (−180 to 180)");
        imagePreview.setId("story-image-preview");
        imagePreview.setPreserveRatio(true);
        imagePreview.setFitWidth(460);
        imagePreview.setFitHeight(300);
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
        Button map = new Button("View story map");
        map.setOnAction(e -> showMap());
        VBox page = new VBox(16, heading, hint, new HBox(10, create, map), filters,
                listMessage, new Separator(), entries);
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
                note("Historical date: " + (story.historicalDate() == null ? "" : story.historicalDate())),
                note(story.placeName() == null || story.placeName().isBlank() ? "" : "Place: " + story.placeName()),
                note(excerpt(story.content())));
        card.getStyleClass().add("form-card");
        card.setMaxWidth(860);
        card.getChildren().get(1).getStyleClass().add("form-hint");
        card.getChildren().addAll(tagLabels(story.tags()), imageLink(story));
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
                note("Historical date: " + story.historicalDate()),
                note("Published " + formatTimestamp(story.createdAt())),
                note("Last updated " + formatTimestamp(story.updatedAt())));
        timestamps.getStyleClass().add("form-hint");
        VBox card = new VBox(14, heading, byline, timestamps, tagLabels(story.tags()),
                imageLink(story), new Separator(), note(story.content()));
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
        historicalDate.setText(story == null || story.historicalDate() == null ? "" : story.historicalDate());
        placeName.setText(story == null || story.placeName() == null ? "" : story.placeName());
        latitude.setText(story == null || story.latitude() == null ? "" : story.latitude().toString());
        longitude.setText(story == null || story.longitude() == null ? "" : story.longitude().toString());
        pendingImage = null;
        pendingImageType = null;
        imageName.setText(story != null && story.hasImage()
                ? "Current image will be kept unless replaced" : "No image selected");
        imagePreview.setImage(null);
        if (story != null && story.hasImage()) loadImage(story, imagePreview);
        save.setText(story == null ? "Share story" : "Save changes");
        formMessage.setText("");
        Button cancel = new Button("Cancel");
        cancel.setOnAction(e -> {
            if (returnToDetail && story != null) showDetail(story);
            else showList();
        });
        Button chooseImage = new Button("Choose historical picture…");
        chooseImage.setOnAction(e -> chooseImage());
        VBox card = new VBox(12, heading, hint, title, historicalDate, content, tags,
                new Label("Optional map pin"), placeName, new HBox(10, latitude, longitude),
                new HBox(10, chooseImage, imageName), imagePreview, formMessage,
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
        if (historicalDate.getText().isBlank()) {
            showError(formMessage, "Enter the historical date or time.");
            return;
        }
        Double lat;
        Double lon;
        try {
            lat = latitude.getText().isBlank() ? null : Double.valueOf(latitude.getText().strip());
            lon = longitude.getText().isBlank() ? null : Double.valueOf(longitude.getText().strip());
        } catch (NumberFormatException ex) {
            showError(formMessage, "Enter numeric latitude and longitude.");
            return;
        }
        if ((lat == null) != (lon == null)) {
            showError(formMessage, "Enter both coordinates for a map pin.");
            return;
        }
        if (lat != null && (lat < -90 || lat > 90 || lon < -180 || lon > 180)) {
            showError(formMessage, "Coordinates are outside the map range.");
            return;
        }
        if (lat != null && placeName.getText().isBlank()) {
            showError(formMessage, "Name the place for this map pin.");
            return;
        }
        save.setDisable(true);
        Session.Story current = editing;
        String titleValue = title.getText();
        String contentValue = content.getText();
        String tagsValue = tags.getText();
        String dateValue = historicalDate.getText();
        String placeValue = placeName.getText();
        byte[] imageBytes = pendingImage;
        String imageType = pendingImageType;
        Session.async(() -> {
                    Session.Story saved = current == null
                            ? Session.createStory(titleValue, contentValue, tagsValue, dateValue,
                            placeValue, lat, lon)
                            : Session.updateStory(current.id(), titleValue, contentValue, tagsValue, dateValue,
                            placeValue, lat, lon);
                    String imageError = null;
                    if (imageBytes != null) {
                        try {
                            Session.uploadStoryImage(saved.id(), imageType, imageBytes);
                            saved = new Session.Story(saved.id(), saved.authorUsername(), saved.authorDisplayName(),
                                    saved.title(), saved.content(), saved.tags(), saved.historicalDate(),
                                    saved.placeName(), saved.latitude(), saved.longitude(), true,
                                    saved.createdAt(), saved.updatedAt());
                        } catch (ApiClient.ApiException ex) {
                            imageError = ex.getMessage();
                        }
                    }
                    return new Submission(saved, imageError);
                },
                result -> {
                    save.setDisable(false);
                    Session.Story story = result.story();
                    stories = new ArrayList<>(stories);
                    stories.removeIf(existing -> existing.id().equals(story.id()));
                    stories.add(0, story);
                    updateTagChoices();
                    if (result.imageError() == null) listMessage.setText("");
                    showDetail(story);
                    if (result.imageError() != null) {
                        Alert warning = new Alert(Alert.AlertType.WARNING);
                        warning.setTitle("Picture upload failed");
                        warning.setHeaderText("Your story was saved, but its picture was not uploaded.");
                        warning.setContentText(result.imageError());
                        warning.showAndWait();
                    }
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

    private void chooseImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a historical picture");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Images (PNG, JPEG, GIF)", "*.png", "*.jpg", "*.jpeg", "*.gif"));
        Window window = getScene() == null ? null : getScene().getWindow();
        File file = chooser.showOpenDialog(window);
        if (file == null) return;
        if (file.length() > 5L * 1024 * 1024) {
            showError(formMessage, "Images must be 5 MB or smaller.");
            return;
        }
        String name = file.getName().toLowerCase(Locale.ROOT);
        pendingImageType = name.endsWith(".png") ? "image/png"
                : name.endsWith(".gif") ? "image/gif"
                : name.endsWith(".jpg") || name.endsWith(".jpeg") ? "image/jpeg" : null;
        if (pendingImageType == null) {
            showError(formMessage, "Choose a PNG, JPEG, or GIF image.");
            return;
        }
        try {
            pendingImage = Files.readAllBytes(file.toPath());
            imageName.setText(file.getName());
            imagePreview.setImage(new Image(new ByteArrayInputStream(pendingImage)));
            formMessage.setText("");
        } catch (IOException ex) {
            showError(formMessage, "Could not read the selected image: " + ex.getMessage());
        }
    }

    private void showMap() {
        List<Session.Story> mapped = stories.stream()
                .filter(story -> story.latitude() != null && story.longitude() != null).toList();
        Button back = new Button("← All stories");
        back.setOnAction(e -> showList());
        Label heading = new Label("Stories on the map");
        heading.getStyleClass().add("form-title");
        Label hint = note(mapped.isEmpty() ? "No stories have map locations yet."
                : "Select a marker to open that story. Authors can add a place and coordinates while editing.");
        hint.getStyleClass().add("form-hint");
        VBox page = new VBox(14, back, heading, hint,
                new StoryMapView(mapped, this::showDetail));
        page.setPadding(new Insets(10));
        page.setMaxWidth(1100);
        showScrollable(page);
    }

    private VBox imageLink(Session.Story story) {
        VBox box = new VBox(6);
        if (!story.hasImage()) return box;
        ImageView view = new ImageView();
        view.setPreserveRatio(true);
        view.setFitWidth(520);
        view.setFitHeight(340);
        loadImage(story, view);
        box.getChildren().add(view);
        return box;
    }

    private static void loadImage(Session.Story story, ImageView view) {
        Session.async(() -> Session.storyImage(story.id()),
                bytes -> view.setImage(new Image(new ByteArrayInputStream(bytes))),
                error -> view.setAccessibleText("Historical picture unavailable: " + error));
    }

    private record Submission(Session.Story story, String imageError) { }

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
