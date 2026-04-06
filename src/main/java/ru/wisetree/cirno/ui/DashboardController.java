package ru.wisetree.cirno.ui;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.wisetree.cirno.MessageType;
import ru.wisetree.cirno.SchedulerEventListener;
import ru.wisetree.cirno.TrackScheduler;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class DashboardController implements SchedulerEventListener {
    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);
    private static final int MAX_IDLE_SECONDS = 300;
    private static final int TICK_INTERVAL = 10;
    private static final int DEBOUNCE_DELAY_MS = 1500;
    private static final int LOGS_LIMIT = 15;

    private final TrackScheduler scheduler;
    private final DashboardRenderer renderer;
    private final Deque<String> logHistory = new ConcurrentLinkedDeque<>();
    private final ScheduledExecutorService executor;
    private final AtomicBoolean updatePending = new AtomicBoolean(false);
    private final AtomicInteger idleSecondsCounter = new AtomicInteger(0);

    private volatile ScheduledFuture<?> tickerTask;
    private volatile Message dashboardMessage;

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

        idleSecondsCounter.set(0);

        channel.sendMessage(net.dv8tion.jda.api.utils.messages.MessageCreateData.fromContent("❄️ **Loading Cirno's Ice Dashboard...**"))
                .queue(msg -> {
                    this.dashboardMessage = msg;
                    updatePending.set(true);
                    forceUpdate();
                    startTicker();
                });
    }

    private void startTicker() {
//        if (tickerTask != null && !tickerTask.isCancelled()) return;
//
//        tickerTask = executor.scheduleAtFixedRate(() -> {
//            try {
//                boolean isPlaying = scheduler.getCurrentTrack() != null;
//                boolean isQueueNotEmpty = !scheduler.getQueue().isEmpty();
//
//                if (isPlaying || isQueueNotEmpty) {
//                    idleSecondsCounter.set(0);
//                    requestUpdate();
//                } else {
//                    idleSecondsCounter.getAndAdd(TICK_INTERVAL);
//
//                    if (idleSecondsCounter.get() >= MAX_IDLE_SECONDS) {
//                        log.info("Dashboard idle timeout. Deleting message.");
//                        deleteMessage();
//                    }
//                }
//            } catch (Exception e) {
//                log.error("Error in ticker", e);
//            }
//        }, TICK_INTERVAL, TICK_INTERVAL, TimeUnit.SECONDS);
    }

    private void stopTicker() {
        if (tickerTask != null) {
            tickerTask.cancel(false);
            tickerTask = null;
        }
    }

    public void addLog(String message) {
        logHistory.add(message);
        while (logHistory.size() > LOGS_LIMIT) {
            logHistory.poll();
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

        if (updatePending.get()) return;
        if(!updatePending.compareAndSet(false, true)) return;

        executor.schedule(this::forceUpdate, 2000, TimeUnit.MILLISECONDS);
    }

    private void forceUpdate() {
        if(!updatePending.compareAndSet(true, false)) return;
        if (dashboardMessage == null) return;

        List<String> logSnapshot;
        logSnapshot = new ArrayList<>(logHistory);

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

    public void updateImmediately() {
        if (dashboardMessage == null) return;

        // 1. Принудительно взводим флаг.
        // Даже если обновление уже запланировано (через секунду), мы перехватим его.
        updatePending.set(true);

        // 2. Планируем выполнение почти мгновенно (100мс для синхронизации состояния Lavalink)
        // Мы используем schedule, а не submit, чтобы дать время Lavalink применить изменения
        executor.schedule(this::forceUpdate, 100, TimeUnit.MILLISECONDS);
    }

    public void deleteMessage() {
        if (dashboardMessage != null) {
            dashboardMessage.delete().queue(s -> {}, e -> {});
            dashboardMessage = null;
        }
        stopTicker();
    }

    @Override
    public void onTrackStartOrStop() {
        requestUpdate();
    }

    @Override
    public void onSchedulerMessage(String message, MessageType messageType) {
        switch (messageType) {
            case SUCCESS -> addSuccess(message);
            case INFO -> addLog(message);
        }
    }
}