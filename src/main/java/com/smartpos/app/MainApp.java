package com.smartpos.app;

import com.smartpos.util.LicenseManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

public class MainApp extends Application {

    private AppContext context;

    @Override
    public void start(Stage stage) {
        // 1. Instantly set window title
        stage.setTitle("Offline First POS");

        // 2. Initialize UI contexts directly
        context = new AppContext();
        SceneManager sceneManager = new SceneManager(stage, context);

        // 3. Setup standard cleanup on exit (Incorporate local AppContext shutdown)
        stage.setOnCloseRequest(event -> {
            System.out.println("[Shutdown]: Cleaning up background threads...");
            if (context != null) {
                context.shutdown(); // Gracefully stops the pos-async-worker pool
            }
            Platform.exit();
            System.exit(0);
        });

        // 4. Verify flat-file hardware license and route screen
        if (LicenseManager.isLicensed()) {
            sceneManager.show(ScreenType.LOGIN);
        } else {
            sceneManager.show(ScreenType.REGISTRATION);
        }

        // 5. Paint the window onto the screen
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}