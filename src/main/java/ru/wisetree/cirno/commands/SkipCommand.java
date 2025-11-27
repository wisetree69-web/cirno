package ru.wisetree.cirno.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ru.wisetree.cirno.PlayerManager;

import java.util.List;

public class SkipCommand implements Command {
    private final PlayerManager playerManager;

    public SkipCommand(PlayerManager playerManager) {
        this.playerManager = playerManager;
    }

    @Override
    public String getName() { return "skip"; }

    @Override
    public String getDescription() { return "Skip one or more tracks (You are too fast, Baka!)"; }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        var guild = event.getGuild();
        if (guild == null) return;

        int amount = event.getOption("amount", 1, OptionMapping::getAsInt);
        if (amount < 1) amount = 1;

        var manager = playerManager.getGuildMusicManager(guild.getIdLong());
        manager.getScheduler().skip(amount);

        event.reply("Skipped " + amount + " tracks! Baka! ⑨").queue();
    }

    @Override
    public List<OptionData> getOptions() {
        return List.of(new OptionData(OptionType.INTEGER, "amount", "How many tracks to skip").setRequired(false));
    }
}