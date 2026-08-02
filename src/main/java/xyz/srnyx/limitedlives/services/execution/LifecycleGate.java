package xyz.srnyx.limitedlives.services.execution;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Testable lifecycle gate and schedule-after-stop instrumentation. */
public final class LifecycleGate {
    private final AtomicReference<PluginLifecycle> state = new AtomicReference<>(PluginLifecycle.STOPPED);
    private final AtomicLong rejectedSubmissions = new AtomicLong();

    public void start() {
        if (!state.compareAndSet(PluginLifecycle.STOPPED, PluginLifecycle.RUNNING)) throw new IllegalStateException("Lifecycle is not stopped");
    }

    public boolean trySubmit(boolean pluginEnabled) {
        if (pluginEnabled && state.get() == PluginLifecycle.RUNNING) return true;
        rejectedSubmissions.incrementAndGet();
        return false;
    }

    public boolean isRunning(boolean pluginEnabled) {
        return pluginEnabled && state.get() == PluginLifecycle.RUNNING;
    }

    public boolean beginStopping() {
        return state.compareAndSet(PluginLifecycle.RUNNING, PluginLifecycle.STOPPING);
    }

    public void stopped() {
        state.set(PluginLifecycle.STOPPED);
    }

    public PluginLifecycle state() { return state.get(); }
    public long rejectedSubmissions() { return rejectedSubmissions.get(); }
}
