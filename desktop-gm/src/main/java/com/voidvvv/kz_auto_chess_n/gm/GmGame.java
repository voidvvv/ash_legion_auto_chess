package com.voidvvv.kz_auto_chess_n.gm;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;
import com.voidvvv.kz_auto_chess_n.Main;

final class GmGame extends Main {
    private GmWindow window;

    GmGame(FileHandle directory) {
        super(directory);
    }

    @Override
    public void render() {
        detachClosedWindow();
        if (Gdx.input.isKeyJustPressed(Input.Keys.F10)) {
            if (window != null) {
                detach();
            } else {
                try {
                    window = GmWindow.open(practiceAccess(), this::detachClosedWindow);
                } catch (RuntimeException | LinkageError ex) {
                    Gdx.app.error("GM", "Could not mount Dear ImGui. The game can continue.", ex);
                }
            }
        }
        super.render();
        if (window != null) {
            try {
                window.draw();
            } catch (RuntimeException | LinkageError ex) {
                Gdx.app.error("GM", "Dear ImGui rendering stopped. The game can continue.", ex);
                detach();
            }
        }
    }

    private void detachClosedWindow() {
        if (window != null && window.shouldClose()) detach();
    }

    private void detach() {
        if (window != null) {
            GmWindow detached = window;
            window = null;
            detached.close();
        }
    }

    @Override
    public void dispose() {
        try {
            detach();
        } finally {
            super.dispose();
        }
    }
}
