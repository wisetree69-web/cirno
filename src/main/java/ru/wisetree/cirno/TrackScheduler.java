package ru.wisetree.cirno;

import dev.arbjerg.lavalink.client.Link;
import dev.arbjerg.lavalink.client.player.PlaylistLoaded;
import dev.arbjerg.lavalink.client.player.SearchResult;
import dev.arbjerg.lavalink.client.player.Track;
import dev.arbjerg.lavalink.client.player.TrackLoaded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class TrackScheduler {
    private static final Logger log = LoggerFactory.getLogger(TrackScheduler.class);

    private final Link link;
    private final BlockingQueue<Track> queue;

    private boolean flowMode = false;
    private Track lastPlayedTrack;
    private Track currentTrack;

    public TrackScheduler(Link link) {
        this.link = link;
        this.queue = new LinkedBlockingQueue<>();
    }

    public void enqueue(Track track) {
        // Если мы ничего не играем сейчас -> играем этот трек
        if (currentTrack == null) {
            startTrack(track);
        } else {
            // Иначе -> в очередь
            queue.offer(track);
        }
    }

    public void nextTrack() {
        // 1. Сохраняем историю
        if (currentTrack != null) {
            lastPlayedTrack = currentTrack;
            currentTrack = null;
        }

        Track nextTrack = queue.poll();

        if (nextTrack != null) {
            log.info("Next track from queue: {}", nextTrack.getInfo().getTitle());
            startTrack(nextTrack);
        } else if (flowMode && lastPlayedTrack != null) {
            log.info("Queue empty, Flow Mode ON. Stopping current and loading recommendations...");
            // Сначала останавливаем текущий трек, чтобы пользователь понял, что скип сработал
            link.createOrUpdatePlayer()
                    .setTrack((Track) null)
                    .subscribe();
            // А теперь грузим новые
            loadRecommendations();
        } else {
            log.info("Queue empty, Flow Mode OFF. Stopping player.");
            link.createOrUpdatePlayer()
                    .setTrack((Track) null)
                    .subscribe();
        }
    }

    public void clearQueue() {
        queue.clear();
        lastPlayedTrack = null;
        currentTrack = null;
    }

    private void startTrack(Track track) {
        currentTrack = track;
        link.createOrUpdatePlayer()
                .setTrack(track)
                .setVolume(50)
                .subscribe();
    }

    private void loadRecommendations() {
        if (lastPlayedTrack == null) return;

        String identifier = lastPlayedTrack.getInfo().getIdentifier();
        String source = lastPlayedTrack.getInfo().getSourceName();
        String query;

        // Логика Flow
        if ("deezer".equals(source)) {
            query = "dzrec:" + identifier;
        } else if ("spotify".equals(source)) {
            // Fallback на YouTube, чтобы не возиться с капризным sprec
            String artist = lastPlayedTrack.getInfo().getAuthor();
            String title = lastPlayedTrack.getInfo().getTitle();
            query = "ytsearch:" + artist + " - " + title;
        } else if ("youtube".equals(source)) {
            query = "https://www.youtube.com/watch?v=" + identifier + "&list=RD" + identifier;
        } else {
            query = "ytsearch:" + lastPlayedTrack.getInfo().getAuthor() + " - " + lastPlayedTrack.getInfo().getTitle();
        }

        log.info("Flow Mode: Loading recommendations for [{}] using query [{}]", lastPlayedTrack.getInfo().getTitle(), query);

        link.loadItem(query).subscribe(result -> {
            if (result instanceof PlaylistLoaded playlist) {
                for (Track track : playlist.getTracks()) {
                    if (!track.getInfo().getIdentifier().equals(identifier)) {
                        queue.offer(track);
                    }
                }
                nextTrack();
            } else if (result instanceof TrackLoaded trackLoaded) {
                queue.offer(trackLoaded.getTrack());
                nextTrack();
            } else if (result instanceof SearchResult searchResult) {
                // Spotify Fallback: Нашли YouTube-версию -> генерируем Mix
                if (!searchResult.getTracks().isEmpty()) {
                    Track youtubeVersion = searchResult.getTracks().getFirst();
                    String ytId = youtubeVersion.getInfo().getIdentifier();

                    String mixUrl = "https://www.youtube.com/watch?v=" + ytId + "&list=RD" + ytId;

                    log.info("Flow Mode: Found YouTube version, loading Mix: {}", mixUrl);

                    link.loadItem(mixUrl).subscribe(mixResult -> {
                        if (mixResult instanceof PlaylistLoaded mixPlaylist) {
                            for (Track track : mixPlaylist.getTracks()) {
                                if (!track.getInfo().getIdentifier().equals(ytId)) {
                                    queue.offer(track);
                                }
                            }
                            nextTrack();
                        }
                    });
                }
            }
        });
    }

    public void pause(boolean state) {
        link.getPlayer().subscribe(player ->
                player.setPaused(state).subscribe()
        );
    }

    public void shuffle() {
        List<Track> list = new ArrayList<>();
        queue.drainTo(list);
        Collections.shuffle(list);
        queue.addAll(list);
    }

    public void skip(int amount) {
        if (amount <= 1) {
            nextTrack();
            return;
        }
        for (int i = 0; i < amount - 1; i++) {
            queue.poll();
        }
        nextTrack();
    }

    public List<Track> getQueueList() {
        return new ArrayList<>(queue);
    }

    public Track getCurrentTrack() {
        return currentTrack;
    }

    public void setFlowMode(boolean flowMode) {
        this.flowMode = flowMode;
    }

    public boolean isFlowMode() {
        return flowMode;
    }

    public void setLastPlayedTrack(Track track) {
        this.lastPlayedTrack = track;
    }

    public BlockingQueue<Track> getQueue() {
        return queue;
    }
}