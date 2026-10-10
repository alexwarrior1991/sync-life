package com.alejandro.synclife.persistence;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;

/**
 * Aplica las migraciones SQL de {@code src/main/resources/db/migration}.
 * Hibernate no gestiona el esquema ({@code hbm2ddl.auto=none}): con SQLite no crearía
 * claves foráneas ni índices únicos compuestos.
 */
public final class DatabaseMigrator {

    private DatabaseMigrator() {
    }

    public static MigrateResult migrate(String jdbcUrl) {
        return Flyway.configure()
                .dataSource(jdbcUrl, null, null)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }
}
