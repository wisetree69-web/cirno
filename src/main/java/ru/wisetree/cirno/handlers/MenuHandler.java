package ru.wisetree.cirno.handlers;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.components.label.Label; // НОВЫЙ ИМПОРТ
import net.dv8tion.jda.api.modals.Modal;

import ru.wisetree.cirno.services.VoiceChannelService;

public class MenuHandler extends VoiceRequiredButtonHandler {

    public MenuHandler(VoiceChannelService voiceService) {
        super(voiceService);
    }

    @Override
    protected void handleButton(ButtonInteractionEvent event) {
        String id = event.getComponentId();

        if (id.equals("cmd:quick_load")) {
            // ИСПРАВЛЕНО: Создаем только само поле ввода
            TextInput input = TextInput.create("query", TextInputStyle.SHORT)
                    .setPlaceholder("Paste link or type song name...")
                    .setRequired(true).build();

            // ИСПРАВЛЕНО: Оборачиваем в Label
            // Примечание: если Label.of подчеркивается красным, замени на Label.create("URL or Query", input)
            Modal modal = Modal.create("modal:quick", "Quick Load ⚡")
                    .addComponents(Label.of("URL or Query", input)).build();

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

            // ИСПРАВЛЕНО: Используем setComponents(ActionRow.of(menu)) вместо addActionRow
            event.reply("Choose where to search:").setEphemeral(true)
                    .setComponents(ActionRow.of(menu))
                    .queue();
        }
    }

    @Override
    public boolean canHandle(String componentId) {
        return componentId.equals("cmd:quick_load") || componentId.equals("cmd:deep_search");
    }
}