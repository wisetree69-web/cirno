package ru.wisetree.cirno;

import dev.arbjerg.lavalink.client.player.*;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.wisetree.cirno.services.VoiceChannelService;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class BotListener extends ListenerAdapter {
    private static final Logger log = LoggerFactory.getLogger(BotListener.class);
    private final CommandRegistry commandRegistry;
    private final PlayerManager playerManager;
    private final VoiceChannelService voiceService; // <-- Добавили сервис

    public BotListener(CommandRegistry commandRegistry, PlayerManager playerManager, VoiceChannelService voiceService) {
        this.commandRegistry = commandRegistry;
        this.playerManager = playerManager;
        this.voiceService = voiceService;
    }

    // --- ВСПОМОГАТЕЛЬНЫЙ МЕТОД: ПРОВЕРКА И ПОДКЛЮЧЕНИЕ ---
    private boolean ensureVoiceConnection(Member member, IReplyCallback event) {
        try {
            // Эта строчка проверит, в канале ли юзер, и подключит бота
            voiceService.joinMemberChannel(member);
            return true;
        } catch (Exception e) {
            // Если юзер не в канале, отвечаем ошибкой (скрытой)
            if (!event.isAcknowledged()) {
                event.reply("❌ **Baka!** You must be in a voice channel!").setEphemeral(true).queue();
            } else {
                // Если уже ответили (defer), шлем через хук
                event.getHook().sendMessage("❌ **Baka!** You must be in a voice channel!").setEphemeral(true).queue();
            }
            return false;
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String commandName = event.getName();
        var command = commandRegistry.getCommand(commandName);
        if (command != null) command.execute(event);
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        var guild = event.getGuild();
        var member = event.getMember();
        if (guild == null || member == null) return;

        // 1. Выбор трека из поиска (search:uri)
        if (id.startsWith("search:")) {
            // Тут тоже нужна проверка, вдруг юзер вышел пока искал
            if (!ensureVoiceConnection(member, event)) return;
            handleSearchResultSelection(event, id);
            return;
        }

        // 2. Quick Load
        if (id.equals("cmd:quick_load")) {
            // ПРОВЕРКА ВОЙСА ПЕРЕД ОТКРЫТИЕМ МОДАЛКИ
            if (!ensureVoiceConnection(member, event)) return;

            TextInput input = TextInput.create("query", "URL or Query", TextInputStyle.SHORT)
                    .setPlaceholder("Paste link or type song name...")
                    .setRequired(true).build();
            Modal modal = Modal.create("modal:quick", "Quick Load ⚡").addActionRow(input).build();
            event.replyModal(modal).queue();
            return;
        }

        // 3. Deep Search (Меню выбора)
        if (id.equals("cmd:deep_search")) {
            if (!ensureVoiceConnection(member, event)) return;

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
            return;
        }

        // 4. Begone
        if (id.equals("cmd:begone")) {
            event.deferEdit().queue();
            var musicManager = playerManager.getGuildMusicManager(guild.getIdLong());
            musicManager.getScheduler().clearQueue();
            event.getMessage().delete().queue(); // Удаляем дешборд
            guild.getAudioManager().closeAudioConnection();
            return;
        }

        // 5. Управление (Pause, Skip...)
        if (id.startsWith("cmd:")) {
            // Для паузы/скипа тоже желательно быть в войсе, но не критично для краша
            // Но лучше проверить, чтобы левые люди не кликали
            if (!ensureVoiceConnection(member, event)) return;

            event.deferEdit().queue();
            var musicManager = playerManager.getGuildMusicManager(guild.getIdLong());
            var scheduler = musicManager.getScheduler();

            switch (id) {
                case "cmd:pause" -> scheduler.pause(!scheduler.isPaused());
                case "cmd:skip" -> scheduler.skip(1);
                case "cmd:stop" -> scheduler.clearQueue();
                case "cmd:shuffle" -> scheduler.shuffle();
                case "cmd:flow" -> scheduler.setFlowMode(!scheduler.isFlowMode());
            }
        }
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (event.getComponentId().equals("menu:search_source")) {
            // Удаляем меню выбора сервиса, чтобы не висело
            event.getMessage().delete().queue();

            String sourcePrefix = event.getValues().getFirst();

            TextInput input = TextInput.create("query", "Search Query", TextInputStyle.SHORT)
                    .setPlaceholder("What are we looking for?")
                    .setRequired(true).build();

            Modal modal = Modal.create("modal:deep:" + sourcePrefix, "Deep Search 🔎")
                    .addActionRow(input).build();

            event.replyModal(modal).queue();
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        String id = event.getModalId();
        String query = event.getValue("query").getAsString();
        var guild = event.getGuild();
        var member = event.getMember();
        if (guild == null || member == null) return;

        // Финальная проверка войса перед загрузкой
        // (хотя мы проверяли до модалки, лучше перестраховаться)
        try {
            voiceService.joinMemberChannel(member);
        } catch (Exception e) {
            event.reply("❌ You left the voice channel!").setEphemeral(true).queue();
            return;
        }

        var musicManager = playerManager.getGuildMusicManager(guild.getIdLong());
        var dashboard = musicManager.getDashboard();
        var link = playerManager.getClient().getOrCreateLink(guild.getIdLong());

        // --- Quick Load ---
        if (id.equals("modal:quick")) {
            event.deferEdit().queue(); // Просто закрываем модалку, ничего не пишем в чат
            dashboard.addLog("⚡ Quick Load: " + query);

            String search = (query.startsWith("http")) ? query : "ytsearch:" + query;

            link.loadItem(search).subscribe(result -> {
                if (result instanceof TrackLoaded) {
                    TrackLoaded tr = (TrackLoaded) result;
                    musicManager.getScheduler().enqueue(tr.getTrack());
                } else if (result instanceof SearchResult) {
                    SearchResult sr = (SearchResult) result;
                    if (!sr.getTracks().isEmpty()) {
                        musicManager.getScheduler().enqueue(sr.getTracks().getFirst());
                    } else {
                        dashboard.addError("Nothing found!");
                    }
                } else if (result instanceof PlaylistLoaded) {
                    PlaylistLoaded pl = (PlaylistLoaded) result;
                    pl.getTracks().forEach(musicManager.getScheduler()::enqueue);
                    dashboard.addSuccess("Added playlist: " + pl.getInfo().getName());
                } else {
                    dashboard.addError("Nothing found!");
                }
            });
        }

        // --- Deep Search ---
        if (id.startsWith("modal:deep:")) {
            event.deferReply().setEphemeral(true).queue();

            String sourcePrefix = id.substring("modal:deep:".length());
            String search = sourcePrefix + query;

            link.loadItem(search).subscribe(result -> {
                if (result instanceof SearchResult) {
                    SearchResult sr = (SearchResult) result;
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

    private void handleSearchResultSelection(ButtonInteractionEvent event, String id) {
        String uri = id.substring(7);

        // Удаляем меню выбора СРАЗУ
        event.getMessage().delete().queue();

        if (uri.equals("cancel")) {
            // Если отмена - просто удалили и всё, подтверждать не обязательно если сообщение удалено,
            // но для чистоты можно сделать deferReply, но тогда останется висеть "Bot is thinking".
            // Лучший вариант для отмены:
            event.deferEdit().queue(); // Подтвердили нажатие
            // Сообщение удалится строчкой выше
            return;
        }

        // Если выбрали трек
        event.deferReply().setEphemeral(true).queue(); // Говорим "думаю..." (скрыто)

        var manager = playerManager.getGuildMusicManager(event.getGuild().getIdLong());
        var link = playerManager.getClient().getOrCreateLink(event.getGuild().getIdLong());

        link.loadItem(uri).subscribe(res -> {
            if (res instanceof TrackLoaded) {
                TrackLoaded tr = (TrackLoaded) res;
                manager.getScheduler().enqueue(tr.getTrack());

                // Пишем в лог дешборда, а эфемерное сообщение удаляем
                manager.getDashboard().addSuccess("Selected: " + tr.getTrack().getInfo().getTitle());
                event.getHook().deleteOriginal().queue(); // Удаляем "Bot is thinking"
            }
        });
    }

    private String formatTime(long millis) {
        return String.format("%02d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(millis),
                TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis)));
    }
}