package ru.wisetree.cirno.handlers;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ru.wisetree.cirno.PlayerManager;
import ru.wisetree.cirno.services.VoiceChannelService;

public class PlayerControlHandler extends VoiceRequiredButtonHandler {
    private final PlayerManager playerManager;

    public PlayerControlHandler(PlayerManager playerManager, VoiceChannelService voiceService) {
        super(voiceService);
        this.playerManager = playerManager;
    }

    @Override
    public boolean canHandle(String componentId) {
        if (!componentId.startsWith("cmd:")) {
            return false;
        }
        return isCompatibleCommand(componentId);
    }

    private boolean isCompatibleCommand(String command) {
        return !command.equals("cmd:quick_load") &&
                !command.equals("cmd:deep_search") &&
                !command.equals("cmd:begone");
    }

    @Override
    protected void handleButton(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        var guild = event.getGuild();
        assert guild != null;

        var musicManager = playerManager.getGuildMusicManager(guild.getIdLong());
        var scheduler = musicManager.getScheduler();

        event.deferEdit().queue();

        switch (id) {
            case "cmd:pause" -> scheduler.pause(!scheduler.isPaused());
            case "cmd:skip" -> scheduler.skip(1);
            case "cmd:stop" -> scheduler.clearQueue();
            case "cmd:shuffle" -> scheduler.shuffle();
            case "cmd:flow" -> scheduler.setFlowMode(!scheduler.isFlowMode());
        }

        // 🔥 ФИЧА: Мгновенное обновление после действия
        musicManager.getDashboard().updateImmediately();
    }
}