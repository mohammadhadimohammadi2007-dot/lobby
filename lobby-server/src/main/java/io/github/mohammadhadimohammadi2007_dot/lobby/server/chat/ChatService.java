package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat;

import io.github.mohammadhadimohammadi2007_dot.lobby.common.bridge.BridgeMessage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.BridgeService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.pipeline.ChatPipeline;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.render.ComponentTransforms;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.stage.AntiSpamStage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.stage.ChannelStage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.stage.CooldownStage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.stage.DeliverStage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.stage.FilterStage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.stage.FormatStage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.stage.LogStage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.stage.MuteStage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.stage.NormalizeStage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.stage.RenderStage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.storage.PlayerChatSettings;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.Messages;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.signedvelocity.SignedVelocityReceiver;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PlayerMeta;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerChatEvent;
import net.minestom.server.event.player.PlayerCommandEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.event.trait.PlayerEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The lobby chat. Takes over Minecraft chat and runs every message through the {@link ChatPipeline} on one
 * dedicated chat thread, off the tick thread and in order, so messages from one player never overtake each
 * other.
 */
public final class ChatService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatService.class);
    /** Empty lines sent to clear a player's chat. */
    private static final int CLEAR_LINES = 100;

    private final ChatServices services;
    private final ChatAudience audience;
    private final ChatPipeline pipeline;
    private final @Nullable SignedVelocityReceiver signedVelocity;
    private final ExecutorService chatThread = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "lobby-chat");
        thread.setDaemon(true);
        return thread;
    });

    /** @param signedVelocity verdicts from SignedVelocity on the proxy, or {@code null} if that integration is off */
    public ChatService(ChatServices services, @Nullable SignedVelocityReceiver signedVelocity) {
        this.services = services;
        this.audience = new ChatAudience(services);
        this.signedVelocity = signedVelocity;
        this.pipeline = new ChatPipeline(List.of(
                new MuteStage(services),
                new CooldownStage(services),
                new AntiSpamStage(services),
                new NormalizeStage(),
                new FilterStage(services),
                new ChannelStage(services),
                new FormatStage(services),
                new RenderStage(services),
                new DeliverStage(services, audience),
                new LogStage(services)
        ), this::onBlocked);
    }

    public ChatServices services() {
        return services;
    }

    public ChatAudience audience() {
        return audience;
    }

    /** Stage names, for the startup log. */
    public List<String> stageNames() {
        return pipeline.stageNames();
    }

    /** Takes over chat and listens to joins, moves and quits. */
    public void register(EventNode<PlayerEvent> players) {
        if (signedVelocity != null) {
            signedVelocity.register(players);
        }
        players.addListener(PlayerChatEvent.class, event -> {
            if (!services.chat().enabled()) {
                return;
            }
            event.setCancelled(true);
            submit(event.getPlayer(), event.getRawMessage());
        });
        players.addListener(PlayerCommandEvent.class, this::blockCommandsWhileMuted);
        players.addListener(PlayerSpawnEvent.class, event -> {
            if (event.isFirstSpawn()) {
                onJoin(event.getPlayer());
            }
        });
        players.addListener(PlayerMoveEvent.class, event -> {
            if (!event.getNewPosition().samePoint(event.getPlayer().getPosition())) {
                services.players().moved(event.getPlayer().getUuid());
            }
        });
        players.addListener(PlayerDisconnectEvent.class, event -> onQuit(event.getPlayer()));
        services.bridge().onChatRelay(relay -> chatThread.execute(() -> handleRelay(relay)));
        services.permissions().onMetaChange(services.players()::resetCooldowns);
        services.text().viewerTransform(this::serverMessagePersian);
    }

    /**
     * Queues a chat message from {@code player} as if they typed it.
     *
     * @return completes when the message went through the whole pipeline
     */
    public CompletableFuture<Void> submit(Player player, String raw) {
        return CompletableFuture.runAsync(() -> handleLocal(player, raw), chatThread);
    }

    private void handleLocal(Player player, String raw) {
        String text = raw;
        if (signedVelocity != null) {
            SignedVelocityReceiver.Verdict verdict = signedVelocity.nextChatVerdict(player.getUuid());
            if (verdict.kind() == SignedVelocityReceiver.Kind.CANCEL) {
                return; // A proxy plugin already handled it (and told the player why).
            }
            if (verdict.kind() == SignedVelocityReceiver.Kind.MODIFY && verdict.text() != null) {
                text = verdict.text();
            }
        }
        ChatConfig config = services.chat();
        ChatConfig.Channel channel = playerChannel(player.getUuid(), config);
        if (text.length() > 1) {
            ChatConfig.Channel prefixed = config.channelByPrefix(text.charAt(0));
            if (prefixed != null) {
                channel = prefixed;
                text = text.substring(1).strip();
            }
        }
        if (text.isBlank()) {
            return;
        }
        ChatMessage message = new ChatMessage(BridgeService.shortId(), player.getUuid(), player.getUsername(), player,
                text, channel, null);
        pipeline.run(message, config);
    }

    private void handleRelay(BridgeMessage.ChatRelay relay) {
        ChatConfig config = services.chat();
        ChatConfig.Channel channel = config.channels().get(relay.channel().toLowerCase(Locale.ROOT));
        if (channel == null || !channel.network()) {
            return;
        }
        ChatMessage message = new ChatMessage(relay.messageId(), relay.senderId(), relay.senderName(), null,
                relay.message(), channel, relay.originServer());
        message.senderMeta(new PlayerMeta(relay.prefix(), relay.suffix(), relay.primaryGroup(), relay.meta()));
        pipeline.run(message, config);
    }

    /** The channel a player writes in: their choice if it still exists, otherwise the default. */
    public ChatConfig.Channel playerChannel(UUID player, ChatConfig config) {
        String chosen = services.settings().get(player).channel();
        ChatConfig.Channel channel = chosen == null ? null : config.channels().get(chosen);
        return channel != null ? channel : config.channels().get(config.defaultChannel());
    }

    private void onBlocked(ChatMessage message, ChatOutcome.Blocked blocked) {
        if (message.relayed()) {
            return;
        }
        services.log().log(LogStage.entry(message, blocked.label(), blocked.stage() + ": " + blocked.reason(),
                services.config().current().config().server().name()));
        if (message.sender() == null || blocked.stage().equals("mute")) {
            return;
        }
        Component spy = services.text().message(MessageKey.CHAT_SPY, Messages.text("player", message.senderName()),
                Messages.text("reason", blocked.stage() + ": " + blocked.reason()), Messages.text("message", message.original()));
        for (Player staff : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (services.moderation().spying(staff.getUuid()) && !staff.getUuid().equals(message.senderId())) {
                staff.sendMessage(spy);
            }
        }
    }

    private void blockCommandsWhileMuted(PlayerCommandEvent event) {
        ChatConfig config = services.chat();
        if (!config.enabled() || event.isCancelled()) {
            return;
        }
        String command = event.getCommand().strip();
        int space = command.indexOf(' ');
        String name = (space < 0 ? command : command.substring(0, space)).toLowerCase(Locale.ROOT);
        if (!config.mutedBlockedCommands().contains(name)) {
            return;
        }
        Player player = event.getPlayer();
        boolean muted = services.mutes().isMuted(player.getUuid()) || services.moderation().localMuteUntil(player.getUuid()) > 0;
        if (muted) {
            event.setCancelled(true);
            player.sendMessage(services.text().message(MessageKey.CHAT_MUTED_COMMAND, player));
        }
    }

    private void onJoin(Player player) {
        services.players().joined(player.getUuid());
        services.settings().load(player.getUuid());
        ChatConfig config = services.chat();
        if (config.joinMessage()) {
            broadcastAbout(player, services.config().current().messages().template(MessageKey.JOIN_MESSAGE));
        }
        for (ChatConfig.JoinAnnouncement announcement : config.joinAnnouncements()) {
            if (services.has(player, announcement.permission())) {
                broadcastAbout(player, announcement.message());
                break;
            }
        }
    }

    private void onQuit(Player player) {
        services.players().left(player.getUuid());
        services.settings().forget(player.getUuid());
        services.moderation().forget(player.getUuid());
        services.violations().reset(player.getUuid());
        if (services.chat().quitMessage()) {
            broadcastAbout(player, services.config().current().messages().template(MessageKey.QUIT_MESSAGE));
        }
    }

    /** Shows {@code template} (placeholders about {@code subject}) to every online player. */
    private void broadcastAbout(Player subject, String template) {
        for (Player viewer : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (viewer.getUuid().equals(subject.getUuid()) || services.settings().get(viewer.getUuid()).chatVisible()) {
                Component message = services.text().placeholders().render(template, subject, viewer);
                viewer.sendMessage(serverMessagePersian(viewer, message));
            }
        }
    }

    /** Fixes Persian in server texts for viewers who want it, if persian.server-messages is on. */
    private Component serverMessagePersian(Player viewer, Component message) {
        ChatConfig config = services.chat();
        if (!config.persian().serverMessages()) {
            return message;
        }
        PlayerChatSettings settings = services.settings().get(viewer.getUuid());
        return audience.wantsPersian(viewer, settings, services.bridge().capabilities(viewer))
                ? ComponentTransforms.reshapePersian(message)
                : message;
    }

    /** Clears every player's chat. */
    public void clearChat(Component notice) {
        Component blank = Component.text(" ");
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            for (int i = 0; i < CLEAR_LINES; i++) {
                player.sendMessage(blank);
            }
            player.sendMessage(notice);
        }
        chatThread.execute(services.history()::clear);
    }

    /**
     * Deletes one recent message: everyone's chat is cleared and the other recent messages are sent again.
     *
     * @return completes with true if a message with this id was found
     */
    public CompletableFuture<Boolean> deleteMessage(String id) {
        return CompletableFuture.supplyAsync(() -> {
            if (!services.history().remove(id)) {
                return false;
            }
            Component blank = Component.text(" ");
            List<ChatMessage> remaining = services.history().messages();
            for (Player viewer : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                for (int i = 0; i < CLEAR_LINES; i++) {
                    viewer.sendMessage(blank);
                }
                for (ChatMessage message : remaining) {
                    if (audience.canSee(viewer, message)) {
                        viewer.sendMessage(message.rendered().variant(audience.variant(viewer, message)));
                    }
                }
            }
            return true;
        }, chatThread);
    }

    /** Runs {@code task} on the chat thread (for tests and staff tools that touch history). */
    public CompletableFuture<Void> onChatThread(Runnable task) {
        return CompletableFuture.runAsync(task, chatThread);
    }

    /** Waits for queued messages and stops the chat thread. */
    public void shutdown() {
        chatThread.shutdown();
        try {
            if (!chatThread.awaitTermination(5, TimeUnit.SECONDS)) {
                LOGGER.warn("Chat thread did not finish in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        services.log().close();
    }
}
