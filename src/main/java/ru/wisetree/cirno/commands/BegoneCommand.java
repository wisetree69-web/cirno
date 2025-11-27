package ru.wisetree.cirno.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ru.wisetree.cirno.PlayerManager;

import java.util.List;

public class BegoneCommand implements Command {
    private final PlayerManager playerManager;

    public BegoneCommand(PlayerManager playerManager) {
        this.playerManager = playerManager;
    }

    @Override
    public String getName() {
        return "begone";
    }

    @Override
    public String getDescription() {
        return "Остановить музыку, очистить очередь и выйти";
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        var guild = event.getGuild();
        if (guild == null) return;

        // 1. Чистим очередь (чтобы ничего не заиграло потом)
        var musicManager = playerManager.getGuildMusicManager(guild.getIdLong());
        musicManager.getScheduler().clearQueue();

        // 2. Останавливаем плеер (ставим трек в null)
        // Это гарантированно работает, так как метод setTrack мы уже использовали
        var link = playerManager.getClient().getOrCreateLink(guild.getIdLong());
        link.createOrUpdatePlayer()
                .setTrack(null)
                .subscribe();

        // 3. Отключаемся от голосового канала в Discord
        // Это самое главное действие для "выхода"
        guild.getAudioManager().closeAudioConnection();

        event.reply("Ну всё, я пошел. Бывай! 👋").queue();
    }

    @Override
    public List<OptionData> getOptions() {
        return List.of();
    }
}