package ru.wisetree.cirno.services;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.wisetree.cirno.PlayerManager;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class VoiceEventHandler extends ListenerAdapter {
    private static final Logger log = LoggerFactory.getLogger(VoiceEventHandler.class);
    private final PlayerManager playerManager;

    // Timer for delayed disconnect (grace period for misclicks)
    private final ScheduledExecutorService leaver = Executors.newSingleThreadScheduledExecutor();

    public VoiceEventHandler(PlayerManager playerManager) {
        this.playerManager = playerManager;
    }

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        Guild guild = event.getGuild();

        // Track which channel was left
        var channelLeft = event.getChannelLeft();
        var channelJoined = event.getChannelJoined();

        // If the bot itself was dragged to another channel by an admin
        var member = event.getMember();
        if (member != null && member == guild.getSelfMember() && channelJoined != null) {
            log.info("Bot moved to voice channel: {}", channelJoined.getName());
            return;
        }

        if (channelLeft == null) return;

        // Ignore non-bot members leaving

        // Check if bot is connected to this specific channel
        var selfVoiceState = guild.getSelfMember().getVoiceState();
        if (selfVoiceState == null || !selfVoiceState.inAudioChannel()) return;

        // Bot must be in the same channel that was left
        if (selfVoiceState.getChannel().getIdLong() != channelLeft.getIdLong()) return;

        // Count humans (not bots)
        long humanCount = channelLeft.getMembers().stream()
                .filter(m -> !m.getUser().isBot())
                .count();

        if (humanCount == 0) {
            log.info("Channel empty in guild {}. Scheduling disconnect...", guild.getName());

            // Wait 30 seconds. If nobody returns — disconnect.
            leaver.schedule(() -> {
                // Re-check (maybe someone rejoined during the 30s window)
                long currentHumans = channelLeft.getMembers().stream()
                        .filter(m -> !m.getUser().isBot())
                        .count();

                if (currentHumans == 0) {
                    disconnectAndClean(guild);
                } else {
                    log.info("Someone rejoined guild {}, disconnect cancelled.", guild.getName());
                }
            }, 30, TimeUnit.SECONDS);
        }
    }

    private void disconnectAndClean(Guild guild) {
        log.info("Leaving guild {} due to inactivity.", guild.getName());

        // 1. Clean up music and dashboard
        var musicManager = playerManager.getGuildMusicManager(guild.getIdLong());
        musicManager.destroy();

        // 2. Close JDA audio connection
        guild.getAudioManager().closeAudioConnection();

        // 3. Destroy Lavalink link (kill player on server)
        playerManager.getClient().getOrCreateLink(guild.getIdLong()).destroy().subscribe();
    }
}