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
    public String getDescription() { return "Пропустить треки"; }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        var guild = event.getGuild();
        if (guild == null) return;

        // Получаем аргумент (по умолчанию 1)
        int amount = event.getOption("amount", 1, OptionMapping::getAsInt);
        if (amount < 1) amount = 1;

        var manager = playerManager.getGuildMusicManager(guild.getIdLong());
        manager.getScheduler().skip(amount);

        event.reply("Пропущено треков: " + amount + " ⏭️").queue();
    }

    @Override
    public List<OptionData> getOptions() {
        return List.of(new OptionData(OptionType.INTEGER, "amount", "Сколько треков пропустить").setRequired(false));
    }
}