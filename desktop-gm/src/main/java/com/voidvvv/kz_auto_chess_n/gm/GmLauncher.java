package com.voidvvv.kz_auto_chess_n.gm;

import com.badlogic.gdx.files.FileHandle;
import com.voidvvv.kz_auto_chess_n.lwjgl3.Lwjgl3Launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class GmLauncher {
    private GmLauncher() {
    }

    public static void main(String[] args) throws IOException {
        String configured = System.getProperty("ember.gm.saveDir");
        Path directory = configured == null
                ? Files.createTempDirectory("ember-gm-")
                : Files.createDirectories(Paths.get(configured).toAbsolutePath());
        System.out.println("GM practice files: " + directory.toRealPath() + " | Attach/detach: F10");
        Lwjgl3Launcher.launch(new GmGame(new FileHandle(directory.toFile())), args);
    }
}
