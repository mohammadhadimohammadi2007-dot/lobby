package io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.Action;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionContext;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;

/** {@code sound: <name> [volume] [pitch]}, e.g. {@code "sound: entity.experience_orb.pickup 1 1.5"}. */
public record SoundAction(Key sound, float volume, float pitch) implements Action {

    @Override
    public Step run(ActionContext context) {
        context.player().playSound(Sound.sound(sound, Sound.Source.MASTER, volume, pitch));
        return Step.CONTINUE;
    }

    @Override
    public String describe() {
        return "sound: " + sound.asMinimalString() + " " + volume + " " + pitch;
    }
}
