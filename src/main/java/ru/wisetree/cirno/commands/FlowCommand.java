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
        return "Toggle Flow Mode (The Strongest Ice Radio ⑨)";
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        var guild = event.getGuild();
        if (guild == null) return;

        var scheduler = playerManager.getGuildMusicManager(guild.getIdLong()).getScheduler();

        boolean newState = !scheduler.isFlowMode();
        scheduler.setFlowMode(newState);

        String status = newState ? "ON ✅" : "OFF ❌";
        event.reply("Flow Mode is **" + status + "**! The Ice Radio is ready! 🧊").queue();
    }

    @Override
    public List<OptionData> getOptions() {
        return List.of();
    }
}