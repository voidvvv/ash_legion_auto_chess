package com.voidvvv.kz_auto_chess_n.gm;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** One attachment's work queue. Closing invalidates both queued operations and their replies. */
final class Attachment {
    private final Consumer<Runnable> gameQueue;
    private final Consumer<Runnable> uiQueue;
    private boolean open = true;

    Attachment(Consumer<Runnable> gameQueue, Consumer<Runnable> uiQueue) {
        this.gameQueue = gameQueue;
        this.uiQueue = uiQueue;
    }

    synchronized <T> void submit(Supplier<T> work, Consumer<T> reply) {
        if (open) gameQueue.accept(() -> run(work, reply));
    }

    private synchronized <T> void run(Supplier<T> work, Consumer<T> reply) {
        if (!open) return;
        T result = work.get();
        uiQueue.accept(() -> deliver(result, reply));
    }

    private synchronized <T> void deliver(T result, Consumer<T> reply) {
        if (open) reply.accept(result);
    }

    synchronized void close() {
        open = false;
    }
}
