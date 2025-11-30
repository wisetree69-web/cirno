package ru.wisetree.cirno.handlers;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

import java.util.ArrayList;
import java.util.List;

public class ButtonHandlerRegistry {
    private final List<IButtonHandler> handlers = new ArrayList<>();

    public void register(IButtonHandler handler) {
        handlers.add(handler);
    }

    public void handle(ButtonInteractionEvent event) {
        for (IButtonHandler handler : handlers) {
            if (handler.canHandle(event.getComponentId())) {
                handler.handle(event);
                return;
            }
        }
    }
}
