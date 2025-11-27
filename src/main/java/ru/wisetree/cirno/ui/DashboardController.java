package ru.wisetree.cirno.ui;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.wisetree.cirno.TrackScheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class DashboardController {
    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    private final TrackScheduler scheduler;
    private final DashboardRenderer renderer;
    private Message dashboardMessage;
    private final List<String> logHistory = Collections.synchronizedList(new ArrayList<>());

    private final ScheduledExecutorService executor;
    private ScheduledFuture<?> tickerTask;
    private boolean updatePending = false;

    public DashboardController(TrackScheduler scheduler, ScheduledExecutorService executor) {
        this.scheduler = scheduler;
        this.renderer = new DashboardRenderer();
        this.executor = executor;
        addLog("+ Dashboard initialized! The Strongest! ⑨");
    }

    public void create(GuildMessageChannel channel) {
        if (dashboardMessage != null) {
            dashboardMessage.delete().queue(s -> {}, e -> {});
            stopTicker();
        }

        channel.sendMessage(net.dv8tion.jda.api.utils.messages.MessageCreateData.fromContent("❄️ **Loading Cirno's Ice Dashboard...**"))
                .queue(msg -> {
                    this.dashboardMessage = msg;
                    forceUpdate();
                    startTicker();
                });
    }

    private void startTicker() {
        if (tickerTask != null && !tickerTask.isCancelled()) return;

        tickerTask = executor.scheduleAtFixedRate(() -> {
            if (scheduler.getCurrentTrack() != null && !scheduler.isPaused()) {
                requestUpdate();
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    private void stopTicker() {
        if (tickerTask != null) {
            tickerTask.cancel(false);
            tickerTask = null;
        }
    }

    public void addLog(String message) {
        synchronized (logHistory) {
            if (logHistory.size() >= 15) {
                logHistory.removeFirst();
            }
            logHistory.add(message);
        }
        requestUpdate();
    }

    public void addError(String error) {
        addLog("- [ERROR] " + error);
    }

    public void addSuccess(String msg) {
        addLog("+ " + msg);
    }

    public void requestUpdate() {
        if (dashboardMessage == null) return;

        synchronized (this) {
            if (updatePending) return;
            updatePending = true;
        }

        executor.schedule(this::forceUpdate, 1000, TimeUnit.MILLISECONDS);
    }

    private void forceUpdate() {
        synchronized (this) {
            updatePending = false;
        }
        if (dashboardMessage == null) return;

        List<String> logSnapshot;
        synchronized (logHistory) {
            logSnapshot = new ArrayList<>(logHistory);
        }

        dashboardMessage.editMessage(renderer.render(scheduler, logSnapshot))
                .queue(
                        success -> {},
                        error -> {
                            log.warn("Failed to update dashboard: {}", error.getMessage());
                            if (error.getMessage().contains("Unknown Message")) {
                                dashboardMessage = null;
                                stopTicker();
                            }
                        }
                );
    }
}