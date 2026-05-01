package org.sainm.schemapilot.datamove;

import java.util.concurrent.atomic.AtomicBoolean;

class DataMoveControl {
    private final AtomicBoolean paused = new AtomicBoolean();
    private final AtomicBoolean cancelled = new AtomicBoolean();

    boolean paused() {
        return paused.get();
    }

    boolean cancelled() {
        return cancelled.get();
    }

    void pause() {
        paused.set(true);
    }

    void resume() {
        paused.set(false);
    }

    void cancel() {
        cancelled.set(true);
        paused.set(false);
    }
}
