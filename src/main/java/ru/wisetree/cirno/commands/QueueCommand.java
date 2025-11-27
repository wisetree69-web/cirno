package ru.wisetree.cirno.commands;

import dev.arbjerg.lavalink.client.player.Track;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ru.wisetree.cirno.PlayerManager;

import java.awt.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class QueueCommand implements Command {
    private final PlayerManager playerManager;
    private static final Color CIRNO_BLUE = new Color(153, 204, 255);

    public QueueCommand(PlayerManager playerManager) {
        this.playerManager = playerManager;
    }

    @Override
    public String getName() { return "queue"; }

    @Override
    public String getDescription() { return "Show Cirno's Strongest Queue! ⑨"; }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        var guild = event.getGuild();
        if (guild == null) return;

        var manager = playerManager.getGuildMusicManager(guild.getIdLong());
        var scheduler = manager.getScheduler();

        List<Track> queue = scheduler.getQueueList();
        Track current = scheduler.getCurrentTrack();

        if (current == null && queue.isEmpty()) {
            event.reply("Queue is empty! Like your brain! Baka!").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("⑨ Cirno's FREEZE Queue! 🧊");
        eb.setColor(CIRNO_BLUE);

        if (current != null) {
            eb.addField("Now Playing (The Strongest!):",
                    "[" + current.getInfo().getTitle() + "](" + current.getInfo().getUri() + ")",
                    false);
        }

        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Track track : queue) {
            if (count >= 9) break;
            count++;
            sb.append(count).append(". ")
                    .append(track.getInfo().getTitle())
                    .append(" `[")
                    .append(formatTime(track.getInfo().getLength()))
                    .append("]`\n");
        }

        if (queue.size() > 9) {
            sb.append("... and ").append(queue.size() - 9).append(" more weaklings!");
        }

        if (!sb.isEmpty()) {
            eb.addField("Next up (Don't melt!):", sb.toString(), false);
        } else if (current != null) {
            eb.addField("Next up:", "The queue is empty! Call /flow to summon the ice radio!", false);
        }

        event.replyEmbeds(eb.build()).queue();
    }

    private String formatTime(long millis) {
        return String.format("%02d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(millis),
                TimeUnit.MILLISECONDS.toSeconds(millis) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))
        );
    }

    @Override
    public List<OptionData> getOptions() { return List.of(); }
}