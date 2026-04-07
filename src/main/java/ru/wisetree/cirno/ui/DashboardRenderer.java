package ru.wisetree.cirno.ui;

import dev.arbjerg.lavalink.client.player.Track;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import ru.wisetree.cirno.TrackScheduler;

import java.awt.Color;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DashboardRenderer {

    private static final Color CIRNO_COLOR = new Color(153, 204, 255);
    private static final String CIRNO_IMG = "https://media.tenor.com/Mw3fsm5V-loAAAAi/cirno-fumo.gif";
    private static final int PROGRESS_BARS = 18;
    private static final int QUEUE_TRUNCATE = 45;
    private static final int LOG_TRUNCATE = 40;

    public MessageEditData render(TrackScheduler scheduler, List<String> logEntries) {
        Track current = scheduler.getCurrentTrack();
        boolean isPaused = scheduler.isPaused();

        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(CIRNO_COLOR);

        if (current == null) {
            eb.setThumbnail(CIRNO_IMG);
            eb.setTitle("Cirno Music — IDLE");
            eb.setDescription("Queue is empty. Use **Quick Load** or **Search** below.");
        } else {
            String displayTitle = TrackScheduler.getDisplayTitle(current);

            // Truncate title to single line
            if (displayTitle.length() > 55) {
                displayTitle = displayTitle.substring(0, 52) + "…";
            }

            // Always use the same thumbnail GIF for consistency
            eb.setThumbnail(CIRNO_IMG);

            eb.setTitle(displayTitle, current.getInfo().getUri());
            eb.setDescription(buildProgressBar(scheduler));

            // Three inline fields: Author | Source | Status
            String author = current.getInfo().getAuthor();
            String source = current.getInfo().getSourceName();
            String status = isPaused ? "Paused" : "Playing";
            eb.addField("Author", author.length() > 18 ? author.substring(0, 17) + "…" : author, true);
            eb.addField("Source", source, true);
            eb.addField("Status", status, true);
        }

        // System Log — show last 12 entries, truncated to fit code block
        StringBuilder logBuilder = new StringBuilder();
        logBuilder.append("```\n");
        int start = Math.max(0, logEntries.size() - 12);
        for (int i = start; i < logEntries.size(); i++) {
            String entry = logEntries.get(i);
            logBuilder.append(trim(entry, 42)).append("\n");
        }
        if (logEntries.isEmpty()) logBuilder.append("No recent activity.");
        logBuilder.append("```");
        eb.addField("System Log", logBuilder.toString(), false);

        // Queue — enumerated, compact
        StringBuilder queueBuilder = new StringBuilder();
        List<Track> queue = scheduler.getQueueList();
        if (!queue.isEmpty()) {
            int limit = Math.min(5, queue.size());
            for (int i = 0; i < limit; i++) {
                String trackTitle = TrackScheduler.getDisplayTitle(queue.get(i));
                queueBuilder.append("`").append(i + 1).append(".` ")
                        .append(trim(trackTitle, QUEUE_TRUNCATE)).append("\n");
            }
            if (queue.size() > 5) {
                queueBuilder.append("...and ").append(queue.size() - 5).append(" more.");
            }
        } else {
            queueBuilder.append("*Empty*");
        }
        eb.addField("Next Up", queueBuilder.toString(), false);

        return new MessageEditBuilder()
                .setContent(null)
                .setEmbeds(eb.build())
                .setComponents(createButtons(scheduler))
                .build();
    }

    private List<ActionRow> createButtons(TrackScheduler scheduler) {
        boolean isPaused = scheduler.isPaused();

        Button playPause = Button.primary("cmd:pause", isPaused ? "Resume" : "Pause")
                .withEmoji(Emoji.fromUnicode("⏯️"));
        Button skip = Button.secondary("cmd:skip", "Skip")
                .withEmoji(Emoji.fromUnicode("⏭️"));
        Button shuffle = Button.secondary("cmd:shuffle", "Shuffle")
                .withEmoji(Emoji.fromUnicode("🔀"));
        Button flow = scheduler.isFlowMode()
                ? Button.success("cmd:flow", "Flow: ON").withEmoji(Emoji.fromUnicode("🌊"))
                : Button.secondary("cmd:flow", "Flow: OFF").withEmoji(Emoji.fromUnicode("🌊"));

        Button quickLoad = Button.success("cmd:quick_load", "URL / Quick")
                .withEmoji(Emoji.fromUnicode("⚡"));

        Button deepSearch = Button.primary("cmd:deep_search", "Search")
                .withEmoji(Emoji.fromUnicode("🔎"));

        Button stop = Button.secondary("cmd:stop", "Stop")
                .withEmoji(Emoji.fromUnicode("⏹️"));

        Button begone = Button.danger("cmd:begone", "Begone!")
                .withEmoji(Emoji.fromUnicode("🚪"));

        return List.of(
                ActionRow.of(playPause, skip, shuffle, flow),
                ActionRow.of(quickLoad, deepSearch, stop, begone)
        );
    }

    private String buildProgressBar(TrackScheduler scheduler) {
        Track track = scheduler.getCurrentTrack();
        if (track == null) return "";
        long duration = track.getInfo().getLength();
        long position = scheduler.getPosition();
        if (duration == Long.MAX_VALUE) return "🔴 **LIVE STREAM**";
        long progress = (duration > 0) ? (position * PROGRESS_BARS) / duration : 0;
        StringBuilder sb = new StringBuilder("`").append(formatTime(position)).append(" `");
        for (int i = 0; i < PROGRESS_BARS; i++) {
            if (i == progress) sb.append("🔘"); else sb.append("▬");
        }
        sb.append("` ").append(formatTime(duration)).append("`");
        return sb.toString();
    }

    private String formatTime(long millis) {
        return String.format("%02d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(millis),
                TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis)));
    }

    private String trim(String text, int maxLen) {
        return (text.length() > maxLen) ? text.substring(0, maxLen - 1) + "…" : text;
    }
}