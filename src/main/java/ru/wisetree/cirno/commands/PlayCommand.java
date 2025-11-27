package ru.wisetree.cirno.commands;

import dev.arbjerg.lavalink.client.Link;
import dev.arbjerg.lavalink.client.player.*;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ru.wisetree.cirno.GuildMusicManager;
import ru.wisetree.cirno.PlayerManager;
import ru.wisetree.cirno.services.VoiceChannelService;

import java.util.List;

public class PlayCommand implements Command {
    private final PlayerManager playerManager;
    private final VoiceChannelService voiceService;

    public PlayCommand(PlayerManager playerManager, VoiceChannelService voiceService) {
        this.playerManager = playerManager;
        this.voiceService = voiceService;
    }

    @Override
    public String getName() {
        return "play";
    }

    @Override
    public String getDescription() {
        return "Играть музыку";
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String query = event.getOption("url", OptionMapping::getAsString);
        // Получаем выбор пользователя или берем YouTube по умолчанию
        String sourcePrefix = event.getOption("source", "ytsearch:", OptionMapping::getAsString);

        var guild = event.getGuild();
        var member = event.getMember();

        if (guild == null || member == null || query == null) return;

        event.deferReply().queue();

        try {
            voiceService.joinMemberChannel(member);

            GuildMusicManager musicManager = playerManager.getGuildMusicManager(guild.getIdLong());
            Link link = playerManager.getClient().getOrCreateLink(guild.getIdLong());

            String search = buildSearchQuery(query, sourcePrefix);

            loadAndPlay(musicManager, link, event, search);

        } catch (Exception e) {
            event.getHook().sendMessage("Ошибка: " + e.getMessage()).queue();
        }
    }

    private String buildSearchQuery(String query, String sourcePrefix) {
        // 1. Если это ссылка -> игнорируем выбор источника, Lavalink сам разберется
        if (query.startsWith("http://") || query.startsWith("https://")) {
            return query;
        }

        // 2. Если пользователь сам написал префикс (power user) -> оставляем как есть
        if (query.startsWith("scsearch:") || query.startsWith("dzsearch:") ||
                query.startsWith("ytsearch:") || query.startsWith("ytmsearch:") ||
                query.startsWith("spsearch:"))  {
            return query;
        }

        // 3. Иначе склеиваем префикс из меню и запрос
        return sourcePrefix + query;
    }

    private void loadAndPlay(GuildMusicManager musicManager, Link link, SlashCommandInteractionEvent event, String identifier) {
        link.loadItem(identifier).subscribe(loadResult -> {
            switch (loadResult) {
                case TrackLoaded trackLoaded -> {
                    musicManager.getScheduler().enqueue(trackLoaded.getTrack());
                    event.getHook().sendMessage("Добавлено: " + trackLoaded.getTrack().getInfo().getTitle()).queue();
                }
                case PlaylistLoaded playlistLoaded -> {
                    List<Track> tracks = playlistLoaded.getTracks();

                    if (tracks.isEmpty()) {
                        event.getHook().sendMessage("Плейлист пуст!").queue();
                        return;
                    }

                    // 1. Добавляем все треки в очередь планировщика
                    // Важно: TrackScheduler.enqueue сам разберется, играть или ждать
                    for (Track track : tracks) {
                        musicManager.getScheduler().enqueue(track);
                    }

                    event.getHook().sendMessage("Добавлен плейлист: " + playlistLoaded.getInfo().getName() +
                            " (" + tracks.size() + " треков)").queue();
                }
                case SearchResult searchResult -> {
                    var track = searchResult.getTracks().getFirst();
                    musicManager.getScheduler().enqueue(track);
                    event.getHook().sendMessage("Найдено: " + track.getInfo().getTitle()).queue();
                }
                case NoMatches noMatches -> {
                    event.getHook().sendMessage("Ничего не найдено").queue();
                }
                case LoadFailed loadFailed -> {
                    event.getHook().sendMessage("Ошибка загрузки: " + loadFailed.getException().getMessage()).queue();
                }
                default -> throw new IllegalStateException("Unexpected value: " + loadResult);
            }
        });
    }

    @Override
    public List<OptionData> getOptions() {
        // Опция запроса
        var urlOption = new OptionData(OptionType.STRING, "url", "Ссылка или название трека")
                .setRequired(true);

        // Опция источника (выпадающий список)
        var sourceOption = new OptionData(OptionType.STRING, "source", "Где искать (по умолчанию YouTube)")
                .setRequired(false) // Необязательно
                .addChoice("YouTube", "ytsearch:")
                .addChoice("YouTube Music", "ytmsearch:")
                .addChoice("SoundCloud", "scsearch:")
                .addChoice("Deezer", "dzsearch:")
                .addChoice("Spotify", "spsearch:");

        return List.of(urlOption, sourceOption);
    }
}