package ru.wisetree.cirno.handlers;

import dev.arbjerg.lavalink.client.player.TrackLoaded;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ru.wisetree.cirno.PlayerManager;
import ru.wisetree.cirno.services.VoiceChannelService;

import java.util.Objects;

public class SearchSelectionHandler extends  VoiceRequiredButtonHandler {
    final PlayerManager playerManager;

    public SearchSelectionHandler(PlayerManager playerManager, VoiceChannelService voiceService) {
        super(voiceService);
        this.playerManager = playerManager;
    }

    @Override
    protected void handleButton(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        String uri = id.substring(7);

        event.getMessage().delete().queue();

        if (uri.equals("cancel")) {
            event.deferEdit().queue();
            return;
        }

        event.deferReply().setEphemeral(true).queue();

        var guild = event.getGuild();
        var userName = guild != null && event.getMember() != null ? event.getMember().getEffectiveName() : null;

        var manager = playerManager.getGuildMusicManager(Objects.requireNonNull(event.getGuild()).getIdLong());
        var link = playerManager.getClient().getOrCreateLink(event.getGuild().getIdLong());

        link.loadItem(uri).subscribe(res -> {
            if (res instanceof TrackLoaded tr) {
                manager.getScheduler().enqueue(tr.getTrack(), userName);
                event.getHook().deleteOriginal().queue();
            }
        });
    }

    @Override
    public boolean canHandle(String componentId) {
        return componentId.startsWith("search:");
    }
}
