package org.example.historyinaglimpse;

import javafx.geometry.Side;
import javafx.scene.layout.Region;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Search the timeline without changing stable event indexes or scene settings. */
final class TimelineSearch {
    private static final Pattern YEAR = Pattern.compile("^(-?\\d{1,7})\\s*(BCE|BC|CE|AD)?$", Pattern.CASE_INSENSITIVE);
    static boolean included(HistoricalEvent event) {
        return !event.title().toLowerCase(Locale.ROOT).contains("human genome project");
    }
    static boolean matches(HistoricalEvent event, String input) {
        if (!included(event)) return false;
        String query = input.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return true;
        var year = YEAR.matcher(query);
        if (year.matches()) {
            int value = Integer.parseInt(year.group(1));
            String suffix = year.group(2);
            if (suffix != null) value = suffix.equalsIgnoreCase("BC") || suffix.equalsIgnoreCase("BCE") ? -Math.abs(value) : Math.abs(value);
            return event.year() == value;
        }
        String text = (event.title() + " " + event.date()).toLowerCase(Locale.ROOT);
        return Arrays.stream(query.split("\\s+")).allMatch(text::contains);
    }
    static HBox toolbar(ScrollPane scroll, Pane chart, Consumer<HistoricalEvent> open) {
        TextField search = new TextField();
        search.setId("timeline-search");
        search.setPromptText("Search era name or year");
        search.setPrefWidth(310); search.setMinWidth(120);
        search.setMaxWidth(310);
        Button submit = new Button("Search"); submit.setId("timeline-search-button");
        submit.setMinWidth(Region.USE_PREF_SIZE);
        MenuButton menu = new MenuButton("☰"); menu.setId("timeline-menu");
        menu.setAccessibleText("History menu");
        MenuItem newHistory = new MenuItem("Enter new history");
        newHistory.setDisable(true);
        Menu recent = new Menu("Last search");
        MenuItem account = new MenuItem("Your account"); account.setDisable(true);
        menu.getItems().addAll(newHistory, recent, account);
        ContextMenu results = new ContextMenu();
        results.setId("timeline-search-results");
        Rectangle highlight = new Rectangle();
        highlight.setManaged(false); highlight.setMouseTransparent(true);
        highlight.setFill(Color.TRANSPARENT); highlight.setStroke(Color.web("#a33d22")); highlight.setStrokeWidth(4);
        highlight.setVisible(false); chart.getChildren().add(highlight);
        var history = new RecentSearches();
        Runnable runSearch = () -> {
            results.hide(); results.getItems().clear();
            String query = search.getText().trim();
            highlight.setVisible(false);
            if (query.isEmpty()) {
                MenuItem hint = new MenuItem("Enter an era name or year."); hint.setDisable(true);
                results.getItems().add(hint);
            } else {
                history.remember(query);
                var found = EventCatalog.EVENTS.stream().filter(e -> matches(e, query))
                        .sorted(Comparator.comparingInt(HistoricalEvent::year)).toList();
                for (var event : found) {
                    MenuItem item = new MenuItem(event.title() + " • " + event.date());
                    item.setOnAction(e -> {
                        if (!locate(scroll, chart, highlight, event)) open.accept(event);
                    });
                    results.getItems().add(item);
                }
                if (found.isEmpty()) {
                    MenuItem empty = new MenuItem("No matching era or year."); empty.setDisable(true);
                    results.getItems().add(empty);
                }
            }
            results.show(search, Side.BOTTOM, 0, 0);
        };
        submit.setOnAction(e -> runSearch.run());
        search.setOnAction(e -> runSearch.run());
        search.textProperty().addListener((o,a,b) -> results.hide());
        menu.setOnShowing(e -> {
            results.hide(); recent.getItems().clear();
            for (String query : history.entries()) {
                MenuItem item = new MenuItem(query);
                item.setOnAction(action -> { search.setText(query); runSearch.run(); });
                recent.getItems().add(item);
            }
            if (recent.getItems().isEmpty()) {
                MenuItem empty = new MenuItem("No searches yet"); empty.setDisable(true); recent.getItems().add(empty);
            }
        });
        // Do not leave detached search popups visible after opening another scene.
        search.sceneProperty().addListener((o,a,b) -> { if (b == null) results.hide(); });
        Region spacer = new Region();
        HBox row = new HBox(8, menu, spacer, search, submit);
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        row.setMaxWidth(Double.MAX_VALUE); row.setMinWidth(0);
        row.setId("timeline-search-bar");
        row.getStyleClass().add("tool-bar");
        row.setPadding(new javafx.geometry.Insets(8, 12, 8, 12));
        menu.setMinWidth(Region.USE_PREF_SIZE);
        // BorderPane sizes this row directly. There is no toolbar overflow menu.
        return row;
    }
    private static boolean locate(ScrollPane scroll, Pane chart, Rectangle highlight, HistoricalEvent selected) {
        int index = EventCatalog.EVENTS.indexOf(selected);
        try (var stream = TimelineSearch.class.getResourceAsStream("/chart-layout.tsv")) {
            if (stream == null) return false;
            var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] f = line.split("\t", -1);
                if (f.length >= 6 && f[0].equals("HOTSPOT") && Integer.parseInt(f[1]) == index) {
                    double x=Double.parseDouble(f[2]), y=Double.parseDouble(f[3]), w=Double.parseDouble(f[4]), h=Double.parseDouble(f[5]);
                    highlight.setX(x); highlight.setY(y); highlight.setWidth(w); highlight.setHeight(h);
                    highlight.setVisible(true); highlight.toFront();
                    double scale = chart.getTransforms().isEmpty() ? 1 : chart.getTransforms().get(0).getMxx();
                    double viewport = scroll.getViewportBounds().getWidth();
                    double overflow = scroll.getContent().getBoundsInLocal().getWidth() - viewport;
                    scroll.setHvalue(overflow <= 0 ? 0 : Math.max(0, Math.min(1, ((x+w/2)*scale-viewport/2)/overflow)));
                    return true;
                }
            }
        } catch (IOException | NumberFormatException ex) { highlight.setVisible(false); }
        return false;
    }
    /** Search history exists only in memory for this running application. */
    static final class RecentSearches {
        private final List<String> queries = new ArrayList<>();
        List<String> entries() { return List.copyOf(queries); }
        void remember(String input) {
            String query = input.replace('\n', ' ').replace('\r', ' ').trim();
            if (query.isEmpty()) return;
            queries.removeIf(q -> q.equalsIgnoreCase(query)); queries.add(0, query);
            while (queries.size() > 20) queries.remove(queries.size()-1);
        }
    }
    private TimelineSearch() {}
}
