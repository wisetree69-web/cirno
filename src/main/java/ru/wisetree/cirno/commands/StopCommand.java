package ru.wisetree.cirno.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ru.wisetree.cirno.PlayerManager;

import java.util.List;

public class StopCommand implements Command {
    private final PlayerManager playerManager;

    public StopCommand(PlayerManager playerManager) {
        this.playerManager = playerManager;
    }

    @Override
    public String getName() { return "stop"; }

    @Override
    public String getDescription() { return "Stop playback and clear the whole queue!"; }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        var guild = event.getGuild();
        if (guild == null) return;

        var manager = playerManager.getGuildMusicManager(guild.getIdLong());
        manager.getScheduler().clearQueue();

        playerManager.getClient().getOrCreateLink(guild.getIdLong())
                .createOrUpdatePlayer()
                .setTrack(null)
                .subscribe();

        event.reply("I'm done! The queue is annihilated! ❄️").queue();
    }

    @Override
    public List<OptionData> getOptions() { return List.of(); }
}