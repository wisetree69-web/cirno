package ru.wisetree.cirno;

import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.NodeOptions;
import dev.arbjerg.lavalink.client.event.TrackEndEvent;
import dev.arbjerg.lavalink.protocol.v4.Message.EmittedEvent.TrackEndEvent.AudioTrackEndReason;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class PlayerManager {
    private final LavalinkClient client;
    private final Map<Long, GuildMusicManager> musicManagers = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "dashboard-ticker");
        t.setDaemon(true);
        return t;
    });

    public PlayerManager(long botId) {
        this.client = new LavalinkClient(botId);

        client.addNode(
                new NodeOptions.Builder()
                        .setName("ice-fairy-node-⑨")
                        .setServerUri(URI.create("ws://localhost:2333"))
                        .setPassword("youshallnotpass")
                        .build()
        );

        client.on(TrackEndEvent.class).subscribe(event -> {
            AudioTrackEndReason reason = event.getEndReason();
            var guildId = event.getGuildId();
            var musicManager = getGuildMusicManager(guildId);

            musicManager.getScheduler().setLastPlayedTrack(event.getTrack());

            boolean shouldStartNext = reason == AudioTrackEndReason.FINISHED ||
                    reason == AudioTrackEndReason.LOAD_FAILED;

            if (shouldStartNext) {
                musicManager.getScheduler().nextTrack();
            }
        });
    }

    public GuildMusicManager getGuildMusicManager(long guildId) {
        return musicManagers.computeIfAbsent(guildId, id -> {
            var link = client.getOrCreateLink(id);
            return new GuildMusicManager(link, scheduler);
        });
    }



    public LavalinkClient getClient() {
        return client;
    }
}