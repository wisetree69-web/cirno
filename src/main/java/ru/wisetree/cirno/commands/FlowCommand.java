package ru.wisetree.cirno.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ru.wisetree.cirno.PlayerManager;

import java.util.List;

public class FlowCommand implements Command {
    private final PlayerManager playerManager;

    public FlowCommand(PlayerManager playerManager) {
        this.playerManager = playerManager;
    }

    @Override
    public String getName() {
        return "flow";
    }

    @Override
    public String getDescription() {
        return "Переключить режим авто-воспроизведения (Flow)";
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        var guild = event.getGuild();
        if (guild == null) return;

        var scheduler = playerManager.getGuildMusicManager(guild.getIdLong()).getScheduler();

        // Переключаем состояние
        boolean newState = !scheduler.isFlowMode();
        scheduler.setFlowMode(newState);

        String status = newState ? "ВКЛЮЧЕН ✅" : "ВЫКЛЮЧЕН ❌";
        event.reply("Режим Flow " + status).queue();

        // Если включили Flow и ничего не играет, но есть история -> можно попробовать запустить сразу
        // Но для простоты оставим логику "заработает после следующего трека"
    }

    @Override
    public List<OptionData> getOptions() {
        return List.of();
    }
}