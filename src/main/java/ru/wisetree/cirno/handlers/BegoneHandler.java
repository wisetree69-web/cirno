package ru.wisetree.cirno.handlers;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ru.wisetree.cirno.PlayerManager;

public class BegoneHandler implements  IButtonHandler {
    final PlayerManager playerManager;

    public BegoneHandler(PlayerManager playerManager) {
        this.playerManager = playerManager;
    }

    @Override
    public boolean canHandle(String componentId) {
        return componentId.equals("cmd:begone");
    }

    @Override
    public void handle(ButtonInteractionEvent event) {
        var guild = event.getGuild();
        if (guild == null) return;

        String userName = event.getMember() != null ? event.getMember().getEffectiveName() : "Unknown";

        event.deferEdit().queue();
        var musicManager = playerManager.getGuildMusicManager(guild.getIdLong());
        musicManager.getScheduler().clearQueue();
        musicManager.getDashboard().addLog("👋 Dismissed", userName);
        musicManager.getDashboard().deleteMessage();

        guild.getAudioManager().closeAudioConnection();
    }
}
