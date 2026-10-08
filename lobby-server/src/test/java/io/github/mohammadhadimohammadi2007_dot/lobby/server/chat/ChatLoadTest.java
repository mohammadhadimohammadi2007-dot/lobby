package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.render.RenderedMessage;
import net.minestom.server.entity.Player;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 200 fake players each sending 2 messages per second for 60 seconds, while the server keeps ticking.
 * Reports tick time (MSPT), allocations on the chat thread, renders per message and delivery latency.
 *
 * <p>Off by default; run with {@code LOBBY_LOAD_TEST=1 ./gradlew :lobby-server:test --tests '*ChatLoadTest'}.
 * {@code LOBBY_LOAD_TEST_SECONDS} changes the duration. The report is printed and written to
 * {@code build/reports/chat-load-test.txt}.
 */
@EnvTest
@EnabledIfEnvironmentVariable(named = "LOBBY_LOAD_TEST", matches = "1")
class ChatLoadTest {

    private static final int PLAYERS = 200;
    private static final int MESSAGES_PER_SECOND_EACH = 2;
    private static final long TICK_NANOS = 50_000_000L;

    @TempDir
    Path dir;

    @Test
    void twoHundredPlayersChatting(Env env) throws Exception {
        int seconds = Integer.parseInt(System.getenv().getOrDefault("LOBBY_LOAD_TEST_SECONDS", "60"));
        // Every message must get through so delivery is measured at full load (no cooldown or duplicate blocks).
        ChatTestServer server = ChatTestServer.start(env, dir, yml -> yml
                .replace("  cooldown: 2\n", "  cooldown: 0\n")
                .replace("  duplicate-check: true\n", "  duplicate-check: false\n"));
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < PLAYERS; i++) {
            players.add(server.joinQuietly("Player" + i));
        }
        String[] samples = {
                "hello everyone", "anyone want to play bedwars?", "gg wp", "سلام به همه", "where is the parkour",
                "lol :heart: this lobby", "@Player7 come here", "check example.com", "brb", "this is a longer message about nothing"};

        com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long chatThreadId = chatThreadId(server);
        long allocatedBefore = threads.getThreadAllocatedBytes(chatThreadId);

        ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();
        LongAdder renders = new LongAdder();
        LongAdder rendered = new LongAdder();
        AtomicInteger sent = new AtomicInteger();
        ConcurrentLinkedQueue<CompletableFuture<Void>> futures = new ConcurrentLinkedQueue<>();

        // 10 batches per second, each with a tenth of the messages, spread over all players.
        int perBatch = PLAYERS * MESSAGES_PER_SECOND_EACH / 10;
        ScheduledExecutorService sender = Executors.newSingleThreadScheduledExecutor();
        sender.scheduleAtFixedRate(() -> {
            for (int i = 0; i < perBatch; i++) {
                int n = sent.getAndIncrement();
                Player player = players.get(n % PLAYERS);
                long start = System.nanoTime();
                futures.add(server.chat.service().submit(player, samples[n % samples.length] + " " + n)
                        .thenRun(() -> latencies.add(System.nanoTime() - start)));
            }
        }, 0, 100, TimeUnit.MILLISECONDS);

        List<Long> ticks = new ArrayList<>();
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        long nextTick = System.nanoTime();
        while (System.nanoTime() < end) {
            long tickStart = System.nanoTime();
            env.tick();
            ticks.add(System.nanoTime() - tickStart);
            nextTick += TICK_NANOS;
            long sleep = nextTick - System.nanoTime();
            if (sleep > 0) {
                TimeUnit.NANOSECONDS.sleep(sleep);
            }
        }
        sender.shutdown();
        assertTrue(sender.awaitTermination(5, TimeUnit.SECONDS));
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(2, TimeUnit.MINUTES);
        long allocated = threads.getThreadAllocatedBytes(chatThreadId) - allocatedBefore;

        // History keeps the last messages; their render counts stand for the rest.
        server.chat.service().onChatThread(() -> {
            for (ChatMessage message : server.services().history().messages()) {
                RenderedMessage r = message.rendered();
                renders.add(r.renderCount());
                rendered.increment();
            }
        }).get(5, TimeUnit.SECONDS);

        int messages = sent.get();
        String report = report(seconds, messages, ticks, latencies, allocated, renders.sum(), rendered.sum());
        System.out.println(report);
        Path out = Path.of("build", "reports", "chat-load-test.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report);

        assertEquals(messages, latencies.size(), "every message went through the pipeline");
    }

    private static long chatThreadId(ChatTestServer server) throws Exception {
        CompletableFuture<Long> id = new CompletableFuture<>();
        server.chat.service().onChatThread(() -> id.complete(Thread.currentThread().threadId())).get(5, TimeUnit.SECONDS);
        return id.get();
    }

    private static String report(int seconds, int messages, List<Long> ticks, ConcurrentLinkedQueue<Long> latencies,
                                 long allocated, long renders, long sampled) {
        long[] tickNanos = ticks.stream().mapToLong(Long::longValue).sorted().toArray();
        long[] latency = latencies.stream().mapToLong(Long::longValue).sorted().toArray();
        double averageTick = Arrays.stream(tickNanos).average().orElse(0) / 1e6;
        return String.format(Locale.ROOT, """
                Chat load test: %d players x %d msg/s for %d s
                  messages sent ............ %d (%.0f per second)
                  deliveries ............... %d (each message to every player)
                  MSPT (tick time) ......... avg %.3f ms, p50 %.3f ms, p99 %.3f ms, max %.3f ms over %d ticks
                  chat thread allocations .. %.1f MB total, %.1f KB per message
                  renders per message ...... %.2f (sampled over the last %d messages)
                  pipeline latency ......... p50 %.2f ms, p99 %.2f ms, max %.2f ms
                """,
                PLAYERS, MESSAGES_PER_SECOND_EACH, seconds,
                messages, messages / (double) seconds,
                (long) messages * PLAYERS,
                averageTick, percentile(tickNanos, 50) / 1e6, percentile(tickNanos, 99) / 1e6,
                tickNanos.length == 0 ? 0 : tickNanos[tickNanos.length - 1] / 1e6, tickNanos.length,
                allocated / 1e6, allocated / 1024.0 / Math.max(1, messages),
                sampled == 0 ? 0 : renders / (double) sampled, sampled,
                percentile(latency, 50) / 1e6, percentile(latency, 99) / 1e6,
                latency.length == 0 ? 0 : latency[latency.length - 1] / 1e6);
    }

    private static double percentile(long[] sorted, int percent) {
        if (sorted.length == 0) {
            return 0;
        }
        return sorted[Math.min(sorted.length - 1, (int) Math.ceil(percent / 100.0 * sorted.length) - 1)];
    }
}
