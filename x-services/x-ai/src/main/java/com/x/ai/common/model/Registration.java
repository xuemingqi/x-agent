package com.x.ai.common.model;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Registration implements AutoCloseable {

    private final Runnable closeAction;

    private final AtomicBoolean closed = new AtomicBoolean();


    public Registration(Runnable closeAction) {
        this.closeAction = Objects.requireNonNull(closeAction, "closeAction");
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            closeAction.run();
        }
    }
}
