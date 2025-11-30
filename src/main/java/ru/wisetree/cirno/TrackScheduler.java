package ru.wisetree.cirno;

import dev.arbjerg.lavalink.client.Link;
import dev.arbjerg.lavalink.client.player.PlaylistLoaded;
import dev.arbjerg.lavalink.client.player.SearchResult;
import dev.arbjerg.lavalink.client.player.Track;
import dev.arbjerg.lavalink.client.player.TrackLoaded;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

public class TrackScheduler {
    private static final Logger log = LoggerFactory.getLogger(TrackScheduler.class);

    // Внутренняя обертка, чтобы знать, откуда пришел трек
    private record QueuedTrack(Track track, boolean isFlow) {}

    private final Link link;
    // Меняем тип очереди на нашу обертку
    private final BlockingQueue<QueuedTrack> queue;
    private final List<SchedulerEventListener> listeners;

    private boolean flowMode = false;
    private Track lastPlayedTrack;
    private Track currentTrack;

    public TrackScheduler(Link link) {
        this.link = link;
        this.queue = new LinkedBlockingQueue<>();
        listeners = new CopyOnWriteArrayList<>();
    }

    public void addListener(SchedulerEventListener listener) {
        listeners.add(listener);
    }

    public void removeListener(SchedulerEventListener listener) {
        listeners.remove(listener);
    }

    // Этот метод вызывается ТОЛЬКО пользователем (команды /play, кнопки)
    public synchronized void enqueue(Track track) {
        if (currentTrack == null) {
            startTrack(track);
            messageListeners("Starting: " + track.getInfo().getTitle(), MessageType.SUCCESS);
        } else {
            // 🔥 ФИЧА: Если юзер добавляет трек, сносим все Flow-треки из очереди
            purgeFlowTracks();

            // Добавляем трек пользователя (isFlow = false)
            offerToQueue(track, false);
            messageListeners("Queued: " + track.getInfo().getTitle(), MessageType.SUCCESS);
        }
    }

    private void purgeFlowTracks() {
        // Удаляем все треки, где isFlow == true
        boolean removed = queue.removeIf(QueuedTrack::isFlow);
        if (removed) {
            log.info("Purged flow tracks from queue because user added a track.");
            // Можно уведомить, но лучше тихо, чтобы не спамить
        }
    }

    private void startTrack(Track track) {
        currentTrack = track;
        link.createOrUpdatePlayer()
                .setTrack(track)
                .setVolume(50)
                .subscribe();

        notifyListenersOnTrackStartOrStop();
    }

    private void messageListeners(String message, MessageType type) {
        try {
            for (SchedulerEventListener listener : listeners) {
                listener.onSchedulerMessage(message, type);
            }
        } catch (Exception e) {
            log.error("Error while messaging listeners: {}", e.getMessage());
        }
    }

    // Приватный метод для добавления в очередь с флагом
    private void offerToQueue(Track track, boolean isFlow) {
        QueuedTrack qt = new QueuedTrack(track, isFlow);
        if (!queue.offer(qt)) {
            log.error("Error while adding to queue track: {}", track.getInfo().getTitle());
        }
    }

    public synchronized void nextTrack() {
        if (currentTrack != null) {
            lastPlayedTrack = currentTrack;
            currentTrack = null;
        }

        QueuedTrack nextQt = queue.poll();

        if (nextQt != null) {
            log.info("Next track from queue: {}", nextQt.track.getInfo().getTitle());
            startTrack(nextQt.track);
        } else if (flowMode && lastPlayedTrack != null) {
            log.info("Queue empty, Flow Mode ON.");
            messageListeners("🌊 Flow Mode: Loading recommendations...", MessageType.INFO);

            stopPlayer();
            loadRecommendations();
        } else {
            log.info("Queue empty. Stopping.");
            messageListeners("💤 Queue finished. Waiting...", MessageType.INFO);
            stopPlayer();
        }
    }

    public synchronized void clearQueue() {
        queue.clear();
        lastPlayedTrack = null;
        currentTrack = null;
        stopPlayer();
        messageListeners("Queue cleared! Silence falls... ❄️", MessageType.INFO);
    }

    public void stopPlayer() {
        link.createOrUpdatePlayer()
                .setTrack(null)
                .subscribe();
        notifyListenersOnTrackStartOrStop();
    }

