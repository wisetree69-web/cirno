package ru.wisetree.cirno;

import dev.arbjerg.lavalink.client.player.PlaylistLoaded;
import dev.arbjerg.lavalink.client.player.SearchResult;
import dev.arbjerg.lavalink.client.player.Track;
import dev.arbjerg.lavalink.client.player.TrackLoaded;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.components.label.Label; // НОВЫЙ ИМПОРТ
import net.dv8tion.jda.api.modals.Modal;

import org.jetbrains.annotations.NotNull;
import ru.wisetree.cirno.handlers.*;
import ru.wisetree.cirno.services.VoiceChannelService;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class BotListener extends ListenerAdapter {
    private final CommandRegistry commandRegistry;
    private final PlayerManager playerManager;
    private final VoiceChannelService voiceService;
    private final ButtonHandlerRegistry buttonHandlerRegistry = new ButtonHandlerRegistry();

    public BotListener(CommandRegistry commandRegistry, PlayerManager playerManager, VoiceChannelService voiceService) {
        this.commandRegistry = commandRegistry;
        this.playerManager = playerManager;
        this.voiceService = voiceService;
        buttonHandlerRegistry.register(new PlayerControlHandler(playerManager, voiceService));
        buttonHandlerRegistry.register(new SearchSelectionHandler(playerManager, voiceService));
        buttonHandlerRegistry.register(new MenuHandler(voiceService));
        buttonHandlerRegistry.register(new BegoneHandler(playerManager));
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String commandName = event.getName();
        var command = commandRegistry.getCommand(commandName);
        if (command != null) command.execute(event);
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        buttonHandlerRegistry.handle(event);
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (event.getComponentId().equals("menu:search_source")) {
            event.getMessage().delete().queue();

            String sourcePrefix = event.getValues().getFirst();

            // ИСПРАВЛЕНО: Создаем только само поле ввода (без заголовка)
            TextInput input = TextInput.create("query", TextInputStyle.SHORT)
                    .setPlaceholder("What are we looking for?")
                    .setRequired(true).build();

            // ИСПРАВЛЕНО: Оборачиваем поле ввода в Label (который содержит заголовок)
            // Примечание: если Label.of подчеркивается красным, замени на Label.create("Search Query", input)
            Modal modal = Modal.create("modal:deep:" + sourcePrefix, "Deep Search 🔎")
                    .addComponents(Label.of("Search Query", input)).build();

            event.replyModal(modal).queue();
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        String id = event.getModalId();
        String query = Objects.requireNonNull(event.getValue("query")).getAsString();
        var guild = event.getGuild();
        var member = event.getMember();
        if (guild == null || member == null) return;

        try {
            voiceService.joinMemberChannel(member);
        } catch (Exception e) {
            event.reply("❌ You left the voice channel!").setEphemeral(true).queue();
            return;
        }

        var musicManager = playerManager.getGuildMusicManager(guild.getIdLong());
        var dashboard = musicManager.getDashboard();
        var link = playerManager.getClient().getOrCreateLink(guild.getIdLong());

        if (id.equals("modal:quick")) {
            event.deferEdit().queue();
            dashboard.addLog("⚡ Quick Load: " + query);

            String search = (query.startsWith("http")) ? query : "ytsearch:" + query;

            link.loadItem(search).subscribe(result -> {
                switch (result) {
                    case TrackLoaded tr -> musicManager.getScheduler().enqueue(tr.getTrack());
                    case SearchResult sr -> {
                        if (!sr.getTracks().isEmpty()) {
                            musicManager.getScheduler().enqueue(sr.getTracks().getFirst());
                        } else {
                            dashboard.addError("Nothing found!");
                        }
                    }
                    case PlaylistLoaded pl -> {
                        pl.getTracks().forEach(musicManager.getScheduler()::enqueue);
                        dashboard.addSuccess("Added playlist: " + pl.getInfo().getName());
                    }
                    case null, default -> dashboard.addError("Nothing found!");
                }
            });
        }

        if (id.startsWith("modal:deep:")) {
            event.deferReply().setEphemeral(true).queue();

            String sourcePrefix = id.substring("modal:deep:".length());
            String search = sourcePrefix + query;

            link.loadItem(search).subscribe(result -> {
                if (result instanceof SearchResult sr) {
                    if (!sr.getTracks().isEmpty()) {
                        sendSearchButtons(event, sr.getTracks(), query);
                    } else {
                        event.getHook().sendMessage("No results found!").queue();
                    }
                } else {
                    event.getHook().sendMessage("No results found!").queue();
                }
            });
        }
    }

    private void sendSearchButtons(ModalInteractionEvent event, List<Track> tracks, String query) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("🔎 Results for: " + query);
        eb.setColor(new Color(153, 204, 255));

        StringBuilder desc = new StringBuilder();
        List<Button> buttons = new ArrayList<>();

        int limit = Math.min(tracks.size(), 5);
        for (int i = 0; i < limit; i++) {
            Track t = tracks.get(i);
            String uri = t.getInfo().getUri();
            assert uri != null;
            if (uri.length() > 80) continue;

            desc.append("**").append(i + 1).append(".** ")
                    .append("[").append(t.getInfo().getTitle()).append("](").append(uri).append(") ")
                    .append("`").append(formatTime(t.getInfo().getLength())).append("`\n");

            buttons.add(Button.primary("search:" + uri, String.valueOf(i + 1)));
        }

        eb.setDescription(desc.toString());
        List<Button> controls = List.of(Button.danger("search:cancel", "Cancel ❌"));

        event.getHook().sendMessageEmbeds(eb.build())
                .setComponents(ActionRow.of(buttons), ActionRow.of(controls))
                .queue();
    }

    private String formatTime(long millis) {
        return String.format("%02d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(millis),
                TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis)));
    }
}