package io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.signedvelocity;

import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerCommandEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerPluginMessageEvent;
import net.minestom.server.event.trait.PlayerEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Receives chat and command verdicts from the SignedVelocity plugin on the Velocity proxy, so a message or
 * command that a proxy plugin cancelled or changed (for example a LiteBans mute on the proxy) is cancelled
 * or changed here too. Since Minecraft 1.19.1 a proxy cannot cancel signed chat itself; it tells the
 * backend instead.
 *
 * <p>Written from SignedVelocity's message format only (no code from SignedVelocity, which is GPL-3.0):
 * channel {@code signedvelocity:main}, then UTF strings: player UUID, {@code CHAT_RESULT} or
 * {@code COMMAND_RESULT}, {@code ALLOWED}/{@code MODIFY}/{@code CANCEL}, and for {@code MODIFY} the new text.
 * The proxy sends the verdict right before the chat or command packet, so it has normally arrived when the
 * chat or command is handled.
 */
public final class SignedVelocityReceiver {

    private static final Logger LOGGER = LoggerFactory.getLogger(SignedVelocityReceiver.class);
    static final String CHANNEL = "signedvelocity:main";
    private static final String CHAT = "CHAT_RESULT";
    private static final String COMMAND = "COMMAND_RESULT";
    /** Verdicts kept per player and kind; more than this means they are not being used, so old ones are dropped. */
    private static final int MAX_QUEUED = 32;

    /** What the proxy decided. {@code text} is the new message for {@link Kind#MODIFY}. */
    public record Verdict(Kind kind, @Nullable String text) {
        public static final Verdict ALLOW = new Verdict(Kind.ALLOWED, null);
    }

    /** The three possible decisions. */
    public enum Kind { ALLOWED, MODIFY, CANCEL }

    private final Map<UUID, Queue<Verdict>> chat = new ConcurrentHashMap<>();
    private final Map<UUID, Queue<Verdict>> commands = new ConcurrentHashMap<>();
    private volatile boolean warnedMissing;

    /** Starts receiving verdicts and applies command verdicts. */
    public void register(EventNode<PlayerEvent> node) {
        node.addListener(PlayerPluginMessageEvent.class, event -> {
            if (CHANNEL.equals(event.getIdentifier())) {
                receive(event.getMessage());
            }
        });
        node.addListener(PlayerCommandEvent.class, event -> {
            Verdict verdict = next(commands, event.getPlayer().getUuid());
            switch (verdict.kind()) {
                case CANCEL -> event.setCancelled(true);
                case MODIFY -> event.setCommand(verdict.text());
                case ALLOWED -> {
                }
            }
        });
        node.addListener(PlayerDisconnectEvent.class, event -> {
            chat.remove(event.getPlayer().getUuid());
            commands.remove(event.getPlayer().getUuid());
        });
    }

    /** The verdict for the player's next chat message; {@link Verdict#ALLOW} if the proxy sent none. */
    public Verdict nextChatVerdict(UUID player) {
        return next(chat, player);
    }

    /** Parses one verdict message. Package-private for tests. */
    void receive(byte[] data) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            UUID player = UUID.fromString(in.readUTF());
            String source = in.readUTF();
            String result = in.readUTF();
            Verdict verdict = switch (result) {
                case "ALLOWED" -> Verdict.ALLOW;
                case "CANCEL" -> new Verdict(Kind.CANCEL, null);
                case "MODIFY" -> new Verdict(Kind.MODIFY, in.readUTF());
                default -> throw new IOException("unknown result " + result);
            };
            Map<UUID, Queue<Verdict>> target = switch (source) {
                case CHAT -> chat;
                case COMMAND -> commands;
                default -> throw new IOException("unknown source " + source);
            };
            Queue<Verdict> queue = target.computeIfAbsent(player, id -> new ConcurrentLinkedQueue<>());
            queue.add(verdict);
            while (queue.size() > MAX_QUEUED) {
                queue.poll();
            }
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.warn("Ignored a bad SignedVelocity message: {}", e.getMessage());
        }
    }

    private Verdict next(Map<UUID, Queue<Verdict>> source, UUID player) {
        Queue<Verdict> queue = source.get(player);
        Verdict verdict = queue == null ? null : queue.poll();
        if (verdict == null) {
            if (!warnedMissing) {
                warnedMissing = true;
                LOGGER.warn("No SignedVelocity verdict arrived for a message. Is SignedVelocity installed on the proxy?"
                        + " If not, turn signedvelocity off in integrations.yml.");
            }
            return Verdict.ALLOW;
        }
        return verdict;
    }
}