    private void loadRecommendations() {
        if (lastPlayedTrack == null) return;

        String identifier = lastPlayedTrack.getInfo().getIdentifier();
        String query = getQuery();

        link.loadItem(query).subscribe(result -> {
            if (result instanceof PlaylistLoaded playlist) {
                for (Track track : playlist.getTracks()) {
                    if (!track.getInfo().getIdentifier().equals(identifier)) {
                        // 🔥 Добавляем как Flow-треки (isFlow = true)
                        offerToQueue(track, true);
                    }
                }
                messageListeners("Flow: Found " + playlist.getTracks().size() + " tracks!", MessageType.SUCCESS);
                nextTrack();
            } else if (result instanceof TrackLoaded trackLoaded) {
                offerToQueue(trackLoaded.getTrack(), true);
                nextTrack();
            } else if (result instanceof SearchResult searchResult) {
                if (!searchResult.getTracks().isEmpty()) {
                    Track youtubeVersion = searchResult.getTracks().getFirst();
                    String ytId = youtubeVersion.getInfo().getIdentifier();
                    String mixUrl = "https://www.youtube.com/watch?v=" + ytId + "&list=RD" + ytId;

                    link.loadItem(mixUrl).subscribe(mixResult -> {
                        if (mixResult instanceof PlaylistLoaded mixPlaylist) {
                            for (Track track : mixPlaylist.getTracks()) {
                                if (!track.getInfo().getIdentifier().equals(ytId)) {
                                    offerToQueue(track, true);
                                }
                            }
                            messageListeners("Flow: Mix loaded via YouTube!", MessageType.SUCCESS);
                            nextTrack();
                        }
                    });
                }
            }
        });
    }

    @NotNull
    private String getQuery() {
        // Null-check на всякий случай, хотя логика nextTrack защищает
        if (lastPlayedTrack == null) return "ytsearch:Cirno Theme";

        String identifier = lastPlayedTrack.getInfo().getIdentifier();
        String source = lastPlayedTrack.getInfo().getSourceName();
        String query;

        switch (source) {
            case "deezer" -> query = "dzrec:" + identifier;
            case "spotify" -> {
                String artist = lastPlayedTrack.getInfo().getAuthor();
                String title = lastPlayedTrack.getInfo().getTitle();
                query = "ytsearch:" + artist + " - " + title;
            }
            case "youtube" -> query = "https://www.youtube.com/watch?v=" + identifier + "&list=RD" + identifier;
            default ->
                    query = "ytsearch:" + lastPlayedTrack.getInfo().getAuthor() + " - " + lastPlayedTrack.getInfo().getTitle();
        }
        return query;
    }

    public void pause(boolean state) {
        link.getPlayer().subscribe(player ->
                player.setPaused(state).subscribe(r ->
                        messageListeners(state ? "🥶 Playback Frozen" : "▶ Playback Thawed", MessageType.INFO))
        );
    }

    public boolean isPaused() {
        try {
            return Objects.requireNonNull(link.getPlayer().block()).getPaused();
        } catch (Exception e) {
            return false;
        }
    }

    public synchronized void shuffle() {
        List<QueuedTrack> list = new ArrayList<>();
        queue.drainTo(list);
        Collections.shuffle(list);
        queue.addAll(list);
        messageListeners("Shuffled " + list.size() + " tracks! 🔀", MessageType.SUCCESS);
    }

    public synchronized void skip(int amount) {
        if (amount <= 1) {
            nextTrack();
            messageListeners("Skipped 1 track.", MessageType.INFO);
            return;
        }
        for (int i = 0; i < amount - 1; i++) {
            queue.poll();
        }
        nextTrack();
        messageListeners("Skipped " + amount + " tracks.", MessageType.SUCCESS);
    }

    // Адаптер для внешнего мира: возвращаем List<Track>, скрывая нашу обертку
    public List<Track> getQueueList() {
        return queue.stream()
                .map(qt -> qt.track)
                .collect(Collectors.toList());
    }

    public Track getCurrentTrack() {
        return currentTrack;
    }

    public void setFlowMode(boolean flowMode) {
        this.flowMode = flowMode;
        messageListeners("Flow Mode: " + (flowMode ? "ON ✅" : "OFF ❌"), MessageType.INFO);
    }

    public boolean isFlowMode() {
        return flowMode;
    }

    public void setLastPlayedTrack(Track track) {
        this.lastPlayedTrack = track;
    }

    // Возвращаем сырую очередь (если нужно для дебага), но лучше использовать getQueueList
    public BlockingQueue<Track> getQueue() {
        // ВАЖНО: Раньше мы возвращали саму очередь.
        // Теперь мы не можем вернуть BlockingQueue<QueuedTrack> как BlockingQueue<Track>.
        // Создаем копию для совместимости, если кто-то вызывает этот метод.
        return new LinkedBlockingQueue<>(getQueueList());
    }

    public long getPosition() {
        if (currentTrack == null) return 0;
        return link.getPlayer()
                .map(dev.arbjerg.lavalink.client.player.LavalinkPlayer::getPosition)
                .blockOptional()
                .orElse(0L);
    }

    private void notifyListenersOnTrackStartOrStop() {
        try {
            for (SchedulerEventListener listener : listeners) {
                listener.onTrackStartOrStop();
            }
        } catch (Exception e) {
            log.error("Error while notifying listeners on track start/stop: {}", e.getMessage());
        }
    }
}