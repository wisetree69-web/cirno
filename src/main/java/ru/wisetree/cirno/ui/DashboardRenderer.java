package ru.wisetree.cirno.ui;

import dev.arbjerg.lavalink.client.player.Track;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.emoji.Emoji;
// НОВЫЕ ИМПОРТЫ JDA
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
    private static final String CIRNO_IMG_IDLE = "https://media.tenor.com/iPKa5SFvaKAAAAAi/touhou-cirno.gif";

    public MessageEditData render(TrackScheduler scheduler, List<String> logEntries) {
        Track current = scheduler.getCurrentTrack();
        boolean isPaused = scheduler.isPaused();

        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(CIRNO_COLOR);
        eb.setThumbnail(CIRNO_IMG_IDLE);

        if (current == null) {
            eb.setTitle("⑨ Cirno Music: IDLE");
            eb.setDescription("❄️ Queue is empty! Use **Quick Load** or **Deep Search** below!");
        } else {
            String statusIcon = isPaused ? "II (FROZEN)" : "▶ (PLAYING)";
            eb.setTitle("⑨ " + statusIcon + ": " + current.getInfo().getTitle(), current.getInfo().getUri());
            eb.setDescription(buildProgressBar(scheduler));
            eb.addField("Author", current.getInfo().getAuthor(), true);
            eb.addField("Source", current.getInfo().getSourceName(), true);
            eb.addField("Flow Mode", scheduler.isFlowMode() ? "✅ ON" : "❌ OFF", true);
        }

        StringBuilder logBuilder = new StringBuilder();
        logBuilder.append("```diff\n");
        int start = Math.max(0, logEntries.size() - 8);
        for (int i = start; i < logEntries.size(); i++) {
            logBuilder.append(logEntries.get(i)).append("\n");
        }
        if (logEntries.isEmpty()) logBuilder.append("- No recent activity -");
        logBuilder.append("```");
        eb.addField("🧊 System Log", logBuilder.toString(), false);

        StringBuilder queueBuilder = new StringBuilder();
        List<Track> queue = scheduler.getQueueList();
        if (!queue.isEmpty()) {
            int limit = Math.min(3, queue.size());
            for (int i = 0; i < limit; i++) {
                queueBuilder.append("`").append(i + 1).append(".` ")
                        .append(trim(queue.get(i).getInfo().getTitle())).append("\n");
            }
            if (queue.size() > 3) queueBuilder.append("*...and ").append(queue.size() - 3).append(" more*");
        } else {
            queueBuilder.append("*Empty... like my head!*");
        }
        eb.addField("Next Up", queueBuilder.toString(), false);
        eb.setFooter("CirnoBot v9.9.9 | The Strongest UI");

        return new MessageEditBuilder()
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
        int totalBars = 15;
        long progress = (duration > 0) ? (position * totalBars) / duration : 0;
        StringBuilder sb = new StringBuilder("`").append(formatTime(position)).append(" `");
        for (int i = 0; i < totalBars; i++) {
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

    private String trim(String text) {
        return (text.length() > 30) ? text.substring(0, 30 - 1) + "…" : text;
    }
}