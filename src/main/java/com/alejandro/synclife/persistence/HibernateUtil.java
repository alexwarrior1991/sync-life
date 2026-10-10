package com.alejandro.synclife.persistence;

import com.alejandro.synclife.persistence.converter.LocalDateConverter;
import com.alejandro.synclife.persistence.converter.LocalDateTimeConverter;
import com.alejandro.synclife.persistence.converter.LocalTimeConverter;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

/**
 * Construye la {@link SessionFactory} a partir de {@code hibernate.cfg.xml}.
 * La URL JDBC se fija aquí (y no en el XML) porque depende de la carpeta de datos del usuario.
 */
public final class HibernateUtil {

    private HibernateUtil() {
    }

    public static SessionFactory build(String jdbcUrl) {
        return new Configuration()
                .configure() // lee /hibernate.cfg.xml del classpath
                .setProperty("jakarta.persistence.jdbc.url", jdbcUrl)
                .addAttributeConverter(LocalDateConverter.class, true)
                .addAttributeConverter(LocalTimeConverter.class, true)
                .addAttributeConverter(LocalDateTimeConverter.class, true)
                .buildSessionFactory();
    }
}
