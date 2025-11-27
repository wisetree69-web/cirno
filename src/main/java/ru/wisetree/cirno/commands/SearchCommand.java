package ru.wisetree.cirno.commands;

import dev.arbjerg.lavalink.client.Link;
import dev.arbjerg.lavalink.client.player.SearchResult;
import dev.arbjerg.lavalink.client.player.Track;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ru.wisetree.cirno.PlayerManager;
import ru.wisetree.cirno.services.VoiceChannelService;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SearchCommand implements Command {
    private final PlayerManager playerManager;
    private final VoiceChannelService voiceService;

    public SearchCommand(PlayerManager playerManager, VoiceChannelService voiceService) {
        this.playerManager = playerManager;
        this.voiceService = voiceService;
    }

    @Override
    public String getName() { return "search"; }

    @Override
    public String getDescription() { return "Найти трек (выбор кнопками)"; }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String query = event.getOption("query", OptionMapping::getAsString);
        var guild = event.getGuild();
        var member = event.getMember();
        if (guild == null || member == null || query == null) return;

        event.deferReply().setEphemeral(true).queue();

        try {
            voiceService.joinMemberChannel(member);
            Link link = playerManager.getClient().getOrCreateLink(guild.getIdLong());

            // Ищем на YouTube (по умолчанию)
            link.loadItem("ytsearch:" + query).subscribe(loadResult -> {
                if (loadResult instanceof SearchResult searchResult) {
                    List<Track> tracks = searchResult.getTracks();

                    if (tracks.isEmpty()) {
                        event.getHook().sendMessage("Ничего не найдено.").queue();
                        return;
                    }

                    // 1. Строим Embed (Текстовый список)
                    EmbedBuilder eb = new EmbedBuilder();
                    eb.setTitle("Результаты поиска: " + query);
                    eb.setColor(Color.ORANGE);

                    StringBuilder description = new StringBuilder();
                    List<Button> trackButtons = new ArrayList<>(); // Кнопки для треков (макс 5)

                    int limit = Math.min(tracks.size(), 5);
                    for (int i = 0; i < limit; i++) {
                        Track track = tracks.get(i);
                        // Получаем Title и URI
                        String title = track.getInfo().getTitle();
                        String uri = track.getInfo().getUri();

                        // Форматируем строку: "1. Title (Author) [03:20]"
                        description.append("**").append(i + 1).append(".** ")
                                .append("[").append(title).append("](").append(uri).append(") ")
                                .append("*by ").append(track.getInfo().getAuthor()).append("* ")
                                .append("`[").append(formatTime(track.getInfo().getLength())).append("]`")
                                .append("\n");

                        // Создаем кнопку. ID = "search:<URL>" (макс 100 символов)
                        trackButtons.add(Button.primary("search:" + uri, String.valueOf(i + 1)));
                    }

                    // 2. Добавляем кнопку отмены в ОТДЕЛЬНУЮ строку
                    List<Button> controlButtons = List.of(
                            Button.danger("search:cancel", "Отмена")
                    );

                    eb.setDescription(description.toString());

                    // 3. Отправляем сообщение с ДВУМЯ ActionRow
                    event.getHook().sendMessageEmbeds(eb.build())
                            .setComponents(
                                    ActionRow.of(trackButtons),   // 1-я строка: 5 кнопок выбора
                                    ActionRow.of(controlButtons)  // 2-я строка: 1 кнопка отмены
                            )
                            .queue();
                } else {
                    event.getHook().sendMessage("Поиск не дал результатов.").queue();
                }
            });
        } catch (Exception e) {
            event.getHook().sendMessage("Ошибка: " + e.getMessage()).queue();
        }
    }

    private String formatTime(long millis) {
        return String.format("%02d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(millis),
                TimeUnit.MILLISECONDS.toSeconds(millis) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))
        );
    }

    @Override
    public List<OptionData> getOptions() {
        return List.of(new OptionData(OptionType.STRING, "query", "Что искать").setRequired(true));
    }
}