package com.alejandro.synclife.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AppPathsTest {

    @AfterEach
    void clearProperty() {
        System.clearProperty(AppPaths.HOME_PROPERTY);
    }

    @Test
    void systemPropertyOverridesDataDir(@TempDir Path dir) {
        System.setProperty(AppPaths.HOME_PROPERTY, dir.toString());
        assertThat(AppPaths.dataDir()).isEqualTo(dir.toAbsolutePath());
    }

    @Test
    void defaultsToHiddenFolderInUserHome() {
        if (System.getenv(AppPaths.HOME_ENV) == null) {
            assertThat(AppPaths.dataDir()).isEqualTo(Path.of(System.getProperty("user.home"), ".synclife"));
        }
    }

    @Test
    void jdbcUrlEnablesForeignKeysAndBusyTimeout(@TempDir Path dir) {
        String url = AppPaths.jdbcUrl(AppPaths.dbFile(dir));
        assertThat(url)
                .startsWith("jdbc:sqlite:")
                .endsWith("synclife.db?foreign_keys=on&busy_timeout=5000")
                .doesNotContain("\\");
    }

    @Test
    void settingsPersistAndClampHorizon(@TempDir Path dir) throws Exception {
        AppSettings settings = AppSettings.load(dir);
        assertThat(settings.getTheme()).isEqualTo(AppSettings.ThemeMode.CLARO);
        assertThat(settings.getRoutineHorizonDays()).isEqualTo(AppSettings.DEFAULT_HORIZON);

        settings.setTheme(AppSettings.ThemeMode.OSCURO);
        settings.setRoutineHorizonDays(500);
        settings.save();

        AppSettings reloaded = AppSettings.load(dir);
        assertThat(reloaded.getTheme()).isEqualTo(AppSettings.ThemeMode.OSCURO);
        assertThat(reloaded.getRoutineHorizonDays()).isEqualTo(AppSettings.MAX_HORIZON);
    }
}
