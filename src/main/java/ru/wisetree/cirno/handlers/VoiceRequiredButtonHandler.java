package ru.wisetree.cirno.handlers;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ru.wisetree.cirno.services.VoiceChannelService;

public abstract class VoiceRequiredButtonHandler implements IButtonHandler {
    private final VoiceChannelService voiceService;

    protected VoiceRequiredButtonHandler(VoiceChannelService voiceService) {
        this.voiceService = voiceService;
    }

    @Override
    public final void handle(ButtonInteractionEvent event) {
        try {
            voiceService.joinMemberChannel(event.getMember());
            handleButton(event);
        } catch (Exception e) {
            if (!event.isAcknowledged()) {
                event.reply("❌ **Baka!** " + e.getMessage()).setEphemeral(true).queue();
            } else {
                event.getHook().sendMessage("❌ **Baka!** " + e.getMessage()).setEphemeral(true).queue();
            }
        }
    }

    protected abstract void handleButton(ButtonInteractionEvent event);
}