module com.smartpos {
    requires java.sql;
    requires java.net.http;
    requires org.xerial.sqlitejdbc;

    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;

    opens com.smartpos.app to javafx.fxml, javafx.graphics;
    opens com.smartpos.ui.screen to javafx.fxml, javafx.graphics;
    opens com.smartpos.model to javafx.base;
}