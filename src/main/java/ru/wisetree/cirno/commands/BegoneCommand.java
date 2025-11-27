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
        return "I'm leaving! You are too weak for me!";
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        var guild = event.getGuild();
        if (guild == null) return;

        var musicManager = playerManager.getGuildMusicManager(guild.getIdLong());
        musicManager.getScheduler().clearQueue();

        playerManager.getClient().getOrCreateLink(guild.getIdLong())
                .createOrUpdatePlayer()
                .setTrack(null)
                .subscribe();

        guild.getAudioManager().closeAudioConnection();

        event.reply("I'm leaving! You're too weak to handle the Strongest! ⑨").queue();
    }

    @Override
    public List<OptionData> getOptions() {
        return List.of();
    }
}