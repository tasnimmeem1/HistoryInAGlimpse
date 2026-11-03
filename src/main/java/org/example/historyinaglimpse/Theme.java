package org.example.historyinaglimpse;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import java.util.prefs.Preferences;

/** Light and dark themes. The choice is remembered between runs of the app. */
public final class Theme {
    private static final String DARK_SHEET = Theme.class.getResource("/theme-dark.css").toExternalForm();
    private static final Preferences PREFS = Preferences.userNodeForPackage(Theme.class);
    private static boolean dark = PREFS.getBoolean("dark", false);

    private Theme() { }

    public static boolean isDark() { return dark; }

    /** Adds or removes the dark stylesheet on a scene to match the current choice. */
    public static void apply(Scene scene) {
        if (scene == null) return;
        scene.getStylesheets().remove(DARK_SHEET);
        if (dark) scene.getStylesheets().add(DARK_SHEET);
    }

    public static void set(boolean useDark, Scene scene) {
        dark = useDark;
        PREFS.putBoolean("dark", useDark);
        apply(scene);
    }

    /** A button that switches themes and relabels itself. */
    public static Button toggleButton() {
        Button toggle = new Button();
        toggle.setId("theme-toggle");
        Runnable label = () -> {
            toggle.setText(dark ? "☀ Light mode" : "☾ Dark mode");
            toggle.setAccessibleText(dark ? "Switch to light mode" : "Switch to dark mode");
        };
        label.run();
        toggle.setOnAction(e -> {
            set(!dark, toggle.getScene());
            label.run();
        });
        return toggle;
    }
}
