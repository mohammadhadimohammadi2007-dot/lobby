package io.github.mohammadhadimohammadi2007_dot.lobby.server.npc;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.ClientObjectClicks;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Which click runs an action list. The same three FancyNpcs has (its fourth, {@code CUSTOM}, is only
 * for other plugins' code and has no meaning without one).
 */
public enum NpcTrigger {
    /** Either button. */
    ANY_CLICK("actions"),
    /** Left click (attack). */
    LEFT_CLICK("left-click-actions"),
    /** Right click (use). */
    RIGHT_CLICK("right-click-actions");

    private final String fileKey;

    NpcTrigger(String fileKey) {
        this.fileKey = fileKey;
    }

    /** The key of this list in data/npcs.yml. */
    public String fileKey() {
        return fileKey;
    }

    /** The word used in commands, e.g. {@code left_click}, as FancyNpcs spells it. */
    public String commandName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** True if a click with this button runs this list. */
    public boolean runsOn(ClientObjectClicks.ClickType click) {
        return switch (this) {
            case ANY_CLICK -> true;
            case LEFT_CLICK -> click == ClientObjectClicks.ClickType.LEFT;
            case RIGHT_CLICK -> click == ClientObjectClicks.ClickType.RIGHT;
        };
    }

    /** All accepted command words. */
    public static Set<String> commandNames() {
        return Arrays.stream(values()).map(NpcTrigger::commandName).collect(Collectors.toUnmodifiableSet());
    }

    /** The trigger with that command word ({@code -} and {@code _} both work), or {@code null}. */
    public static NpcTrigger fromCommandName(String name) {
        String wanted = name.strip().toLowerCase(Locale.ROOT).replace('-', '_');
        for (NpcTrigger trigger : values()) {
            if (trigger.commandName().equals(wanted)) {
                return trigger;
            }
        }
        return null;
    }
}
