package com.alejandro.synclife.ui;

import com.alejandro.synclife.AppContext;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.net.URL;

/**
 * Carga FXML de {@code /com/alejandro/synclife/view/} e inyecta el {@link AppContext} en el
 * constructor de los controladores (si tienen uno que lo reciba).
 */
public final class ViewLoader {

    private static final String VIEW_PATH = "/com/alejandro/synclife/view/";

    public record Loaded<C>(Parent root, C controller) {
    }

    private ViewLoader() {
    }

    public static <C> Loaded<C> load(AppContext context, String fxml) {
        URL url = ViewLoader.class.getResource(VIEW_PATH + fxml);
        if (url == null) {
            throw new IllegalArgumentException("No existe la vista " + fxml);
        }
        FXMLLoader loader = new FXMLLoader(url);
        loader.setControllerFactory(type -> instantiate(type, context));
        try {
            Parent root = loader.load();
            return new Loaded<>(root, loader.getController());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo cargar " + fxml, e);
        }
    }

    private static Object instantiate(Class<?> type, AppContext context) {
        try {
            try {
                Constructor<?> withContext = type.getConstructor(AppContext.class);
                return withContext.newInstance(context);
            } catch (NoSuchMethodException e) {
                return type.getConstructor().newInstance();
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No se pudo crear el controlador " + type.getName(), e);
        }
    }
}
