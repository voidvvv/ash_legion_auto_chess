package com.voidvvv.kz_auto_chess_n.gm;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AttachmentTest {
    @Test
    void detachCancelsPendingGameWorkAndLateRepliesAcrossReattach() {
        Queue<Runnable> game = new ArrayDeque<>();
        Queue<Runnable> ui = new ArrayDeque<>();
        AtomicInteger writes = new AtomicInteger();
        AtomicInteger visible = new AtomicInteger();
        Attachment first = new Attachment(game::add, ui::add);
        first.submit(writes::incrementAndGet, visible::set);
        game.remove().run();
        first.submit(writes::incrementAndGet, visible::set);
        first.close();
        first.close();

        Attachment second = new Attachment(game::add, ui::add);
        second.submit(writes::incrementAndGet, visible::set);
        game.remove().run(); // former attachment's queued mutation
        ui.remove().run(); // former attachment's queued repaint
        assertEquals(1, writes.get());
        assertEquals(0, visible.get());
        game.remove().run();
        ui.remove().run();
        assertEquals(2, writes.get());
        assertEquals(2, visible.get());
    }
}
