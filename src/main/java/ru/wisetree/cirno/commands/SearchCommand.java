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
    private static final Color CIRNO_BLUE = new Color(153, 204, 255);

    public SearchCommand(PlayerManager playerManager, VoiceChannelService voiceService) {
        this.playerManager = playerManager;
        this.voiceService = voiceService;
    }

    @Override
    public String getName() { return "search"; }

    @Override
    public String getDescription() { return "⑨ Search for a track and pick with buttons! The strongest way!"; }

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

            link.loadItem("ytsearch:" + query).subscribe(loadResult -> {
                if (loadResult instanceof SearchResult searchResult) {
                    List<Track> tracks = searchResult.getTracks();

                    if (tracks.isEmpty()) {
                        event.getHook().sendMessage("Baka! Found nothing for: **" + query + "**").queue();
                        return;
                    }

                    // 1. Build Embed
                    EmbedBuilder eb = new EmbedBuilder();
                    eb.setTitle("⑨ Search Results: " + query);
                    eb.setColor(CIRNO_BLUE);

                    StringBuilder description = new StringBuilder();
                    List<Button> trackButtons = new ArrayList<>();

                    int limit = Math.min(tracks.size(), 5);
                    for (int i = 0; i < limit; i++) {
                        Track track = tracks.get(i);
                        String title = track.getInfo().getTitle();
                        String uri = track.getInfo().getUri();

                        description.append("**").append(i + 1).append(".** ")
                                .append("[").append(title).append("](").append(uri).append(") ")
                                .append("*by ").append(track.getInfo().getAuthor()).append("* ")
                                .append("`[").append(formatTime(track.getInfo().getLength())).append("]`")
                                .append("\n");

                        trackButtons.add(net.dv8tion.jda.api.components.buttons.Button.primary("search:" + uri, String.valueOf(i + 1)));
                    }

                    // 2. Control Button
                    List<Button> controlButtons = List.of(
                            net.dv8tion.jda.api.components.buttons.Button.danger("search:cancel", "Cancel ❌")
                    );

                    eb.setDescription(description.toString());

                    // 3. Send with TWO ActionRows
                    event.getHook().sendMessageEmbeds(eb.build())
                            .setComponents(
                                    ActionRow.of(trackButtons),
                                    ActionRow.of(controlButtons)
                            )
                            .queue();
                } else {
                    event.getHook().sendMessage("W-What? Search failed!").queue();
                }
            });
        } catch (Exception e) {
            event.getHook().sendMessage("Baka! An error occurred: " + e.getMessage()).queue();
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
        return List.of(new OptionData(OptionType.STRING, "query", "What to search for!").setRequired(true));
    }
}