package ru.wisetree.cirno.handlers;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

public interface IButtonHandler {
    boolean canHandle(String componentId);
    void handle(ButtonInteractionEvent event);
}
