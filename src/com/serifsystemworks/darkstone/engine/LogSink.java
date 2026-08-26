package com.serifsystemworks.darkstone.engine;

/**
 * UI-agnostic logging so the engine can run from JavaFX, CLI, or tests.
 */
public interface LogSink {
    void log(String message);

    default void status(String message) {
        log(message);
    }

    default void analysis(String text) {
        log(text);
    }

    default void info(String message) {
        log(message);
    }

    default void warning(String message) {
        log("[!] " + message);
    }

    LogSink NULL = message -> { };
}
