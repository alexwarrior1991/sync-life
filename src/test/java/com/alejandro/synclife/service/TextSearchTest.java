package com.alejandro.synclife.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextSearchTest {

    @Test
    void ignoresCaseAndAccents() {
        assertThat(TextSearch.normalize("  Reunión SEMANAL ")).isEqualTo("reunion semanal");
        assertThat(TextSearch.matches("reunion", "Reunión semanal")).isTrue();
        assertThat(TextSearch.matches("ÁRBOL", "arbol de navidad")).isTrue();
    }

    @Test
    void requiresEveryWordInAnyField() {
        assertThat(TextSearch.matches("informe ventas", "Informe", "datos de ventas Q3")).isTrue();
        assertThat(TextSearch.matches("informe compras", "Informe", "datos de ventas")).isFalse();
        assertThat(TextSearch.matches("", "lo que sea")).isTrue();
        assertThat(TextSearch.matches("algo", (String) null)).isFalse();
    }
}
