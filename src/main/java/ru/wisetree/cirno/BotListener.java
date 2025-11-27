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
            event.reply("Command not found").setEphemeral(true).queue();
            return;
        }
        command.execute(event);
    }

    // --- ОБРАБОТКА КНОПОК ---
    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();

        // Проверяем, что это кнопка поиска
        if (buttonId.startsWith("search:")) {
            String trackUrl = buttonId.substring(7); // Отрезаем "search:"

            // Обработка кнопки "Отмена"
            if (trackUrl.equals("cancel")) {
                event.getMessage().delete().queue(); // Удаляем сообщение с кнопками
                return;
            }

            var guild = event.getGuild();
            if (guild == null) return;

            // Отвечаем пользователю (чтобы кнопка перестала крутиться)
            event.deferReply().queue();

            var link = playerManager.getClient().getOrCreateLink(guild.getIdLong());
            var musicManager = playerManager.getGuildMusicManager(guild.getIdLong());

            // Грузим трек по ссылке, которая была зашита в кнопке
            link.loadItem(trackUrl).subscribe(loadResult -> {
                if (loadResult instanceof TrackLoaded trackLoaded) {
                    musicManager.getScheduler().enqueue(trackLoaded.getTrack());

                    event.getHook().sendMessage("✅ Выбрано: " + trackLoaded.getTrack().getInfo().getTitle()).queue();

                    // Удаляем сообщение с кнопками, чтобы нельзя было нажать второй раз
                    event.getMessage().delete().queue();
                } else {
                    event.getHook().sendMessage("Ошибка загрузки трека.").setEphemeral(true).queue();
                }
            });
        }
    }
}