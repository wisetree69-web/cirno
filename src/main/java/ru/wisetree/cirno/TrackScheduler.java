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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TrackScheduler {
    private static final Logger log = LoggerFactory.getLogger(TrackScheduler.class);
    private static final Pattern YT_VIDEO_ID = Pattern.compile("(?:v=|/v/|youtu\\.be/)([a-zA-Z0-9_-]{11})");

    private record QueuedTrack(Track track, boolean isFlow, String userName) {}

    private final Link link;
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

    /**
     * Returns a human-readable title for a track.
     * Some sources return URLs as titles — this extracts a readable fallback.
     */
    public static String getDisplayTitle(Track track) {
        String title = track.getInfo().getTitle();
        if (title != null && !title.isEmpty() && !title.startsWith("http")) {
            return title;
        }
        // Try to extract from URI
        String uri = track.getInfo().getUri();
        if (uri != null && uri.contains("youtube.com")) {
            Matcher m = YT_VIDEO_ID.matcher(uri);
            if (m.find()) return "YouTube Video: " + m.group(1);
        }
        if (uri != null && uri.startsWith("spotify:")) {
            String[] parts = uri.split(":");
            if (parts.length >= 3) return "Spotify Track: " + parts[2];
        }
        return title != null && !title.isEmpty() ? title : "Unknown Title";
    }

    public void addListener(SchedulerEventListener listener) {
        listeners.add(listener);
    }

    public void removeListener(SchedulerEventListener listener) {
        listeners.remove(listener);
    }

    public synchronized void enqueue(Track track) {
        enqueue(track, null);
    }

    private String queuedByUser;

    public synchronized void enqueue(Track track, String userName) {
        String displayTitle = getDisplayTitle(track);
        // Log queue first, then start playing (correct order)
        messageListeners("📥 " + displayTitle, MessageType.SUCCESS, userName);

        if (currentTrack == null) {
            queuedByUser = userName;
            // Add to flow history (user-queued tracks only)
            addToFlowHistory(track);
            startTrack(track, userName);
        } else {
            purgeFlowTracks();
            offerToQueue(track, false, userName);
            // Add to flow history
            addToFlowHistory(track);
        }
    }

    // Flow history: last 10 user-queued tracks (not flow-generated)
    private final List<Track> flowHistory = new ArrayList<>();

    private void addToFlowHistory(Track track) {
        flowHistory.add(track);
        if (flowHistory.size() > 10) {
            flowHistory.remove(0);
        }
    }

    private void purgeFlowTracks() {
        boolean removed = queue.removeIf(QueuedTrack::isFlow);
        if (removed) {
            log.info("Purged flow tracks from queue because user added a track.");
        }
    }

    private void startTrack(Track track, String userName) {
        currentTrack = track;
        queuedByUser = userName;
        link.createOrUpdatePlayer()
                .setTrack(track)
                .setVolume(50)
                .subscribe();

        messageListeners("🔊 " + getDisplayTitle(track), MessageType.INFO, userName);
        notifyListenersOnTrackStartOrStop();
    }

    private void messageListeners(String message, MessageType type) {
        messageListeners(message, type, null);
    }

    private void messageListeners(String message, MessageType type, String userName) {
        try {
            for (SchedulerEventListener listener : listeners) {
                listener.onSchedulerMessage(message, type, userName);
            }
        } catch (Exception e) {
            log.error("Error while messaging listeners: {}", e.getMessage());
        }
    }

    private void offerToQueue(Track track, boolean isFlow, String userName) {
        QueuedTrack qt = new QueuedTrack(track, isFlow, userName);
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
            startTrack(nextQt.track, nextQt.userName());
        } else if (flowMode && lastPlayedTrack != null) {
            log.info("Queue empty, Flow Mode ON.");
            stopPlayer();
            loadRecommendations("Flow");
        } else {
            log.info("Queue empty. Stopping.");
            messageListeners("💤 Queue finished", MessageType.INFO);
            stopPlayer();
        }
    }

    // Convenience overloads without user (for slash commands)
    public void pause(boolean state) {
        pause(state, null);
    }

    public synchronized void clearQueue() {
        clearQueue(null);
    }

    public synchronized void shuffle() {
        shuffle(null);
    }

    public synchronized void skip(int amount) {
        skip(amount, null);
    }

    public void setFlowMode(boolean flowMode) {
        setFlowMode(flowMode, null);
    }

    // Main methods with user attribution
    public void pause(boolean state, String userName) {
        link.createOrUpdatePlayer()
                .setPaused(state)
                .subscribe(player ->
                        messageListeners(state ? "⏸️ Paused" : "▶️ Resumed", MessageType.INFO, userName)
                );
    }

    public synchronized void clearQueue(String userName) {
        queue.clear();
        lastPlayedTrack = null;
        currentTrack = null;
        stopPlayer();
        messageListeners("🧹 Queue cleared", MessageType.INFO, userName);
    }

    public synchronized void shuffle(String userName) {
        List<QueuedTrack> list = new ArrayList<>();
        queue.drainTo(list);
        Collections.shuffle(list);
        queue.addAll(list);
        messageListeners("🔀 Shuffled " + list.size() + " tracks", MessageType.SUCCESS, userName);
    }

    public synchronized void skip(int amount, String userName) {
        String skipTarget = (currentTrack != null) ? getDisplayTitle(currentTrack) : "nothing";
        if (amount <= 1) {
            messageListeners("⏭️ " + skipTarget, MessageType.INFO, userName);
            nextTrack();
            return;
        }
        for (int i = 0; i < amount - 1; i++) {
            queue.poll();
        }
        messageListeners("⏭️ " + skipTarget, MessageType.INFO, userName);
        nextTrack();
    }

    public void setFlowMode(boolean flowMode, String userName) {
        this.flowMode = flowMode;
        messageListeners("🌊 Flow " + (flowMode ? "enabled" : "disabled"), MessageType.INFO, userName);
    }

    public void stopPlayer() {
        link.createOrUpdatePlayer()
                .setTrack(null)
                .subscribe();
        notifyListenersOnTrackStartOrStop();
    }

    private void loadRecommendations(String userName) {
        if (flowHistory.isEmpty()) {
            messageListeners("🌊 No history to generate flow from", MessageType.INFO, userName);
            return;
        }

        // Build set of identifiers we already played or have in queue
        Set<String> playedIdentifiers = new HashSet<>();
        if (lastPlayedTrack != null) playedIdentifiers.add(lastPlayedTrack.getInfo().getIdentifier());
        for (QueuedTrack qt : queue) {
            playedIdentifiers.add(qt.track.getInfo().getIdentifier());
        }

        // Take last 5 user-queued tracks from history
        int take = Math.min(5, flowHistory.size());
        List<Track> seeds = new ArrayList<>(flowHistory.subList(flowHistory.size() - take, flowHistory.size()));
        Collections.shuffle(seeds);

        // Load all 5 mixes concurrently, combine results
        List<Track> combined = new ArrayList<>();
        final Object lock = new Object();
        final int[] pending = {seeds.size()};
        final boolean[] finalized = {false};

        for (Track seed : seeds) {
            String source = seed.getInfo().getSourceName();
            String identifier = seed.getInfo().getIdentifier();
            String artist = seed.getInfo().getAuthor();
            String title = getDisplayTitle(seed);

            String query = switch (source) {
                case "youtube" -> "https://www.youtube.com/watch?v=" + identifier + "&list=RD" + identifier;
                default -> "ytsearch:" + artist + " - " + title;
            };

            final String finalQuery = query;
            link.loadItem(query).subscribe(result -> {
                List<Track> fromSeed = new ArrayList<>();
                synchronized (lock) {
                    if (result instanceof PlaylistLoaded playlist) {
                        for (Track track : playlist.getTracks()) {
                            if (!playedIdentifiers.contains(track.getInfo().getIdentifier())) {
                                playedIdentifiers.add(track.getInfo().getIdentifier());
                                fromSeed.add(track);
                            }
                        }
                    } else if (result instanceof TrackLoaded trackLoaded) {
                        if (!playedIdentifiers.contains(trackLoaded.getTrack().getInfo().getIdentifier())) {
                            fromSeed.add(trackLoaded.getTrack());
                        }
                    } else if (result instanceof SearchResult sr && !sr.getTracks().isEmpty()) {
                        if (!"youtube".equals(source)) {
                            // Convert search result to a full mix
                            Track ytVersion = sr.getTracks().getFirst();
                            String ytId = ytVersion.getInfo().getIdentifier();
                            String mixUrl = "https://www.youtube.com/watch?v=" + ytId + "&list=RD" + ytId;
                            var mixResult = link.loadItem(mixUrl).block();
                            if (mixResult instanceof PlaylistLoaded mixPlaylist) {
                                for (Track track : mixPlaylist.getTracks()) {
                                    if (!playedIdentifiers.contains(track.getInfo().getIdentifier())) {
                                        playedIdentifiers.add(track.getInfo().getIdentifier());
                                        fromSeed.add(track);
                                    }
                                }
                            }
                        } else {
                            for (Track track : sr.getTracks()) {
                                if (!playedIdentifiers.contains(track.getInfo().getIdentifier())) {
                                    fromSeed.add(track);
                                }
                            }
                        }
                    }
                }

                synchronized (lock) {
                    combined.addAll(fromSeed);
                    pending[0]--;
                    if (pending[0] == 0 && !finalized[0]) {
                        finalized[0] = true;
                        Collections.shuffle(combined);
                        for (Track track : combined) {
                            offerToQueue(track, true, userName);
                        }
                        if (!combined.isEmpty()) {
                            messageListeners("🌊 Added " + combined.size() + " tracks", MessageType.SUCCESS, userName);
                            nextTrack();
                        } else {
                            messageListeners("🌊 No new tracks found", MessageType.INFO, userName);
                        }
                    }
                }
            }, err -> {
                log.warn("Flow mix load failed for {}: {}", finalQuery, err.getMessage());
                synchronized (lock) {
                    pending[0]--;
                    if (pending[0] == 0 && !finalized[0]) {
                        finalized[0] = true;
                        Collections.shuffle(combined);
                        for (Track track : combined) {
                            offerToQueue(track, true, userName);
                        }
                        if (!combined.isEmpty()) {
                            messageListeners("🌊 Added " + combined.size() + " tracks", MessageType.SUCCESS, userName);
                            nextTrack();
                        } else {
                            messageListeners("🌊 No new tracks found", MessageType.INFO, userName);
                        }
                    }
                }
            });
        }
    }

    // Исправлено: неблокирующий вызов
    public boolean isPaused() {
        var player = link.getCachedPlayer();
        return player != null && player.getPaused();
    }

    public List<Track> getQueueList() {
        return queue.stream()
                .map(qt -> qt.track)
                .collect(Collectors.toList());
    }

    public Track getCurrentTrack() {
        return currentTrack;
    }

    public boolean isFlowMode() {
        return flowMode;
    }

    public void setLastPlayedTrack(Track track) {
        this.lastPlayedTrack = track;
    }

    public BlockingQueue<Track> getQueue() {
        return new LinkedBlockingQueue<>(getQueueList());
    }

    // Исправлено: неблокирующий вызов
    public long getPosition() {
        if (currentTrack == null) return 0;
        var player = link.getCachedPlayer();
        return player != null ? player.getPosition() : 0L;
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