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

    public QueueCommand(PlayerManager playerManager) {
        this.playerManager = playerManager;
    }

    @Override
    public String getName() { return "queue"; }

    @Override
    public String getDescription() { return "Показать текущую очередь"; }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        var guild = event.getGuild();
        if (guild == null) return;

        var manager = playerManager.getGuildMusicManager(guild.getIdLong());
        var scheduler = manager.getScheduler();

        List<Track> queue = scheduler.getQueueList();
        Track current = scheduler.getCurrentTrack();

        if (current == null && queue.isEmpty()) {
            event.reply("Очередь пуста.").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Музыкальная очередь");
        eb.setColor(Color.CYAN);

        // Текущий трек
        if (current != null) {
            eb.addField("Сейчас играет:",
                    "[" + current.getInfo().getTitle() + "](" + current.getInfo().getUri() + ")",
                    false);
        }

        // Список (показываем первые 10)
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Track track : queue) {
            if (count >= 10) break;
            count++;
            sb.append(count).append(". ")
                    .append(track.getInfo().getTitle())
                    .append(" `[")
                    .append(formatTime(track.getInfo().getLength()))
                    .append("]`\n");
        }

        if (queue.size() > 10) {
            sb.append("... и еще ").append(queue.size() - 10).append(" треков");
        }

        if (sb.length() > 0) {
            eb.addField("Далее:", sb.toString(), false);
        } else if (current != null) {
            eb.addField("Далее:", "Пусто (включите /flow для авто-радио)", false);
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