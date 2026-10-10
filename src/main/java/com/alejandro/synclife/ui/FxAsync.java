package com.alejandro.synclife.ui;

import javafx.application.Platform;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ejecuta el trabajo de base de datos fuera del hilo de JavaFX y devuelve el resultado a él.
 *
 * <p>Todo pasa por <b>un único hilo</b> ("synclife-db"): SQLite admite un solo escritor y el pool de
 * Hibernate tiene una única conexión, así que dos transacciones simultáneas fallarían.
 */
public final class FxAsync {

    private static final Logger LOG = Logger.getLogger(FxAsync.class.getName());

    private static final ExecutorService DB_THREAD = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "synclife-db");
        thread.setDaemon(true);
        return thread;
    });

    private static final AtomicInteger PENDING = new AtomicInteger();

    private FxAsync() {
    }

    /** Ejecuta {@code work} en el hilo de base de datos; los errores se muestran con {@link Dialogs#error}. */
    public static <T> void run(Callable<T> work, Consumer<T> onSuccess) {
        run(work, onSuccess, Dialogs::error);
    }

    public static <T> void run(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        PENDING.incrementAndGet();
        DB_THREAD.execute(() -> {
            try {
                T result = work.call();
                Platform.runLater(() -> finish(() -> {
                    if (onSuccess != null) {
                        onSuccess.accept(result);
                    }
                }));
            } catch (Throwable error) {
                LOG.log(Level.FINE, "Fallo en tarea de base de datos", error);
                Platform.runLater(() -> finish(() -> onError.accept(error)));
            }
        });
    }

    /** Variante para trabajo sin resultado. */
    public static void runVoid(ThrowingRunnable work, Runnable onSuccess) {
        run(() -> {
            work.run();
            return null;
        }, ignored -> {
            if (onSuccess != null) {
                onSuccess.run();
            }
        });
    }

    /** ¿No queda trabajo pendiente ni callbacks por ejecutar? (usado por la prueba de humo). */
    public static boolean isIdle() {
        return PENDING.get() == 0;
    }

    public static void shutdown() {
        DB_THREAD.shutdown();
        try {
            if (!DB_THREAD.awaitTermination(5, TimeUnit.SECONDS)) {
                DB_THREAD.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void finish(Runnable callback) {
        try {
            callback.run();
        } finally {
            PENDING.decrementAndGet();
        }
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}
