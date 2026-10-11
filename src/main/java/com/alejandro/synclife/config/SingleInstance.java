package com.alejandro.synclife.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Garantiza que solo haya una SyncLife abierta por carpeta de datos (con la ventana en la bandeja sería fácil
 * abrir una segunda sin darse cuenta, y las dos escribirían en la misma base de datos).
 *
 * <p>La primera instancia bloquea {@code synclife.lock} y escucha en un puerto local (solo 127.0.0.1) que
 * apunta en {@code synclife.port}. Una segunda instancia no consigue el bloqueo, le pide a la primera que
 * muestre su ventana y termina.
 */
public final class SingleInstance implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(SingleInstance.class.getName());
    private static final String SHOW = "SHOW";
    private static final String LOCK_FILE = "synclife.lock";
    private static final String PORT_FILE = "synclife.port";

    private final FileChannel channel;
    private final FileLock lock;
    private final ServerSocket server;
    private final Path portFile;

    private SingleInstance(FileChannel channel, FileLock lock, ServerSocket server, Path portFile) {
        this.channel = channel;
        this.lock = lock;
        this.server = server;
        this.portFile = portFile;
    }

    /**
     * Intenta ser la instancia principal. Si ya hay otra abierta, le pide que se muestre y devuelve vacío.
     * {@code onShowRequest} se llama (desde otro hilo) cuando una segunda instancia pide la ventana.
     */
    public static Optional<SingleInstance> acquire(Path dataDir, Runnable onShowRequest) {
        try {
            Files.createDirectories(dataDir);
            FileChannel channel = FileChannel.open(dataDir.resolve(LOCK_FILE),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock;
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException e) {
                lock = null; // la tiene esta misma JVM
            }
            if (lock == null) {
                channel.close();
                askToShow(dataDir.resolve(PORT_FILE));
                return Optional.empty();
            }
            ServerSocket server = new ServerSocket();
            server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            Path portFile = dataDir.resolve(PORT_FILE);
            Files.writeString(portFile, Integer.toString(server.getLocalPort()));
            SingleInstance instance = new SingleInstance(channel, lock, server, portFile);
            Thread listener = new Thread(() -> instance.listen(onShowRequest), "synclife-single-instance");
            listener.setDaemon(true);
            listener.start();
            return Optional.of(instance);
        } catch (IOException e) {
            // Sin bloqueo no se impide arrancar (p. ej. carpeta en un disco que no admite bloqueos)
            LOG.log(Level.WARNING, "No se pudo comprobar si SyncLife ya está abierta", e);
            return Optional.of(new SingleInstance(null, null, null, null));
        }
    }

    private void listen(Runnable onShowRequest) {
        while (!server.isClosed()) {
            try (Socket client = server.accept()) {
                client.setSoTimeout(2000);
                BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                char[] buffer = new char[16];
                int read = in.read(buffer);
                if (read > 0 && new String(buffer, 0, read).trim().equals(SHOW)) {
                    onShowRequest.run();
                }
            } catch (IOException | RuntimeException e) {
                if (!server.isClosed()) {
                    LOG.log(Level.FINE, "Petición de otra instancia ignorada", e);
                }
            }
        }
    }

    private static void askToShow(Path portFile) {
        try {
            int port = Integer.parseInt(Files.readString(portFile).trim());
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 2000);
                OutputStream out = socket.getOutputStream();
                out.write((SHOW + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        } catch (IOException | NumberFormatException e) {
            LOG.log(Level.WARNING, "SyncLife ya está abierta, pero no responde", e);
        }
    }

    @Override
    public void close() {
        try {
            if (server != null) {
                server.close();
            }
            if (lock != null) {
                lock.release();
            }
            if (channel != null) {
                channel.close();
            }
            if (portFile != null) {
                Files.deleteIfExists(portFile);
            }
        } catch (IOException e) {
            LOG.log(Level.FINE, "Error al liberar el bloqueo de instancia única", e);
        }
    }
}
