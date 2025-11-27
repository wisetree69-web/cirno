package ru.wisetree.cirno;

import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import ru.wisetree.cirno.commands.Command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommandRegistry {
    private final Map<String, Command> commands = new HashMap<>();

    public void register(Command... commandsToRegister) {
        for (Command command : commandsToRegister) {
            commands.put(command.getName(), command);
        }
    }

    public Command getCommand(String name) {
        return commands.get(name);
    }

    public List<CommandData> getCommandData() {
        List<CommandData> dataList = new ArrayList<>();
        for (Command command : commands.values()) {
            SlashCommandData data = Commands.slash(command.getName(), command.getDescription());
            data.addOptions(command.getOptions());
            dataList.add(data);
        }
        return dataList;
    }
}