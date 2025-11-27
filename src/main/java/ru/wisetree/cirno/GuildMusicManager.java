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
        this.scheduler.setDashboard(dashboard);
    }

    public TrackScheduler getScheduler() {
        return scheduler;
    }

    public DashboardController getDashboard() {
        return dashboard;
    }
}