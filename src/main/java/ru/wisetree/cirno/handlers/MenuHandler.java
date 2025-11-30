package ru.wisetree.cirno.handlers;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import ru.wisetree.cirno.services.VoiceChannelService;

public class MenuHandler extends VoiceRequiredButtonHandler {

    public MenuHandler(VoiceChannelService voiceService) {
        super(voiceService);
    }

    @Override
    protected void handleButton(ButtonInteractionEvent event) {
        String id = event.getComponentId();

        if (id.equals("cmd:quick_load")) {
            TextInput input = TextInput.create("query", "URL or Query", TextInputStyle.SHORT)
                    .setPlaceholder("Paste link or type song name...")
                    .setRequired(true).build();
            Modal modal = Modal.create("modal:quick", "Quick Load ⚡").addActionRow(input).build();
            event.replyModal(modal).queue();
        } else if (id.equals("cmd:deep_search")) {
            StringSelectMenu menu = StringSelectMenu.create("menu:search_source")
                    .setPlaceholder("Select Music Service")
                    .addOption("YouTube", "ytsearch:")
                    .addOption("YouTube Music", "ytmsearch:")
                    .addOption("Spotify", "spsearch:")
                    .addOption("SoundCloud", "scsearch:")
                    .addOption("Deezer", "dzsearch:")
                    .build();

            event.reply("Choose where to search:").setEphemeral(true)
                    .addActionRow(menu)
                    .queue();
        }
    }

    @Override
    public boolean canHandle(String componentId) {
        return componentId.equals("cmd:quick_load") || componentId.equals("cmd:deep_search");
    }
}
