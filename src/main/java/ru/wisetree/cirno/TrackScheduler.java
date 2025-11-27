package ru.wisetree.cirno;

import dev.arbjerg.lavalink.client.Link;
import dev.arbjerg.lavalink.client.player.PlaylistLoaded;
import dev.arbjerg.lavalink.client.player.SearchResult;
import dev.arbjerg.lavalink.client.player.Track;
import dev.arbjerg.lavalink.client.player.TrackLoaded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.wisetree.cirno.ui.DashboardController;

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

    // Ссылка на UI контроллер
    private DashboardController dashboard;

    public TrackScheduler(Link link) {
        this.link = link;
        this.queue = new LinkedBlockingQueue<>();
    }

    public void setDashboard(DashboardController dashboard) {
        this.dashboard = dashboard;
    }

    public synchronized void enqueue(Track track) {
        if (currentTrack == null) {
            startTrack(track);
            if (dashboard != null) dashboard.addSuccess("Starting: " + track.getInfo().getTitle());
        } else {
            queue.offer(track);
            if (dashboard != null) dashboard.addSuccess("Queued: " + track.getInfo().getTitle());
        }
    }

    public synchronized void nextTrack() {
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
            log.info("Queue empty, Flow Mode ON.");
            if (dashboard != null) dashboard.addLog("🌊 Flow Mode: Loading recommendations...");

            // Останавливаем текущий, пока ищем новый
            stopPlayer();
            loadRecommendations();
        } else {
            log.info("Queue empty. Stopping.");
            if (dashboard != null) dashboard.addLog("💤 Queue finished. Waiting...");
            stopPlayer();
        }
    }

    public synchronized void clearQueue() {
        queue.clear();
        lastPlayedTrack = null;
        currentTrack = null;
        stopPlayer();
        if (dashboard != null) dashboard.addSuccess("Queue cleared! Silence falls... ❄️");
    }

    private void startTrack(Track track) {
        currentTrack = track;
        link.createOrUpdatePlayer()
                .setTrack(track)
                .setVolume(50)
                .subscribe();

        // Форсируем обновление дешборда
        if (dashboard != null) dashboard.requestUpdate();
    }

    private void stopPlayer() {
        link.createOrUpdatePlayer()
                .setTrack((Track) null)
                .subscribe();
        if (dashboard != null) dashboard.requestUpdate();
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
            String artist = lastPlayedTrack.getInfo().getAuthor();
            String title = lastPlayedTrack.getInfo().getTitle();
            query = "ytsearch:" + artist + " - " + title;
        } else if ("youtube".equals(source)) {
            query = "https://www.youtube.com/watch?v=" + identifier + "&list=RD" + identifier;
        } else {
            query = "ytsearch:" + lastPlayedTrack.getInfo().getAuthor() + " - " + lastPlayedTrack.getInfo().getTitle();
        }

        link.loadItem(query).subscribe(result -> {
            if (result instanceof PlaylistLoaded playlist) {
                for (Track track : playlist.getTracks()) {
                    if (!track.getInfo().getIdentifier().equals(identifier)) {
                        queue.offer(track);
                    }
                }
                if (dashboard != null) dashboard.addSuccess("Flow: Found " + playlist.getTracks().size() + " tracks!");
                nextTrack();
            } else if (result instanceof TrackLoaded trackLoaded) {
                queue.offer(trackLoaded.getTrack());
                nextTrack();
            } else if (result instanceof SearchResult searchResult) {
                // Spotify Fallback logic
                if (!searchResult.getTracks().isEmpty()) {
                    Track youtubeVersion = searchResult.getTracks().getFirst();
                    String ytId = youtubeVersion.getInfo().getIdentifier();
                    String mixUrl = "https://www.youtube.com/watch?v=" + ytId + "&list=RD" + ytId;

                    link.loadItem(mixUrl).subscribe(mixResult -> {
                        if (mixResult instanceof PlaylistLoaded mixPlaylist) {
                            for (Track track : mixPlaylist.getTracks()) {
                                if (!track.getInfo().getIdentifier().equals(ytId)) {
                                    queue.offer(track);
                                }
                            }
                            if (dashboard != null) dashboard.addSuccess("Flow: Mix loaded via YouTube!");
                            nextTrack();
                        }
                    });
                }
            }
        });
    }

    public void pause(boolean state) {
        link.getPlayer().subscribe(player ->
                player.setPaused(state).subscribe(r -> {
                    if (dashboard != null) {
                        dashboard.addLog(state ? "🥶 Playback Frozen" : "▶ Playback Thawed");
                    }
                })
        );
    }

    public boolean isPaused() {
        try {
            // В идеале состояние нужно кешировать, но для примера берем блокирующе
            return link.getPlayer().block().getPaused();
        } catch (Exception e) {
            return false;
        }
    }

    public synchronized void shuffle() {
        List<Track> list = new ArrayList<>();
        queue.drainTo(list);
        Collections.shuffle(list);
        queue.addAll(list);
        if (dashboard != null) dashboard.addSuccess("Shuffled " + list.size() + " tracks! 🔀");
    }

    public synchronized void skip(int amount) {
        if (amount <= 1) {
            nextTrack();
            if (dashboard != null) dashboard.addLog("Skipped 1 track.");
            return;
        }
        for (int i = 0; i < amount - 1; i++) {
            queue.poll();
        }
        nextTrack();
        if (dashboard != null) dashboard.addSuccess("Skipped " + amount + " tracks.");
    }

    public List<Track> getQueueList() {
        return new ArrayList<>(queue);
    }

    public Track getCurrentTrack() {
        return currentTrack;
    }

    public void setFlowMode(boolean flowMode) {
        this.flowMode = flowMode;
        if (dashboard != null) dashboard.addLog("Flow Mode: " + (flowMode ? "ON ✅" : "OFF ❌"));
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

    public long getPosition() {
        if (currentTrack == null) return 0;
        // Получаем плеер блокирующим образом (для UI это допустимо, так как это быстро)
        return link.getPlayer()
                .map(dev.arbjerg.lavalink.client.player.LavalinkPlayer::getPosition)
                .blockOptional()
                .orElse(0L);
    }
}