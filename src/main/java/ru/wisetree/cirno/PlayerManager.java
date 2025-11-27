package ru.wisetree.cirno;

import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.NodeOptions;
import dev.arbjerg.lavalink.client.event.TrackEndEvent;
import dev.arbjerg.lavalink.protocol.v4.Message.EmittedEvent.TrackEndEvent.AudioTrackEndReason;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerManager {
    private final LavalinkClient client;
    private final Map<Long, GuildMusicManager> musicManagers = new ConcurrentHashMap<>();

    public PlayerManager(long botId) {
        this.client = new LavalinkClient(botId);

        client.addNode(
                new NodeOptions.Builder()
                        .setName("local-node")
                        .setServerUri(URI.create("ws://localhost:2333"))
                        .setPassword("youshallnotpass")
                        .build()
        );

        client.on(TrackEndEvent.class).subscribe(event -> {
            AudioTrackEndReason reason = event.getEndReason();
            var guildId = event.getGuildId();
            var musicManager = getGuildMusicManager(guildId);

            // 1. Сохраняем трек как "последний сыгранный"
            musicManager.getScheduler().setLastPlayedTrack(event.getTrack());

            // 2. Если трек кончился нормально -> включаем следующий
            boolean shouldStartNext = reason == AudioTrackEndReason.FINISHED ||
                    reason == AudioTrackEndReason.LOAD_FAILED;

            if (shouldStartNext) {
                musicManager.getScheduler().nextTrack();
            }
        });
    }

    // Остальной код без изменений...
    public GuildMusicManager getGuildMusicManager(long guildId) {
        return musicManagers.computeIfAbsent(guildId, id -> {
            var link = client.getOrCreateLink(id);
            return new GuildMusicManager(link);
        });
    }

    public LavalinkClient getClient() {
        return client;
    }
}