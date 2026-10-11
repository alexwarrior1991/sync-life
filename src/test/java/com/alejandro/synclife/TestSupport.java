package com.alejandro.synclife;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Utilidades comunes de los tests de integración (SQLite real en un directorio temporal). */
public final class TestSupport {

    public static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    /** Lunes 12 de octubre de 2026. */
    public static final LocalDate TODAY = LocalDate.of(2026, 10, 12);

    private TestSupport() {
    }

    public static Clock clockAt(LocalDate date) {
        return Clock.fixed(date.atTime(9, 0).atZone(ZONE).toInstant(), ZONE);
    }

    public static AppContext context(Path dir) {
        return AppContext.create(dir, clockAt(TODAY));
    }

    public static AppContext context(Path dir, LocalDate today) {
        return AppContext.create(dir, clockAt(today));
    }

    /** Contexto con el reloj parado en un instante concreto (para probar recordatorios). */
    public static AppContext contextAt(Path dir, LocalDateTime now) {
        return AppContext.create(dir, Clock.fixed(now.atZone(ZONE).toInstant(), ZONE));
    }

    /** Ejecuta una consulta SQL nativa y devuelve la primera columna de cada fila como texto. */
    public static List<String> column(AppContext ctx, String sql) {
        try (Connection c = DriverManager.getConnection(ctx.jdbcUrl());
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            List<String> values = new ArrayList<>();
            while (rs.next()) {
                values.add(rs.getString(1));
            }
            return values;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String single(AppContext ctx, String sql) {
        List<String> values = column(ctx, sql);
        if (values.size() != 1) {
            throw new IllegalStateException("Se esperaba una fila y hay " + values.size());
        }
        return values.get(0);
    }

    public static void execute(AppContext ctx, String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection(ctx.jdbcUrl());
             Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }
}
