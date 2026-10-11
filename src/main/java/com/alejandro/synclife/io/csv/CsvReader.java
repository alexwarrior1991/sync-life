package com.alejandro.synclife.io.csv;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Lee CSV (RFC 4180) de cualquier hoja de cálculo: detecta la codificación (UTF-8, con o sin BOM; si no lo es,
 * Windows-1252, la de Excel en Windows) y el separador ({@code ;}, {@code ,} o tabulador), admite comillas,
 * comillas dobladas y saltos de línea dentro de una celda, y recuerda en qué línea empieza cada fila.
 */
public final class CsvReader {

    /** Una fila con el número de línea del fichero en el que empieza (para los mensajes de error). */
    public record Row(int line, List<String> cells) {
        public String cell(int index) {
            return index < cells.size() ? cells.get(index) : "";
        }

        public boolean isBlank() {
            return cells.stream().allMatch(String::isBlank);
        }
    }

    public record Parsed(Charset charset, char separator, List<Row> rows) {
    }

    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    private CsvReader() {
    }

    public static Parsed parse(byte[] bytes) {
        Charset[] charset = new Charset[1];
        String text = decode(bytes, charset);
        char separator = detectSeparator(text);
        return new Parsed(charset[0], separator, parse(text, separator));
    }

    /** Quita el apóstrofo con el que {@link CsvWriter} protege las celdas que parecen fórmulas. */
    public static String unprotect(String value) {
        if (value != null && value.length() >= 2 && value.charAt(0) == '\''
                && CsvWriter.FORMULA_STARTS.indexOf(value.charAt(1)) >= 0) {
            return value.substring(1);
        }
        return value;
    }

    static String decode(byte[] bytes, Charset[] used) {
        if (bytes.length >= 3 && Arrays.equals(Arrays.copyOf(bytes, 3), CsvWriter.UTF8_BOM)) {
            used[0] = StandardCharsets.UTF_8;
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            used[0] = StandardCharsets.UTF_8;
            return text;
        } catch (CharacterCodingException e) {
            used[0] = WINDOWS_1252;
            return new String(bytes, WINDOWS_1252);
        }
    }

    /** El separador más frecuente fuera de comillas en la primera fila (cabecera); por defecto {@code ;}. */
    static char detectSeparator(String text) {
        int semicolons = 0;
        int commas = 0;
        int tabs = 0;
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (!quoted) {
                if (c == '\n' || c == '\r') {
                    break;
                }
                switch (c) {
                    case ';' -> semicolons++;
                    case ',' -> commas++;
                    case '\t' -> tabs++;
                    default -> { }
                }
            }
        }
        if (tabs > semicolons && tabs > commas) {
            return '\t';
        }
        return commas > semicolons ? ',' : ';';
    }

    static List<Row> parse(String text, char separator) {
        List<Row> rows = new ArrayList<>();
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        int line = 1;
        int rowStart = 1;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    if (c == '\n') {
                        line++;
                    }
                    cell.append(c);
                }
            } else if (c == '"' && cell.isEmpty()) {
                quoted = true;
            } else if (c == separator) {
                cells.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\r' || c == '\n') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                cells.add(cell.toString());
                cell.setLength(0);
                addRow(rows, rowStart, cells);
                cells = new ArrayList<>();
                line++;
                rowStart = line;
            } else {
                cell.append(c);
            }
            i++;
        }
        if (!cell.isEmpty() || !cells.isEmpty()) {
            cells.add(cell.toString());
            addRow(rows, rowStart, cells);
        }
        return rows;
    }

    private static void addRow(List<Row> rows, int line, List<String> cells) {
        Row row = new Row(line, List.copyOf(cells));
        if (!row.isBlank()) {
            rows.add(row);
        }
    }
}
