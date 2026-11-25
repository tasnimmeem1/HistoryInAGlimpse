package org.example.historyinaglimpse;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import java.awt.Desktop;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Searches the public historical catalog and manages account-saved selections. */
public final class SavedHistoryView extends StackPane {
    private static final String ALL = "All records";
    private static final String SAVED = "Saved only";
    private static final String UNSAVED = "Not saved";
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a", Locale.getDefault());

    private final TextField search = new TextField();
    private final ComboBox<String> filter = new ComboBox<>();
    private final Label status = new Label();
    private final VBox entries = new VBox(12);
    private final ScrollPane listPage;
    private List<Session.HistoricalRecord> records = List.of();
    private final Set<String> savedIds = new HashSet<>();
    private final Map<String, String> savedAt = new HashMap<>();

    public SavedHistoryView() {
        getStyleClass().add("form-page");
        search.setId("history-search");
        search.setPromptText("Search title, date, or description");
        search.textProperty().addListener((o, oldValue, newValue) -> render());
        filter.setId("history-saved-filter");
        filter.getItems().setAll(ALL, SAVED, UNSAVED);
        filter.setValue(ALL);
        filter.setMaxWidth(Double.MAX_VALUE);
        filter.setOnAction(e -> render());
        status.setWrapText(true);
        status.getStyleClass().add("form-hint");
        status.managedProperty().bind(status.textProperty().isNotEmpty());
        status.visibleProperty().bind(status.managedProperty());

        Label heading = new Label("Historical records");
        heading.getStyleClass().add("form-title");
        Label hint = new Label("Search the public timeline catalog, open a record, and save it to your account.");
        hint.getStyleClass().add("form-hint");
        hint.setWrapText(true);
        HBox controls = new HBox(10, search, filter);
        HBox.setHgrow(search, Priority.ALWAYS);
        HBox.setHgrow(filter, Priority.ALWAYS);
        VBox page = new VBox(16, heading, hint, controls, status, new Separator(), entries);
        page.setPadding(new Insets(10));
        page.setMaxWidth(920);
        listPage = new ScrollPane(page);
        listPage.setFitToWidth(true);
        listPage.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        getChildren().setAll(listPage);
        load();
    }

    private void load() {
        status.setText("Loading historical records and saved selections…");
        Session.async(() -> new Data(Session.listHistoricalRecords(), Session.listSavedSelections()),
                data -> {
                    records = data.records();
                    savedIds.clear();
                    savedAt.clear();
                    data.selections().forEach(selection -> {
                        savedIds.add(selection.recordId());
                        savedAt.put(selection.recordId(), selection.savedAt());
                    });
                    status.setText("");
                    render();
                },
                error -> showError(status, error));
    }

    private void render() {
        if (entries == null) return;
        entries.getChildren().clear();
        String query = search.getText() == null ? "" : search.getText().strip().toLowerCase(Locale.ROOT);
        String selectedFilter = filter.getValue();
        List<Session.HistoricalRecord> matches = records.stream()
                .filter(record -> query.isEmpty() || searchable(record).contains(query))
                .filter(record -> !SAVED.equals(selectedFilter) || savedIds.contains(record.id()))
                .filter(record -> !UNSAVED.equals(selectedFilter) || !savedIds.contains(record.id()))
                .toList();
        if (matches.isEmpty()) {
            entries.getChildren().add(note(records.isEmpty()
                    ? "The historical-record catalog is empty."
                    : "No records match these search and filter settings."));
            return;
        }
        matches.forEach(record -> entries.getChildren().add(recordCard(record)));
    }

    private VBox recordCard(Session.HistoricalRecord record) {
        Label title = new Label(record.title());
        title.getStyleClass().add("form-section");
        title.setWrapText(true);
        Label date = note(record.date());
        date.getStyleClass().add("form-hint");
        Label description = note(excerpt(record.description()));
        Button detail = new Button("Details");
        detail.setOnAction(e -> showDetail(record));
        Button toggle = new Button(savedIds.contains(record.id()) ? "Remove saved" : "Save record");
        toggle.setOnAction(e -> toggleSaved(record));
        VBox card = new VBox(8, title, date, description, new HBox(8, detail, toggle));
        card.getStyleClass().add("form-card");
        card.setMaxWidth(860);
        return card;
    }

    private void showDetail(Session.HistoricalRecord record) {
        Label title = new Label(record.title());
        title.getStyleClass().add("form-title");
        title.setWrapText(true);
        Label date = note(record.date() + (savedAt.containsKey(record.id())
                ? " · Saved " + formatTimestamp(savedAt.get(record.id())) : ""));
        date.getStyleClass().add("form-hint");
        VBox card = new VBox(14, title, date, new Separator(), note(record.description()));
        card.getStyleClass().add("form-card");
        card.setMaxWidth(860);
        if (record.source() != null && !record.source().isBlank()) {
            Hyperlink source = new Hyperlink("Open historical source");
            source.setOnAction(e -> openSource(record.source()));
            card.getChildren().add(source);
        }
        Button back = new Button("← All records");
        back.setOnAction(e -> showList());
        Button toggle = new Button(savedIds.contains(record.id()) ? "Remove saved" : "Save record");
        toggle.setOnAction(e -> toggleSaved(record));
        VBox page = new VBox(16, new HBox(8, back, toggle), status, card);
        page.setPadding(new Insets(10));
        page.setMaxWidth(920);
        showPage(page);
    }

    private void showList() {
        getChildren().setAll(listPage);
        render();
    }

    private void toggleSaved(Session.HistoricalRecord record) {
        boolean remove = savedIds.contains(record.id());
        status.setText(remove ? "Removing saved record…" : "Saving record…");
        Session.async(() -> {
                    if (remove) {
                        Session.removeSelection(record.id());
                        return null;
                    }
                    return Session.saveSelection(record.id());
                },
                selection -> {
                    if (remove) {
                        savedIds.remove(record.id());
                        savedAt.remove(record.id());
                        showNotice(status, "Removed from saved history.");
                    } else {
                        savedIds.add(record.id());
                        savedAt.put(record.id(), selection.savedAt());
                        showNotice(status, "Saved to your history.");
                    }
                    render();
                    if (getChildren().get(0) != listPage) {
                        // Keep the detail view current after a save/remove action.
                        showDetail(record);
                    }
                },
                error -> showError(status, error));
    }

    private void openSource(String source) {
        try {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create(source));
            else showError(status, "This device cannot open web links.");
        } catch (Exception ex) {
            showError(status, "Could not open the historical source: " + ex.getMessage());
        }
    }

    private void showPage(javafx.scene.Node page) {
        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        getChildren().setAll(scroll);
    }

    private static String searchable(Session.HistoricalRecord record) {
        return (record.title() + " " + record.date() + " " + record.description())
                .toLowerCase(Locale.ROOT);
    }

    private static String excerpt(String value) {
        String text = value == null ? "" : value.strip();
        return text.length() <= 260 ? text : text.substring(0, 257).stripTrailing() + "…";
    }

    private static String formatTimestamp(String value) {
        if (value == null || value.isBlank()) return "Unknown date";
        try {
            return DATE.withZone(ZoneId.systemDefault()).format(Instant.parse(value));
        } catch (RuntimeException ignored) {
            return value;
        }
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

    private record Data(List<Session.HistoricalRecord> records, List<Session.SavedSelection> selections) { }
}
