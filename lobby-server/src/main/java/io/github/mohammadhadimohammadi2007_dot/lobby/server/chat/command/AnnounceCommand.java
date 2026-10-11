package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.command;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatPermissions;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatServices;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.render.ComponentTransforms;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.render.PlayerTextParser;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.storage.ChatLog;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.Messages;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentStringArray;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.server.play.SystemChatPacket;
import net.minestom.server.utils.PacketSendingUtils;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@code /announce <message>}: one message to every player on this lobby server (all instances), in the
 * {@code announce} format of messages.yml, also to players who hid the chat. Works from the console.
 *
 * <p>Staff may style the text with colors and decorations; click, hover and every other MiniMessage tag
 * need {@code lobby.command.announce.advanced} (the console always has it).
 */
public final class AnnounceCommand extends Command {

    /** Who the chat log names when the console announces. */
    static final UUID CONSOLE_ID = new UUID(0, 0);
    /** The chat log's channel for announcements. */
    static final String LOG_CHANNEL = "announce";
    private static final PlayerTextParser STAFF_STYLES = new PlayerTextParser(ChatConfig.SAFE_PLAYER_TAGS);

    private final ChatServices services;

    public AnnounceCommand(ChatServices services) {
        super("announce");
        this.services = services;
        setCondition(ChatCommandSupport.require(services, ChatPermissions.COMMAND_ANNOUNCE));
        setDefaultExecutor((sender, context) -> sender.sendMessage(services.text().message(MessageKey.ANNOUNCE_USAGE, sender)));
        ArgumentStringArray words = ArgumentType.StringArray("message");
        addSyntax((sender, context) -> announce(sender, String.join(" ", context.get(words)).strip()), words);
    }

    /** Sends the announcement. Tick thread (a command). */
    void announce(CommandSender sender, String raw) {
        if (raw.isEmpty()) {
            sender.sendMessage(services.text().message(MessageKey.ANNOUNCE_USAGE, sender));
            return;
        }
        boolean advanced = services.permissions().hasPermission(sender, ChatPermissions.ANNOUNCE_ADVANCED);
        Component body = advanced ? MiniMessage.miniMessage().deserialize(raw) : STAFF_STYLES.parse(raw, true);
        String senderName = sender instanceof Player player ? player.getUsername() : "Console";

        Component modern = format(MessageKey.ANNOUNCE, body, senderName);
        boolean ownLegacy = !services.config().current().messages().template(MessageKey.ANNOUNCE_LEGACY).isBlank();
        Component legacy = ComponentTransforms.downsampleColors(
                ownLegacy ? format(MessageKey.ANNOUNCE_LEGACY, body, senderName) : modern);

        ChatConfig.Announce settings = services.chat().announce();
        List<Player> modernViewers = new ArrayList<>();
        List<Player> legacyViewers = new ArrayList<>();
        for (Player viewer : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            (services.bridge().capabilities(viewer).legacy() ? legacyViewers : modernViewers).add(viewer);
        }
        // One packet per client generation, encoded once.
        PacketSendingUtils.sendGroupedPacket(modernViewers, new SystemChatPacket(modern, false));
        PacketSendingUtils.sendGroupedPacket(legacyViewers, new SystemChatPacket(legacy, false));
        if (!(sender instanceof Player)) {
            sender.sendMessage(modern);
        }
        Sound sound = sound(settings.sound());
        Title title = settings.title() ? Title.title(services.text().message(MessageKey.ANNOUNCE_TITLE, (Player) null),
                body, Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(settings.titleSeconds()),
                        Duration.ofMillis(500))) : null;
        for (Player viewer : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (sound != null) {
                viewer.playSound(sound);
            }
            if (title != null) {
                viewer.showTitle(title);
            }
        }
        UUID senderId = sender instanceof Player player ? player.getUuid() : CONSOLE_ID;
        services.log().log(new ChatLog.Entry(System.currentTimeMillis(),
                services.config().current().config().server().name(), LOG_CHANNEL, senderId, senderName, raw, "",
                "announce", ""));
    }

    private Component format(MessageKey key, Component body, String senderName) {
        return services.text().message(key, (Player) null, Placeholder.component("message", body),
                Messages.text("sender", senderName));
    }

    private static @Nullable Sound sound(String name) {
        if (name.isBlank()) {
            return null;
        }
        try {
            return Sound.sound(Key.key(name), Sound.Source.MASTER, 1f, 1f);
        } catch (InvalidKeyException e) {
            return null;
        }
    }
}
