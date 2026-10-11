package com.alejandro.synclife.ui.tray;

import com.alejandro.synclife.AppContext;
import com.alejandro.synclife.ui.AppUi;
import com.alejandro.synclife.ui.SmokeRunner;
import javafx.application.Platform;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Icono de SyncLife en la bandeja del sistema: al cerrar la ventana la aplicación sigue en marcha
 * (recordatorios y Pomodoro incluidos) y se sale desde el menú del icono. También muestra las
 * notificaciones del sistema.
 *
 * <p>Si el escritorio no tiene bandeja (muchos Linux) no se instala nada y la aplicación se comporta
 * como siempre: cerrar la ventana la cierra. Hilos: lo de AWT va en su hilo de eventos
 * ({@link EventQueue#invokeLater}) y lo de JavaFX con {@link Platform#runLater}; nunca se espera al otro.
 */
public final class TrayIntegration {

    private static final Logger LOG = Logger.getLogger(TrayIntegration.class.getName());
    private static final int[] ICON_SIZES = {16, 32, 64};

    private final AppContext context;
    private final Stage stage;
    /** Solo se toca desde el hilo de AWT. */
    private TrayIcon icon;
    private volatile boolean active;
    private boolean hintShown;

    public TrayIntegration(AppContext context, Stage stage) {
        this.context = context;
        this.stage = stage;
    }

    public static boolean isSupported() {
        if (SmokeRunner.enabled()) {
            return false;
        }
        try {
            return SystemTray.isSupported();
        } catch (Throwable e) {
            LOG.log(Level.FINE, "Sin bandeja del sistema", e);
            return false;
        }
    }

    /** Instala el icono si el sistema lo admite y el usuario lo quiere (Ajustes). Llamar desde JavaFX. */
    public void install() {
        if (active || !context.settings().getNotifications().minimizeToTray()) {
            return;
        }
        if (!isSupported()) {
            LOG.info("El escritorio no tiene bandeja del sistema: al cerrar la ventana se sale de SyncLife");
            return;
        }
        EventQueue.invokeLater(this::addIcon);
    }

    /** Quita el icono: cerrar la ventana vuelve a cerrar la aplicación. Llamar desde JavaFX. */
    public void uninstall() {
        if (!active) {
            return;
        }
        active = false;
        Platform.setImplicitExit(true);
        stage.setOnCloseRequest(null);
        EventQueue.invokeLater(this::removeIcon);
    }

    public boolean isActive() {
        return active;
    }

    /** ¿La ventana no está a la vista (oculta en la bandeja, minimizada o en segundo plano)? */
    public boolean isWindowAway() {
        return !stage.isShowing() || stage.isIconified() || !stage.isFocused();
    }

    public void showWindow() {
        if (!stage.isShowing()) {
            stage.show();
        }
        stage.setIconified(false);
        stage.toFront();
        stage.requestFocus();
    }

    /** Notificación del sistema (solo si el icono está instalado y el usuario las quiere). */
    public void notifySystem(String title, String message) {
        if (!active || !context.settings().getNotifications().systemNotifications()) {
            return;
        }
        EventQueue.invokeLater(() -> {
            if (icon != null) {
                icon.displayMessage(title, message, TrayIcon.MessageType.INFO);
            }
        });
    }

    /** Salir de verdad: retira el icono y cierra JavaFX (que guarda el Pomodoro a medias y cierra la base de datos). */
    public void exit() {
        active = false;
        EventQueue.invokeLater(this::removeIcon);
        Platform.exit();
    }

    // ---------------------------------------------------------------- internos

    /** Hilo de AWT. */
    private void addIcon() {
        try {
            SystemTray tray = SystemTray.getSystemTray();
            PopupMenu menu = new PopupMenu();
            MenuItem open = new MenuItem("Abrir SyncLife");
            open.addActionListener(e -> Platform.runLater(this::showWindow));
            MenuItem pomodoro = new MenuItem("Pausar o reanudar el pomodoro");
            pomodoro.addActionListener(e -> Platform.runLater(() -> {
                if (AppUi.isInstalled()) {
                    AppUi.pomodoro().togglePause();
                }
            }));
            MenuItem quit = new MenuItem("Salir");
            quit.addActionListener(e -> Platform.runLater(this::exit));
            menu.add(open);
            menu.add(pomodoro);
            menu.addSeparator();
            menu.add(quit);

            TrayIcon trayIcon = new TrayIcon(loadImage(tray.getTrayIconSize()), "SyncLife", menu);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(e -> Platform.runLater(this::showWindow)); // doble clic en Windows
            trayIcon.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 1) {
                        Platform.runLater(TrayIntegration.this::showWindow); // un clic, como en otras apps
                    }
                }
            });
            tray.add(trayIcon);
            icon = trayIcon;
            Platform.runLater(this::activate);
        } catch (Throwable e) {
            LOG.log(Level.WARNING, "No se pudo añadir el icono a la bandeja del sistema", e);
        }
    }

    /** Hilo de JavaFX, una vez que el icono existe de verdad. */
    private void activate() {
        active = true;
        Platform.setImplicitExit(false);
        stage.setOnCloseRequest(e -> {
            e.consume();
            hideToTray();
        });
    }

    private void hideToTray() {
        stage.hide();
        if (!hintShown) {
            hintShown = true;
            notifySystem("SyncLife sigue en la bandeja",
                    "Los recordatorios y el pomodoro siguen funcionando. Para cerrar del todo, usa «Salir» en este icono.");
        }
    }

    /** Hilo de AWT. */
    private void removeIcon() {
        if (icon != null) {
            try {
                SystemTray.getSystemTray().remove(icon);
            } catch (Throwable e) {
                LOG.log(Level.FINE, "No se pudo quitar el icono de la bandeja", e);
            }
            icon = null;
        }
    }

    /** El PNG más pequeño que cubra el tamaño que pide la bandeja. */
    private static Image loadImage(Dimension wanted) throws IOException {
        int target = Math.max(wanted.width, wanted.height);
        int chosen = ICON_SIZES[ICON_SIZES.length - 1];
        for (int size : ICON_SIZES) {
            if (size >= target) {
                chosen = size;
                break;
            }
        }
        try (InputStream in = TrayIntegration.class.getResourceAsStream(
                "/com/alejandro/synclife/images/icon-" + chosen + ".png")) {
            if (in == null) {
                throw new IOException("Falta el icono de " + chosen + " px");
            }
            return ImageIO.read(in);
        }
    }
}
