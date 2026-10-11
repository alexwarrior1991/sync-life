package com.alejandro.synclife.ui;

import com.alejandro.synclife.ui.notify.NotificationCenter;
import com.alejandro.synclife.ui.pomodoro.PomodoroTimer;
import com.alejandro.synclife.ui.tray.TrayIntegration;

/**
 * Piezas de la interfaz que existen una sola vez por ventana y que usan varias pantallas
 * (el Pomodoro, los avisos, la navegación). Las registra {@code MainController} al crearse.
 * {@code AppContext} no sabe nada de JavaFX; esto es su equivalente en la capa de interfaz.
 */
public final class AppUi {

    private static PomodoroTimer pomodoro;
    private static NotificationCenter notifications;
    private static Navigator navigator;
    private static TrayIntegration tray;

    private AppUi() {
    }

    public static void install(Navigator nav, NotificationCenter center, PomodoroTimer timer) {
        navigator = nav;
        notifications = center;
        pomodoro = timer;
    }

    /** La bandeja se conoce cuando ya existe la ventana (puede estar inactiva si el escritorio no la admite). */
    public static void installTray(TrayIntegration trayIntegration) {
        tray = trayIntegration;
    }

    public static boolean isInstalled() {
        return navigator != null;
    }

    public static PomodoroTimer pomodoro() {
        return required(pomodoro);
    }

    public static NotificationCenter notifications() {
        return required(notifications);
    }

    public static Navigator navigator() {
        return required(navigator);
    }

    public static TrayIntegration tray() {
        return required(tray);
    }

    /** Muestra la ventana principal (p. ej. cuando se intenta abrir SyncLife otra vez). */
    public static void showMainWindow() {
        if (tray != null) {
            tray.showWindow();
        }
    }

    private static <T> T required(T value) {
        if (value == null) {
            throw new IllegalStateException("La ventana principal aún no se ha creado");
        }
        return value;
    }
}
