package com.alejandro.synclife.io.csv;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Escribe CSV (RFC 4180) pensado para abrirse con doble clic en Excel en español: UTF-8 con BOM (para que
 * respete las tildes), separador {@code ;} y saltos de línea CRLF.
 *
 * <p>Las celdas que empiezan por {@code = + - @} (o tabulador/retorno) llevan delante un apóstrofo para que
 * la hoja de cálculo no las ejecute como fórmula (inyección de CSV); {@link CsvReader#unprotect} lo quita.
 */
public final class CsvWriter {

    public static final char DEFAULT_SEPARATOR = ';';
    static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    static final String FORMULA_STARTS = "=+-@\t\r";

    private CsvWriter() {
    }

    public static byte[] write(List<List<String>> rows, char separator) {
        StringBuilder text = new StringBuilder();
        for (List<String> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) {
                    text.append(separator);
                }
                text.append(cell(row.get(i), separator));
            }
            text.append("\r\n");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(UTF8_BOM);
        out.writeBytes(text.toString().getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    /** Una celda ya protegida y, si hace falta, entre comillas. */
    static String cell(String value, char separator) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String text = FORMULA_STARTS.indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        boolean quote = text.indexOf(separator) >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0
                || text.indexOf('\r') >= 0 || Character.isWhitespace(text.charAt(0))
                || Character.isWhitespace(text.charAt(text.length() - 1));
        return quote ? '"' + text.replace("\"", "\"\"") + '"' : text;
    }
}
