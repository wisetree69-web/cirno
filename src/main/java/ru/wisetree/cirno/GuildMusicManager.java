package ru.wisetree.cirno;

import dev.arbjerg.lavalink.client.Link;

public class GuildMusicManager {
    private final TrackScheduler scheduler;

    public GuildMusicManager(Link link) {
        this.scheduler = new TrackScheduler(link);
    }

    public TrackScheduler getScheduler() {
        return scheduler;
    }
}