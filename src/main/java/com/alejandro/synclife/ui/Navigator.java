package com.alejandro.synclife.ui;

import com.alejandro.synclife.AppContext;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.layout.StackPane;

import java.util.EnumMap;
import java.util.Map;

/** Cambia la pantalla mostrada en el centro de la ventana. Las vistas se crean una vez y se reutilizan. */
public class Navigator {

    private final AppContext context;
    private final StackPane host;
    private final Map<View, ViewLoader.Loaded<?>> cache = new EnumMap<>(View.class);
    private final ReadOnlyObjectWrapper<View> current = new ReadOnlyObjectWrapper<>();

    public Navigator(AppContext context, StackPane host) {
        this.context = context;
        this.host = host;
    }

    public void navigate(View view) {
        ViewLoader.Loaded<?> loaded = cache.computeIfAbsent(view, v -> ViewLoader.load(context, v.fxml()));
        host.getChildren().setAll(loaded.root());
        current.set(view);
        refreshCurrent();
    }

    /** Descarta todas las pantallas creadas (p. ej. tras borrar todos los datos) y vuelve a crear la actual. */
    public void reset() {
        cache.clear();
        View view = current.get();
        if (view != null) {
            navigate(view);
        }
    }

    public void refreshCurrent() {
        View view = current.get();
        if (view != null && cache.get(view).controller() instanceof ViewController controller) {
            controller.onShow();
        }
    }

    /** Controlador de una pantalla ya creada (o null si aún no se ha mostrado). */
    @SuppressWarnings("unchecked")
    public <C> C controller(View view) {
        ViewLoader.Loaded<?> loaded = cache.get(view);
        return loaded == null ? null : (C) loaded.controller();
    }

    public ReadOnlyObjectProperty<View> currentProperty() {
        return current.getReadOnlyProperty();
    }

    public View getCurrent() {
        return current.get();
    }
}
