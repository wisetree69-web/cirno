package ru.wisetree.cirno;

import dev.arbjerg.lavalink.client.player.TrackLoaded;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BotListener extends ListenerAdapter {
    private static final Logger log = LoggerFactory.getLogger(BotListener.class);
    private final CommandRegistry commandRegistry;
    private final PlayerManager playerManager;

    public BotListener(CommandRegistry commandRegistry, PlayerManager playerManager) {
        this.commandRegistry = commandRegistry;
        this.playerManager = playerManager;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String commandName = event.getName();
        var command = commandRegistry.getCommand(commandName);
        if (command == null) {
            event.reply("W-What?! Command not found! ⑨").setEphemeral(true).queue();
            log.warn("Command not found: {}", commandName);
            return;
        }
        log.info("Executing command: {} from user: {}", commandName, event.getUser().getName());
        command.execute(event);
    }

    // --- ОБРАБОТКА КНОПОК ---
    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();

        // Проверяем, что это кнопка поиска
        if (buttonId.startsWith("search:")) {
            String trackUrl = buttonId.substring(7);

            // Обработка кнопки "Отмена"
            if (trackUrl.equals("cancel")) {
                event.getMessage().delete().queue();
                return;
            }

            var guild = event.getGuild();
            if (guild == null) return;

            event.deferReply().queue();

            var link = playerManager.getClient().getOrCreateLink(guild.getIdLong());
            var musicManager = playerManager.getGuildMusicManager(guild.getIdLong());

            link.loadItem(trackUrl).subscribe(loadResult -> {
                if (loadResult instanceof TrackLoaded trackLoaded) {
                    musicManager.getScheduler().enqueue(trackLoaded.getTrack());

                    event.getHook().sendMessage("🧊 Track selected! Added to the FREEZE queue!").queue();

                    event.getMessage().delete().queue();
                } else {
                    event.getHook().sendMessage("B-Baka! Could not load that track.").setEphemeral(true).queue();
                }
            });
        }
    }
}