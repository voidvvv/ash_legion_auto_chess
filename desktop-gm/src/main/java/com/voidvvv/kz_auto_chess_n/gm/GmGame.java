package com.voidvvv.kz_auto_chess_n.gm;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;
import com.voidvvv.kz_auto_chess_n.Main;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;

final class GmGame extends Main {
    private GmPanel panel; // EDT only
    private volatile boolean stopping;
    private boolean usedHotkey;

    GmGame(FileHandle directory) {
        super(directory);
    }

    @Override
    public void render() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.F10)) {
            usedHotkey = true;
            SwingUtilities.invokeLater(this::toggle);
        }
        super.render();
    }

    private void toggle() {
        if (stopping) return;
        if (panel != null) {
            detach();
            return;
        }
        panel = new GmPanel(practiceAccess(), Gdx.app::postRunnable, this::detach);
        panel.open();
    }

    private void detach() {
        if (panel != null) {
            panel.close();
            panel = null;
        }
    }

    @Override
    public void dispose() {
        stopping = true;
        try {
            if (usedHotkey) SwingUtilities.invokeAndWait(this::detach);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (InvocationTargetException ex) {
            Gdx.app.error("GM", "Could not dispose the practice window", ex.getCause());
        } finally {
            super.dispose();
        }
    }
}
