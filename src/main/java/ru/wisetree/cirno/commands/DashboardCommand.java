package ru.wisetree.cirno.commands;

import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ru.wisetree.cirno.PlayerManager;

import java.util.List;

public class DashboardCommand implements Command {
    private final PlayerManager playerManager;

    public DashboardCommand(PlayerManager playerManager) {
        this.playerManager = playerManager;
    }

    @Override
    public String getName() { return "dashboard"; }

    @Override
    public String getDescription() { return "Summon or reset the Cirno Music Dashboard."; }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        var guild = event.getGuild();
        if (guild == null) return;

        if (!(event.getChannel() instanceof GuildMessageChannel channel)) {
            event.reply("Baka! I can only make a dashboard in text channels!").setEphemeral(true).queue();
            return;
        }

        event.deferReply().setEphemeral(true).queue(hook -> {
            var musicManager = playerManager.getGuildMusicManager(guild.getIdLong());
            String userName = event.getMember() != null ? event.getMember().getEffectiveName() : event.getUser().getName();
            musicManager.getDashboard().create(channel, userName);
            hook.deleteOriginal().queue();
        });
    }

    @Override
    public List<OptionData> getOptions() {
        return List.of();
    }
}