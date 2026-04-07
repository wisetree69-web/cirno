package ru.wisetree.cirno.commands;

import dev.arbjerg.lavalink.client.Link;
import dev.arbjerg.lavalink.client.player.SearchResult;
import dev.arbjerg.lavalink.client.player.Track;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

// НОВЫЕ ИМПОРТЫ JDA
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;

import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ru.wisetree.cirno.PlayerManager;
import ru.wisetree.cirno.services.VoiceChannelService;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SearchCommand implements Command {
    private final PlayerManager playerManager;
    private final VoiceChannelService voiceService;
    private static final Color CIRNO_BLUE = new Color(153, 204, 255);

    public SearchCommand(PlayerManager playerManager, VoiceChannelService voiceService) {
        this.playerManager = playerManager;
        this.voiceService = voiceService;
    }

    @Override
    public String getName() { return "search"; }

    @Override
    public String getDescription() { return "Search for a track and pick with buttons."; }

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

            String sourcePrefix = event.getOption("source", "ytsearch:", OptionMapping::getAsString);
            String search = buildSearchQuery(query, sourcePrefix);

            link.loadItem(search).subscribe(loadResult -> {
                if (loadResult instanceof SearchResult searchResult) {
                    List<Track> tracks = searchResult.getTracks();

                    if (tracks.isEmpty()) {
                        event.getHook().sendMessage("Baka! Found nothing for: **" + query + "**").queue();
                        return;
                    }

                    EmbedBuilder eb = new EmbedBuilder();
                    eb.setTitle("Search Results: " + query);
                    eb.setColor(CIRNO_BLUE);
                    eb.setFooter("Select a track or click Cancel");

                    StringBuilder description = new StringBuilder();
                    List<Button> trackButtons = new ArrayList<>();

                    int limit = Math.min(tracks.size(), 5);
                    for (int i = 0; i < limit; i++) {
                        Track track = tracks.get(i);
                        String title = track.getInfo().getTitle();
                        String uri = track.getInfo().getUri();

                        assert uri != null;
                        if (uri.length() > 80) continue;

                        description.append("**").append(i + 1).append(".** ")
                                .append("[").append(title).append("](").append(uri).append(") ")
                                .append("`[").append(formatTime(track.getInfo().getLength())).append("]`")
                                .append("\n");

                        trackButtons.add(Button.primary("search:" + uri, String.valueOf(i + 1)));
                    }

                    List<Button> controlButtons = List.of(
                            Button.danger("search:cancel", "Cancel ❌")
                    );

                    eb.setDescription(description.toString());

                    event.getHook().sendMessageEmbeds(eb.build())
                            .setComponents(
                                    ActionRow.of(trackButtons),
                                    ActionRow.of(controlButtons)
                            )
                            .queue();
                } else {
                    event.getHook().sendMessage("W-What? This isn't a search result! (Try providing a direct link in /play)").queue();
                }
            });
        } catch (Exception e) {
            event.getHook().sendMessage("Baka! An error occurred: " + e.getMessage()).queue();
        }
    }

    private String buildSearchQuery(String query, String sourcePrefix) {
        if (query.startsWith("http://") || query.startsWith("https://")) return query;
        if (query.startsWith("ytsearch:") || query.startsWith("ytmsearch:") ||
                query.startsWith("scsearch:") || query.startsWith("spsearch:") ||
                query.startsWith("dzsearch:")) {
            return query;
        }
        return sourcePrefix + query;
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
        return List.of(
                new OptionData(OptionType.STRING, "query", "What to search for!").setRequired(true),
                new OptionData(OptionType.STRING, "source", "Source (Default: YouTube)").setRequired(false)
                        .addChoice("YouTube", "ytsearch:")
                        .addChoice("YouTube Music", "ytmsearch:")
                        .addChoice("SoundCloud", "scsearch:")
                        .addChoice("Spotify", "spsearch:")
                        .addChoice("Deezer", "dzsearch:")
        );
    }
}