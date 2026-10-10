package com.alejandro.synclife.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Comparación de texto sin distinguir mayúsculas ni tildes ("reunion" encuentra "Reunión").
 * Se hace en memoria porque {@code lower()} de SQLite solo entiende ASCII.
 */
public final class TextSearch {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

    private TextSearch() {
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        return DIACRITICS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT).trim();
    }

    /** ¿Contiene alguno de los textos todas las palabras de la consulta? */
    public static boolean matches(String query, String... fields) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isEmpty()) {
            return true;
        }
        StringBuilder haystack = new StringBuilder();
        for (String field : fields) {
            haystack.append(normalize(field)).append(' ');
        }
        for (String word : normalizedQuery.split("\\s+")) {
            if (haystack.indexOf(word) < 0) {
                return false;
            }
        }
        return true;
    }
}
