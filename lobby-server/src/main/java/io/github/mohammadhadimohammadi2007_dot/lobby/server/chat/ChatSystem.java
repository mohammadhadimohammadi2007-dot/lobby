package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.BridgeService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.command.ChannelCommand;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.command.ChatCommand;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.command.IgnoreCommand;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.command.PrivateMessageCommands;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.command.SlowmodeCommand;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.filter.ChatFilter;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.render.ChatRenderer;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.spam.ViolationTracker;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.storage.ChatLog;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.storage.DatabaseChatLog;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.storage.DatabaseSettingsStore;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.storage.FileChatLog;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.storage.FileSettingsStore;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.storage.SettingsStore;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigSnapshot;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.database.DatabasePool;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.litebans.MuteService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.signedvelocity.SignedVelocityReceiver;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import net.minestom.server.command.CommandManager;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.trait.PlayerEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Builds the chat system from chat.yml at startup: storage (database or files), the filter, the pipeline,
 * commands and broadcasts. Keeps {@code LobbyServer} small.
 */
public final class ChatSystem {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatSystem.class);

    /** What the lobby already started that chat needs. {@code database} and {@code signedVelocity} may be null. */
    public record Dependencies(ConfigManager config, LobbyText text, PermissionService permissions, MuteService mutes,
                               BridgeService bridge, @Nullable DatabasePool database,
                               @Nullable SignedVelocityReceiver signedVelocity) {
    }

    private final ConfigManager config;
    private final ChatService service;
    private final ChatBroadcaster broadcaster;
    private final String storageDescription;

    private ChatSystem(ConfigManager config, ChatService service, ChatBroadcaster broadcaster, String storageDescription) {
        this.config = config;
        this.service = service;
        this.broadcaster = broadcaster;
        this.storageDescription = storageDescription;
    }

    /** Opens storage and builds every part. Runs before the port opens, so blocking calls are fine here. */
    public static ChatSystem start(Dependencies deps) {
        ConfigManager config = deps.config();
        ChatConfig chat = config.current().chat();
        SettingsStore store = settingsStore(deps, chat.storage());
        ChatLog log = chatLog(deps, chat.storage());
        ChatServices services = new ChatServices(config, deps.text(), deps.permissions(), deps.mutes(),
                new ChatModeration(), new ChatSettingsService(store), new ChatPlayerTracker(),
                new AtomicReference<>(loadFilter(config, chat)), new ViolationTracker(), deps.bridge(),
                new ChatRenderer(deps.text(), () -> config.current().chat(), () -> config.current().messages()),
                new ChatHistory(), log);
        ChatService service = new ChatService(services, deps.signedVelocity());
        ChatBroadcaster broadcaster = new ChatBroadcaster(() -> config.current().chat(), deps.text(), deps.bridge());
        return new ChatSystem(config, service, broadcaster,
                "settings: " + store.describe() + ", log: " + log.describe());
    }

    /** Takes over chat, registers the commands and starts broadcasts. */
    public void register(EventNode<PlayerEvent> players, CommandManager commands) {
        service.register(players);
        ChatServices services = service.services();
        commands.register(new ChatCommand(service));
        commands.register(new ChannelCommand(services));
        commands.register(new IgnoreCommand(services));
        commands.register(new SlowmodeCommand(services));
        if (services.chat().privateMessagesEnabled()) {
            new PrivateMessageCommands(services).commands().forEach(commands::register);
        }
        broadcaster.restart();
    }

    /** Applies chat.yml changes after {@code /lobby reload}: new filter lists, cooldowns and broadcasts. */
    public void reload(ConfigSnapshot reloaded) {
        ChatServices services = service.services();
        services.filter().set(loadFilter(config, reloaded.chat()));
        services.players().resetAllCooldowns();
        broadcaster.restart();
    }

    /** For the startup summary. */
    public String describe() {
        ChatConfig chat = config.current().chat();
        if (!chat.enabled()) {
            return "off (vanilla chat)";
        }
        return "on, " + service.services().filter().get().entryCount() + " filter entries, " + storageDescription;
    }

    /** Every player's saved settings (chat choices and lobby choices such as player visibility). */
    public ChatSettingsService settings() {
        return service().services().settings();
    }

    public ChatService service() {
        return service;
    }

    /** Stops broadcasts, finishes queued messages and flushes the chat log. */
    public void shutdown() {
        broadcaster.stop();
        service.shutdown();
    }

    private static ChatFilter loadFilter(ConfigManager config, ChatConfig chat) {
        Set<String> domains = chat.filter().allowedDomains();
        try {
            return ChatFilter.load(config.dataDir(), domains);
        } catch (IOException e) {
            LOGGER.error("Could not read the chat filter lists in {}/: {}. Only link and IP checks are active.",
                    ChatFilter.FOLDER, e.getMessage());
            return ChatFilter.withoutWordLists(domains);
        }
    }

    private static SettingsStore settingsStore(Dependencies deps, ChatConfig.Storage storage) {
        if (deps.database() != null) {
            try {
                return DatabaseSettingsStore.create(deps.database(), storage.tablePrefix());
            } catch (SQLException e) {
                LOGGER.error("Could not create the chat settings table, using files instead: {}", e.getMessage());
            }
        }
        return new FileSettingsStore(deps.config().dataDir());
    }

    private static ChatLog chatLog(Dependencies deps, ChatConfig.Storage storage) {
        if (deps.database() != null && storage.logToDatabase()) {
            try {
                return DatabaseChatLog.create(deps.database(), storage.tablePrefix(), storage.retentionDays());
            } catch (SQLException e) {
                LOGGER.error("Could not create the chat log table: {}", e.getMessage());
            }
        }
        if (deps.database() == null && storage.logToFile()) {
            return FileChatLog.create(deps.config().dataDir(), storage.retentionDays());
        }
        return ChatLog.NONE;
    }
}
