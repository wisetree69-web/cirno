package ru.wisetree.cirno.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ru.wisetree.cirno.PlayerManager;

import java.util.List;

public class ShuffleCommand implements Command {
    private final PlayerManager playerManager;

    public ShuffleCommand(PlayerManager playerManager) {
        this.playerManager = playerManager;
    }

    @Override
    public String getName() { return "shuffle"; }

    @Override
    public String getDescription() { return "Перемешать очередь"; }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        var guild = event.getGuild();
        if (guild == null) return;

        var manager = playerManager.getGuildMusicManager(guild.getIdLong());
        manager.getScheduler().shuffle();

        event.reply("Очередь перемешана 🔀").queue();
    }

    @Override
    public List<OptionData> getOptions() { return List.of(); }
}