package com.alejandro.synclife.config;

import com.alejandro.synclife.config.AppSettings.PomodoroSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AppSettingsTest {

    @TempDir
    Path dir;

    @Test
    void pomodoroDefaultsWhenThereIsNoFile() {
        assertThat(AppSettings.load(dir).getPomodoro()).isEqualTo(PomodoroSettings.DEFAULT);
    }

    @Test
    void pomodoroSettingsAreSavedAndReloaded() throws Exception {
        AppSettings settings = AppSettings.load(dir);
        settings.setPomodoro(new PomodoroSettings(50, 10, 20, 3, false, false));
        settings.save();

        assertThat(AppSettings.load(dir).getPomodoro())
                .isEqualTo(new PomodoroSettings(50, 10, 20, 3, false, false));
    }

    @Test
    void outOfRangeOrBrokenValuesAreCorrected() throws Exception {
        Files.writeString(dir.resolve("settings.properties"), """
                pomodoro.focus.minutes=500
                pomodoro.shortBreak.minutes=0
                pomodoro.longBreak.minutes=abc
                pomodoro.longBreak.every=1
                """);

        PomodoroSettings pomodoro = AppSettings.load(dir).getPomodoro();

        assertThat(pomodoro.focusMinutes()).isEqualTo(90);
        assertThat(pomodoro.shortBreakMinutes()).isEqualTo(1);
        assertThat(pomodoro.longBreakMinutes()).isEqualTo(PomodoroSettings.DEFAULT.longBreakMinutes());
        assertThat(pomodoro.longBreakEvery()).isEqualTo(2);
        assertThat(pomodoro.autoStartBreaks()).isTrue();
    }
}
