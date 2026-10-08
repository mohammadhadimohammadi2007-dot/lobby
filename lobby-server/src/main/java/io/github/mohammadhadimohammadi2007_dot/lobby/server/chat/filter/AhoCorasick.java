package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.filter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * Finds every occurrence of many words in a text in one pass (the Aho-Corasick algorithm), so a word list
 * with thousands of entries costs about the same as one with ten.
 *
 * <p>Immutable after construction and safe to share between threads.
 */
final class AhoCorasick {

    /** One occurrence: pattern number {@code pattern} ends at {@code end} (inclusive) in the text. */
    record Match(int pattern, int end) {
    }

    private static final class Node {
        final Map<Character, Node> next = new HashMap<>(4);
        Node fail;
        /** Patterns that end here, including those reached through fail links. */
        final List<Integer> outputs = new ArrayList<>(1);
    }

    private final Node root = new Node();
    private final int[] lengths;

    /** @param patterns the words to look for; must not be empty strings */
    AhoCorasick(List<String> patterns) {
        lengths = new int[patterns.size()];
        for (int id = 0; id < patterns.size(); id++) {
            String pattern = patterns.get(id);
            lengths[id] = pattern.length();
            Node node = root;
            for (int i = 0; i < pattern.length(); i++) {
                node = node.next.computeIfAbsent(pattern.charAt(i), c -> new Node());
            }
            node.outputs.add(id);
        }
        buildFailLinks();
    }

    private void buildFailLinks() {
        Queue<Node> queue = new ArrayDeque<>();
        for (Node child : root.next.values()) {
            child.fail = root;
            queue.add(child);
        }
        while (!queue.isEmpty()) {
            Node node = queue.remove();
            for (Map.Entry<Character, Node> edge : node.next.entrySet()) {
                char c = edge.getKey();
                Node child = edge.getValue();
                Node fail = node.fail;
                while (fail != null && !fail.next.containsKey(c)) {
                    fail = fail.fail;
                }
                child.fail = fail == null ? root : fail.next.get(c);
                child.outputs.addAll(child.fail.outputs);
                queue.add(child);
            }
        }
    }

    /** Length of pattern number {@code id}. */
    int length(int id) {
        return lengths[id];
    }

    /** All matches in {@code text}, ordered by end position. */
    List<Match> search(String text) {
        List<Match> matches = new ArrayList<>();
        Node node = root;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            while (node != root && !node.next.containsKey(c)) {
                node = node.fail;
            }
            node = node.next.getOrDefault(c, root);
            for (int pattern : node.outputs) {
                matches.add(new Match(pattern, i));
            }
        }
        return matches;
    }
}
