module org.example.historyinaglimpse {
    requires javafx.controls;
    requires javafx.media;
    requires java.desktop;
    requires java.net.http;
    requires com.fasterxml.jackson.databind;


    exports org.example.historyinaglimpse;
    // Jackson reads the account records in Session.
    opens org.example.historyinaglimpse to com.fasterxml.jackson.databind;
}