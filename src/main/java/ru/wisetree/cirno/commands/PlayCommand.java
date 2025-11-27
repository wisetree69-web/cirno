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
        return "9️⃣ Add a track to the queue! You can't stop the strongest!";
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String query = event.getOption("url", OptionMapping::getAsString);
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
            event.getHook().sendMessage("Baka! Connection failed: " + e.getMessage()).queue();
        }
    }

    private String buildSearchQuery(String query, String sourcePrefix) {
        // 1. Direct link
        if (query.startsWith("http://") || query.startsWith("https://")) {
            return query;
        }

        // 2. Power user: already typed a search prefix
        if (query.startsWith("scsearch:") || query.startsWith("dzsearch:") ||
                query.startsWith("ytsearch:") || query.startsWith("ytmsearch:") ||
                query.startsWith("spsearch:"))  {
            return query;
        }

        // 3. Text search (uses the selected source)
        return sourcePrefix + query;
    }

    private void loadAndPlay(GuildMusicManager musicManager, Link link, SlashCommandInteractionEvent event, String identifier) {
        link.loadItem(identifier).subscribe(loadResult -> {
            switch (loadResult) {
                case TrackLoaded trackLoaded -> {
                    musicManager.getScheduler().enqueue(trackLoaded.getTrack());
                    event.getHook().sendMessage("🧊 Added to the FREEZE queue: **" + trackLoaded.getTrack().getInfo().getTitle() + "**").queue();
                }
                case PlaylistLoaded playlistLoaded -> {
                    List<Track> tracks = playlistLoaded.getTracks();

                    if (tracks.isEmpty()) {
                        event.getHook().sendMessage("Baka! Playlist is empty! 🥶").queue();
                        return;
                    }

                    for (Track track : tracks) {
                        musicManager.getScheduler().enqueue(track);
                    }

                    event.getHook().sendMessage("🧊 Freezing playlist: **" + playlistLoaded.getInfo().getName() + "** (" + tracks.size() + " tracks)").queue();
                }
                case SearchResult searchResult -> {
                    var track = searchResult.getTracks().getFirst();
                    musicManager.getScheduler().enqueue(track);
                    event.getHook().sendMessage("⑨ Found and added: **" + track.getInfo().getTitle() + "**").queue();
                }
                case NoMatches noMatches -> {
                    event.getHook().sendMessage("W-What?! Found nothing!").queue();
                }
                case LoadFailed loadFailed -> {
                    event.getHook().sendMessage("Error! My ice powers failed: " + loadFailed.getException().getMessage()).queue();
                }
                default -> event.getHook().sendMessage("I got a weird result, Baka!").queue();
            }
        });
    }

    @Override
    public List<OptionData> getOptions() {
        var urlOption = new OptionData(OptionType.STRING, "url", "The link or search query to be frozen")
                .setRequired(true);

        var sourceOption = new OptionData(OptionType.STRING, "source", "Where to search (Default: YouTube)")
                .setRequired(false)
                .addChoice("YouTube", "ytsearch:")
                .addChoice("YouTube Music", "ytmsearch:")
                .addChoice("SoundCloud", "scsearch:")
                .addChoice("Deezer", "dzsearch:")
                .addChoice("Spotify", "spsearch:");

        return List.of(urlOption, sourceOption);
    }
}