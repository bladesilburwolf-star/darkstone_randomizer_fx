package com.serifsystemworks.darkstone;

import com.serifsystemworks.darkstone.ui.PcMainView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** Entry point — Darkstone PC randomizer. */
public final class DarkstonePcApp extends Application {

    @Override
    public void start(Stage stage) {
        PcMainView view = new PcMainView(stage);
        Scene scene = new Scene(view.build(), 720, 780);
        var css = PcMainView.class.getResource("theme.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        stage.setTitle("Darkstone PC Randomizer " + PcMainView.VERSION);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        // CLI: --cli --game <path> --seed <s> [--out <path>]
        if (args.length > 0 && "--cli".equals(args[0])) {
            runCli(args);
            return;
        }
        launch(args);
    }

    private static void runCli(String[] args) {
        try {
            var opt = new com.serifsystemworks.darkstone.pc.PcOptions();
            for (int i = 1; i < args.length; i++) {
                switch (args[i]) {
                    case "--game" -> opt.gameRoot = java.nio.file.Path.of(args[++i]);
                    case "--out" -> opt.outputRoot = java.nio.file.Path.of(args[++i]);
                    case "--seed" -> opt.seedText = args[++i];
                    case "--preset" -> opt.preset = args[++i];
                    default -> System.err.println("Unknown arg: " + args[i]);
                }
            }
            if (opt.seedText == null || opt.seedText.isBlank()) {
                opt.seedText = com.serifsystemworks.darkstone.pc.PcOptions.randomSeedString();
            }
            if ("Chaotic".equalsIgnoreCase(opt.preset)) {
                opt.rangeRollMonsters = true;
                opt.rangeRollItems = true;
            } else if ("Advanced".equalsIgnoreCase(opt.preset)) {
                opt.rangeRollMonsters = true;
            }
            new com.serifsystemworks.darkstone.pc.PcRandomizerEngine(System.out::println).run(opt);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}
