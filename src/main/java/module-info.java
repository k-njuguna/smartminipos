module com.smartpos {
    requires java.sql;
    requires java.net.http;

    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires org.xerial.sqlitejdbc;

    opens com.smartpos.app to javafx.fxml, javafx.graphics;
    opens com.smartpos.ui.screen to javafx.fxml, javafx.graphics;
    opens com.smartpos.model to javafx.base;
}