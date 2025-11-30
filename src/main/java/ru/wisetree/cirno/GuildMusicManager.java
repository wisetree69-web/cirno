package ru.wisetree.cirno;

import dev.arbjerg.lavalink.client.Link;
import ru.wisetree.cirno.ui.DashboardController;

import java.util.concurrent.ScheduledExecutorService;

public class GuildMusicManager {
    private final TrackScheduler scheduler;
    private final DashboardController dashboard;

    public GuildMusicManager(Link link, ScheduledExecutorService executor) {
        this.scheduler = new TrackScheduler(link);
        this.dashboard = new DashboardController(scheduler, executor);
        this.scheduler.addListener(dashboard);
    }

    public void destroy() {
        scheduler.clearQueue();
        scheduler.stopPlayer();

        scheduler.removeListener(dashboard);
        if (dashboard != null) {
            dashboard.deleteMessage();
        }
    }

    public TrackScheduler getScheduler() {
        return scheduler;
    }

    public DashboardController getDashboard() {
        return dashboard;
    }
}