module org.example.historyinaglimpse {
    requires javafx.controls;
    requires javafx.media;
    requires java.desktop;
    requires java.net.http;
    requires java.prefs;
    // Receives the browser's return from Google sign-in on a local address.
    requires jdk.httpserver;
    requires com.fasterxml.jackson.databind;


    exports org.example.historyinaglimpse;
    // Jackson reads the account records in Session.
    opens org.example.historyinaglimpse to com.fasterxml.jackson.databind;
}