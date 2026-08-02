package xyz.srnyx.limitedlives.services.execution;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

import xyz.srnyx.limitedlives.LimitedLives;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * The only component allowed to submit work to Folia schedulers.
 */
public final class FoliaExecutionService {
    @NotNull private final LimitedLives plugin;
    @NotNull private final LifecycleGate lifecycle = new LifecycleGate();
    @NotNull private final Set<ScheduledTask> tasks = ConcurrentHashMap.newKeySet();

    public FoliaExecutionService(@NotNull LimitedLives plugin) {
        this.plugin = plugin;
    }

    public void start() {
        lifecycle.start();
    }

    @NotNull
    public PluginLifecycle getLifecycle() {
        return lifecycle.state();
    }

    public long getRejectedSubmissions() {
        return lifecycle.rejectedSubmissions();
    }

    public boolean runForEntity(@NotNull Entity entity, @NotNull Runnable action, @NotNull Runnable retired) {
        if (!acceptSubmission()) return false;
        final ScheduledTask task = entity.getScheduler().run(plugin, scheduled -> executeTracked(scheduled, action), retired);
        return track(task);
    }

    public boolean runForEntityOrNow(@NotNull Entity entity, @NotNull Runnable action, @NotNull Runnable retired) {
        if (!acceptSubmission()) return false;
        if (plugin.getServer().isOwnedByCurrentRegion(entity)) {
            action.run();
            return true;
        }
        return runForEntity(entity, action, retired);
    }

    public boolean runForRegion(@NotNull Location location, @NotNull Runnable action) {
        if (!acceptSubmission()) return false;
        final ScheduledTask task = plugin.getServer().getRegionScheduler().run(plugin, location, scheduled -> executeTracked(scheduled, action));
        return track(task);
    }

    public boolean runGlobal(@NotNull Runnable action) {
        if (!acceptSubmission()) return false;
        final ScheduledTask task = plugin.getServer().getGlobalRegionScheduler().run(plugin, scheduled -> executeTracked(scheduled, action));
        return track(task);
    }

    public boolean runGlobalOrNow(@NotNull Runnable action) {
        if (!acceptSubmission()) return false;
        if (Bukkit.isGlobalTickThread()) {
            action.run();
            return true;
        }
        return runGlobal(action);
    }

    public boolean runAsync(@NotNull Runnable action) {
        if (!acceptSubmission()) return false;
        final ScheduledTask task = plugin.getServer().getAsyncScheduler().runNow(plugin, scheduled -> executeTracked(scheduled, action));
        return track(task);
    }

    public boolean runAsyncDelayed(@NotNull Runnable action, long delay, @NotNull TimeUnit unit) {
        if (!acceptSubmission()) return false;
        final ScheduledTask task = plugin.getServer().getAsyncScheduler().runDelayed(plugin, scheduled -> executeTracked(scheduled, action), delay, unit);
        return track(task);
    }

    /**
     * Prevents submissions first, then cancels every task owned by this service.
     * This method never submits scheduler work and is safe to call from disable().
     */
    public void stop() {
        if (!lifecycle.beginStopping()) return;
        for (final ScheduledTask task : tasks) task.cancel();
        tasks.clear();
        lifecycle.stopped();
    }

    private boolean isRunning() {
        return lifecycle.isRunning(plugin.isEnabled());
    }

    private boolean acceptSubmission() {
        return lifecycle.trySubmit(plugin.isEnabled());
    }

    private boolean track(ScheduledTask task) {
        if (task == null) return false;
        if (!isRunning()) {
            task.cancel();
            return false;
        }
        tasks.add(task);
        final ScheduledTask.ExecutionState state = task.getExecutionState();
        if (state == ScheduledTask.ExecutionState.FINISHED
                || state == ScheduledTask.ExecutionState.CANCELLED
                || state == ScheduledTask.ExecutionState.CANCELLED_RUNNING) tasks.remove(task);
        return true;
    }

    private void executeTracked(@NotNull ScheduledTask task, @NotNull Runnable action) {
        try {
            if (isRunning()) action.run();
        } finally {
            tasks.remove(task);
        }
    }
}
