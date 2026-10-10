package com.alejandro.synclife.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** Copia de seguridad consistente de la base de datos con {@code VACUUM INTO}. */
public final class DatabaseBackup {

    private DatabaseBackup() {
    }

    /**
     * Usa una conexión propia en modo autocommit: VACUUM no puede ejecutarse dentro de una transacción.
     * Llamar desde el hilo de base de datos para no competir con Hibernate.
     */
    public static void backupTo(String jdbcUrl, Path target) throws SQLException {
        try {
            Files.deleteIfExists(target); // VACUUM INTO falla si el destino ya existe
        } catch (java.io.IOException e) {
            throw new SQLException("No se pudo sobrescribir " + target, e);
        }
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement("VACUUM INTO ?")) {
            statement.setString(1, target.toAbsolutePath().toString());
            statement.execute();
        }
    }
}
