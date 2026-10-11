package com.alejandro.synclife.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SingleInstanceTest {

    @TempDir
    Path dir;

    @Test
    void aSecondInstanceAsksTheFirstToShowItselfAndGivesUp() throws Exception {
        CountDownLatch shown = new CountDownLatch(1);
        Optional<SingleInstance> first = SingleInstance.acquire(dir, shown::countDown);
        assertThat(first).isPresent();
        assertThat(dir.resolve("synclife.port")).exists();

        Optional<SingleInstance> second = SingleInstance.acquire(dir, () -> { });

        assertThat(second).isEmpty();
        assertThat(shown.await(5, TimeUnit.SECONDS)).as("la primera recibe la petición de mostrarse").isTrue();
        first.get().close();
    }

    @Test
    void afterClosingAnotherInstanceCanStart() {
        SingleInstance first = SingleInstance.acquire(dir, () -> { }).orElseThrow();
        first.close();
        assertThat(dir.resolve("synclife.port")).doesNotExist();

        Optional<SingleInstance> again = SingleInstance.acquire(dir, () -> { });
        assertThat(again).isPresent();
        again.get().close();
    }

    @Test
    void differentDataFoldersDoNotBlockEachOther() {
        SingleInstance a = SingleInstance.acquire(dir.resolve("a"), () -> { }).orElseThrow();
        Optional<SingleInstance> b = SingleInstance.acquire(dir.resolve("b"), () -> { });
        assertThat(b).isPresent();
        b.get().close();
        a.close();
    }
}
