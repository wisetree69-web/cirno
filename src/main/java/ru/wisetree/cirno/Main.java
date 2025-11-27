package ru.wisetree.cirno;

import dev.arbjerg.lavalink.libraries.jda.JDAVoiceUpdateListener;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.wisetree.cirno.commands.*;
import ru.wisetree.cirno.services.JdaVoiceChannelService;

import java.util.Base64;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws InterruptedException {
        String token = System.getenv("BOT_TOKEN");
        if (token == null) throw new IllegalArgumentException("Token not found! (Baka!)");

        // 1. Get Bot ID
        String botIdStr = new String(Base64.getDecoder().decode(token.split("\\.")[0]));
        long botId = Long.parseLong(botIdStr);

        // 2. Initialize Managers
        var playerManager = new PlayerManager(botId);
        var voiceService = new JdaVoiceChannelService();

        var registry = new CommandRegistry();

        // 3. Registering ALL Cirno's Strongest Commands!
        registry.register(
                new PlayCommand(playerManager, voiceService),
                new SkipCommand(playerManager),
                new FlowCommand(playerManager),
                new BegoneCommand(playerManager),
                new PauseCommand(playerManager),
                new StopCommand(playerManager),
                new ShuffleCommand(playerManager),
                new QueueCommand(playerManager),
                new SearchCommand(playerManager, voiceService)
        );

        var listener = new BotListener(registry, playerManager);

        // 4. Build and Start JDA
        var jda = JDABuilder
                .createDefault(token)
                .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_VOICE_STATES)
                .enableCache(CacheFlag.VOICE_STATE)
                .setVoiceDispatchInterceptor(new JDAVoiceUpdateListener(playerManager.getClient()))
                .addEventListeners(listener)
                .build();

        jda.awaitReady();
        log.info("CirnoBot is launched! The Strongest! ⑨");

        jda.updateCommands().addCommands(registry.getCommandData()).queue();
    }
}