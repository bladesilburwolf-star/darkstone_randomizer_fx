package com.serifsystemworks.darkstone;

import com.serifsystemworks.darkstone.ui.MtfExplorerView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Standalone launcher for the MTF Explorer/Editor, kept separate from
 * {@link DarkstonePcApp} for now so it can be tested independently against
 * a real DATA.MTF before being merged into the main randomizer shell as
 * another tab.
 */
public final class MtfExplorerApp extends Application {

    @Override
    public void start(Stage stage) {
        MtfExplorerView view = new MtfExplorerView(stage);
        Scene scene = new Scene(view.build(), 1000, 640);
        stage.setTitle("Darkstone MTF Explorer (test build)");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
