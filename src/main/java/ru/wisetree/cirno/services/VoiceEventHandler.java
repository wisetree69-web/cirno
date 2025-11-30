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

    // Таймер для отложенного выхода (чтобы не ливать мгновенно при мисклике)
    private final ScheduledExecutorService leaver = Executors.newSingleThreadScheduledExecutor();

    public VoiceEventHandler(PlayerManager playerManager) {
        this.playerManager = playerManager;
    }

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        Guild guild = event.getGuild();

        // Нас интересует канал, который покинули (Channel Left)
        var channelLeft = event.getChannelLeft();

        if (channelLeft == null) return; // Это был просто вход в канал, игнорируем

        // Проверяем: Бот вообще подключен к этому серверу?
        var selfVoiceState = guild.getSelfMember().getVoiceState();
        if (selfVoiceState == null || !selfVoiceState.inAudioChannel()) return;

        // Проверяем: Бот находится именно в том канале, откуда кто-то вышел?
        if (selfVoiceState.getChannel().getIdLong() != channelLeft.getIdLong()) return;

        // Считаем людей (не ботов)
        long humanCount = channelLeft.getMembers().stream()
                .filter(m -> !m.getUser().isBot())
                .count();

        if (humanCount == 0) {
            log.info("Channel empty in guild {}. Scheduling disconnect...", guild.getName());

            // Ждем 30 секунд. Если никто не вернулся — ливаем.
            leaver.schedule(() -> {
                // ПОВТОРНАЯ ПРОВЕРКА (вдруг кто-то зашел за эти 30 сек)
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

        // 1. Чистим музыку и дэшборд
        var musicManager = playerManager.getGuildMusicManager(guild.getIdLong());
        musicManager.destroy();

        // 2. Рвем соединение JDA
        guild.getAudioManager().closeAudioConnection();

        // 3. Рвем соединение Lavalink (уничтожаем плеер на сервере)
        playerManager.getClient().getOrCreateLink(guild.getIdLong()).destroy().subscribe();
    }
}