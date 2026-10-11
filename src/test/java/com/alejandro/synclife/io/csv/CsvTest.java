package com.alejandro.synclife.io.csv;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvTest {

    @Test
    void writesUtf8WithBomSemicolonsAndCrlf() {
        byte[] bytes = CsvWriter.write(List.of(List.of("Título", "Área"), List.of("Reunión", "Trabajo")), ';');

        assertThat(bytes).startsWith(0xEF, 0xBB, 0xBF);
        String text = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        assertThat(text).isEqualTo("Título;Área\r\nReunión;Trabajo\r\n");
    }

    @Test
    void quotesWhatNeedsQuoting() {
        assertThat(CsvWriter.cell("a;b", ';')).isEqualTo("\"a;b\"");
        assertThat(CsvWriter.cell("dice \"hola\"", ';')).isEqualTo("\"dice \"\"hola\"\"\"");
        assertThat(CsvWriter.cell("línea 1\nlínea 2", ';')).isEqualTo("\"línea 1\nlínea 2\"");
        assertThat(CsvWriter.cell(" espacio", ';')).isEqualTo("\" espacio\"");
        assertThat(CsvWriter.cell("a,b", ';')).isEqualTo("a,b");
        assertThat(CsvWriter.cell(null, ';')).isEmpty();
    }

    @Test
    void cellsThatLookLikeFormulasAreNeutralizedAndRestoredOnRead() {
        for (String dangerous : List.of("=1+1", "+34 600 000 000", "-rm", "@SUM(A1)")) {
            String written = CsvWriter.cell(dangerous, ';');
            assertThat(written).startsWith("'");
            assertThat(CsvReader.unprotect(written)).isEqualTo(dangerous);
        }
        assertThat(CsvReader.unprotect("'normal")).isEqualTo("'normal");
    }

    @Test
    void roundTripKeepsEveryCell() {
        List<List<String>> rows = List.of(
                List.of("Título", "Notas", "Subtareas"),
                List.of("Informe; v2", "Dijo \"vale\"", "[x] uno\n[ ] dos"),
                List.of("=peligro", "", "ñandú"));

        CsvReader.Parsed parsed = CsvReader.parse(CsvWriter.write(rows, ';'));

        assertThat(parsed.charset()).isEqualTo(StandardCharsets.UTF_8);
        assertThat(parsed.separator()).isEqualTo(';');
        assertThat(parsed.rows()).hasSize(3);
        assertThat(parsed.rows().get(1).cells()).containsExactly("Informe; v2", "Dijo \"vale\"", "[x] uno\n[ ] dos");
        assertThat(CsvReader.unprotect(parsed.rows().get(2).cell(0))).isEqualTo("=peligro");
        // La fila 2 empieza en la línea 2 y, como tiene un salto de línea dentro, la 3 empieza en la línea 4
        assertThat(parsed.rows().get(2).line()).isEqualTo(4);
    }

    @Test
    void detectsCommasAndTabs() {
        assertThat(CsvReader.parse("a,b,c\n1,2,3\n".getBytes(StandardCharsets.UTF_8)).separator()).isEqualTo(',');
        assertThat(CsvReader.parse("a\tb\n1\t2\n".getBytes(StandardCharsets.UTF_8)).separator()).isEqualTo('\t');
        // Las comas dentro de comillas no cuentan
        assertThat(CsvReader.parse("\"a,b,c\";d\n".getBytes(StandardCharsets.UTF_8)).separator()).isEqualTo(';');
    }

    @Test
    void readsWindows1252FromExcel() {
        byte[] bytes = "Título;Área\nCaña;Vida\n".getBytes(Charset.forName("windows-1252"));

        CsvReader.Parsed parsed = CsvReader.parse(bytes);

        assertThat(parsed.charset().name()).isEqualTo("windows-1252");
        assertThat(parsed.rows().get(0).cells()).containsExactly("Título", "Área");
        assertThat(parsed.rows().get(1).cells()).containsExactly("Caña", "Vida");
    }

    @Test
    void blankLinesAreSkippedAndMissingCellsAreEmpty() {
        CsvReader.Parsed parsed = CsvReader.parse("a;b\n\n;\nx\n".getBytes(StandardCharsets.UTF_8));
        assertThat(parsed.rows()).hasSize(2);
        assertThat(parsed.rows().get(1).cell(1)).isEmpty();
        assertThat(parsed.rows().get(1).line()).isEqualTo(4);
    }
}
