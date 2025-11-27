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
    public String getName() { return "play"; }

    @Override
    public String getDescription() { return "Add a track to the queue (Does not spawn dashboard)"; }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        var guild = event .getGuild();
        var member = event.getMember();
        if (guild == null || member == null) return;

        String query = event.getOption("url", OptionMapping::getAsString);
        if (query == null) return;

        event.deferReply().setEphemeral(true).queue();

        try {
            voiceService.joinMemberChannel(member);

            GuildMusicManager musicManager = playerManager.getGuildMusicManager(guild.getIdLong());
            Link link = playerManager.getClient().getOrCreateLink(guild.getIdLong());

            String sourcePrefix = event.getOption("source", "ytsearch:", OptionMapping::getAsString);
            String search = buildSearchQuery(query, sourcePrefix);

            musicManager.getDashboard().addLog("🔎 /play request: " + query);

            link.loadItem(search).subscribe(result -> handleResult(musicManager, result, event));

        } catch (Exception e) {
            event.getHook().sendMessage("Baka! " + e.getMessage()).queue();
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

    private void handleResult(GuildMusicManager musicManager, Object result, SlashCommandInteractionEvent event) {
        if (result instanceof TrackLoaded tr) {
            musicManager.getScheduler().enqueue(tr.getTrack());
            event.getHook().sendMessage("Added: " + tr.getTrack().getInfo().getTitle()).queue();

        } else if (result instanceof PlaylistLoaded pl) {
            pl.getTracks().forEach(musicManager.getScheduler()::enqueue);
            event.getHook().sendMessage("Added playlist: " + pl.getInfo().getName()).queue();

        } else if (result instanceof SearchResult sr) {
            if (!sr.getTracks().isEmpty()) {
                var track = sr.getTracks().getFirst();
                musicManager.getScheduler().enqueue(track);
                event.getHook().sendMessage("Found: " + track.getInfo().getTitle()).queue();
            } else {
                event.getHook().sendMessage("Nothing found!").queue();
            }

        } else if (result instanceof NoMatches) {
            event.getHook().sendMessage("No matches found!").queue();

        } else if (result instanceof LoadFailed lf) {
            event.getHook().sendMessage("Error: " + lf.getException().getMessage()).queue();
        }
    }

    @Override
    public List<OptionData> getOptions() {
        return List.of(
                new OptionData(OptionType.STRING, "url", "URL or Search Query").setRequired(true),
                new OptionData(OptionType.STRING, "source", "Source (Default: YouTube)").setRequired(false)
                        .addChoice("YouTube", "ytsearch:")
                        .addChoice("YouTube Music", "ytmsearch:")
                        .addChoice("SoundCloud", "scsearch:")
                        .addChoice("Spotify", "spsearch:")
                        .addChoice("Deezer", "dzsearch:")
        );
    }
}