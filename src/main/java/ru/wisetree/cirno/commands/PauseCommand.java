package ru.wisetree.cirno.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ru.wisetree.cirno.PlayerManager;

import java.util.List;

public class PauseCommand implements Command {
    private final PlayerManager playerManager;

    public PauseCommand(PlayerManager playerManager) {
        this.playerManager = playerManager;
    }

    @Override
    public String getName() {
        return "pause";
    }

    @Override
    public String getDescription() {
        return "Freeze or unfreeze the music!";
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        var guild = event.getGuild();
        if (guild == null) return;

        var manager = playerManager.getGuildMusicManager(guild.getIdLong());
        boolean isPaused = manager.getScheduler().isPaused();

        manager.getScheduler().pause(!isPaused);

        String status = !isPaused ? "FROZEN 🥶" : "UNFROZEN! Time to go! ▶️";
        event.reply("Music is now " + status).queue();
    }

    @Override
    public List<OptionData> getOptions() {
        return List.of();
    }
}