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

        String userName = event.getMember() != null ? event.getMember().getEffectiveName() : "Unknown";

        var musicManager = playerManager.getGuildMusicManager(guild.getIdLong());
        var scheduler = musicManager.getScheduler();
        var dashboard = musicManager.getDashboard();

        event.deferEdit().queue();

        switch (id) {
            case "cmd:pause" -> scheduler.pause(!scheduler.isPaused(), userName);
            case "cmd:skip" -> scheduler.skip(1, userName);
            case "cmd:stop" -> scheduler.clearQueue(userName);
            case "cmd:shuffle" -> scheduler.shuffle(userName);
            case "cmd:flow" -> scheduler.setFlowMode(!scheduler.isFlowMode(), userName);
        }

        musicManager.getDashboard().updateImmediately();
    }
}