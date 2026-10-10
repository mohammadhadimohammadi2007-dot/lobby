package io.github.mohammadhadimohammadi2007_dot.lobby.server.movement;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigReader;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.region.Region;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;
import net.minestom.server.particle.Particle;
import net.minestom.server.sound.SoundEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Everything in {@code movement.yml}: double jump, {@code /fly}, jump pads and launch pads. A pad that
 * cannot be read is reported and left out.
 */
public record MovementConfig(DoubleJump doubleJump, String flyPermission, boolean jumpPadsEnabled,
                             List<JumpPad> jumpPads, List<LaunchPad> launchPads) {

    /** Owner name of launch pad regions in the region tracker. */
    public static final String LAUNCH_PAD_OWNER = "launch-pad";
    private static final double MAX_SPEED = 200;
    private static final int MAX_COOLDOWN_MILLIS = 60_000;

    public MovementConfig {
        jumpPads = List.copyOf(jumpPads);
        launchPads = List.copyOf(launchPads);
    }

    /**
     * A sound and a particle burst played where something happens; either may be {@code null}.
     */
    public record Effect(@Nullable SoundEvent sound, @Nullable Particle particle) {

        public static final Effect NONE = new Effect(null, null);
    }

    /**
     * @param forward speed in the look direction, blocks per second
     * @param up      upward speed, blocks per second
     */
    public record DoubleJump(boolean enabled, String permission, long cooldownMillis, double forward, double up,
                             Effect effect) {
    }

    /**
     * A pressure plate on a block that throws players.
     *
     * @param below the block under the plate, or {@code null} for any
     */
    public record JumpPad(Block plate, @Nullable Block below, double forward, double up, Effect effect) {

        /** True if a player standing in {@code feet}, above {@code under}, is on this pad. */
        public boolean matches(Block feet, Block under) {
            return feet.compare(plate) && (below == null || under.compare(below));
        }
    }

    /** A box that throws everyone who walks in with a fixed velocity. */
    public record LaunchPad(Region region, Vec velocity, Effect effect) {
    }

    /** Reads movement.yml. */
    public static MovementConfig read(ConfigReader reader) {
        DoubleJump doubleJump = new DoubleJump(reader.bool("double-jump.enabled", true),
                reader.string("double-jump.permission", "").strip(),
                reader.integer("double-jump.cooldown", 0, MAX_COOLDOWN_MILLIS, 1000),
                speed(reader, "double-jump.forward"), speed(reader, "double-jump.up"),
                effect(reader, "double-jump", reader.string("double-jump.sound", ""),
                        reader.string("double-jump.particle", "")));
        List<JumpPad> jumpPads = new ArrayList<>();
        int index = 0;
        for (Object entry : reader.lineList("jump-pads.pads")) {
            JumpPad pad = jumpPad(reader, "jump-pads.pads[" + index++ + "]", entry);
            if (pad != null) {
                jumpPads.add(pad);
            }
        }
        List<LaunchPad> launchPads = new ArrayList<>();
        index = 0;
        for (Object entry : reader.lineList("launch-pads")) {
            LaunchPad pad = launchPad(reader, "launch-pads[" + index++ + "]", entry);
            if (pad != null) {
                launchPads.add(pad);
            }
        }
        return new MovementConfig(doubleJump, reader.string("fly.permission", "lobby.fly").strip(),
                reader.bool("jump-pads.enabled", true), jumpPads, launchPads);
    }

    private static @Nullable JumpPad jumpPad(ConfigReader reader, String path, Object entry) {
        if (!(entry instanceof Map<?, ?> section)) {
            reader.reportInvalid(path, entry, "a section with plate, below, forward and up");
            return null;
        }
        Block plate = block(reader, path + ".plate", section.get("plate"));
        if (plate == null) {
            return null;
        }
        Object belowName = section.get("below");
        Block below = null;
        if (belowName != null && !"any".equalsIgnoreCase(String.valueOf(belowName).strip())) {
            below = block(reader, path + ".below", belowName);
            if (below == null) {
                return null;
            }
        }
        return new JumpPad(plate, below, number(section.get("forward"), 0), number(section.get("up"), 0),
                effect(reader, path, section.get("sound"), section.get("particle")));
    }

    private static @Nullable LaunchPad launchPad(ConfigReader reader, String path, Object entry) {
        if (!(entry instanceof Map<?, ?> section)) {
            reader.reportInvalid(path, entry, "a section with name, from, to and velocity");
            return null;
        }
        double[] from = triple(section.get("from"));
        double[] to = triple(section.get("to"));
        double[] velocity = triple(section.get("velocity"));
        Object name = section.get("name");
        if (from == null || to == null || velocity == null || name == null) {
            reader.reportInvalid(path, entry, "name, and from, to and velocity as [x, y, z]");
            return null;
        }
        Region region = Region.between(LAUNCH_PAD_OWNER, String.valueOf(name).strip().toLowerCase(Locale.ROOT),
                new Vec(from[0], from[1], from[2]), new Vec(to[0], to[1], to[2]));
        return new LaunchPad(region, new Vec(clamp(velocity[0]), clamp(velocity[1]), clamp(velocity[2])),
                effect(reader, path, section.get("sound"), section.get("particle")));
    }

    private static @Nullable Block block(ConfigReader reader, String path, @Nullable Object name) {
        String key = name == null ? "" : String.valueOf(name).strip().toLowerCase(Locale.ROOT);
        Block block = key.isEmpty() ? null : Block.fromKey(key.contains(":") ? key : "minecraft:" + key);
        if (block == null) {
            reader.reportInvalid(path, name, "a block name like \"slime_block\"");
        }
        return block;
    }

    /** The sound and particle of an effect; unknown names are reported and left out. */
    static Effect effect(ConfigReader reader, String path, @Nullable Object soundName, @Nullable Object particleName) {
        SoundEvent sound = null;
        String soundKey = soundName == null ? "" : String.valueOf(soundName).strip().toLowerCase(Locale.ROOT);
        if (!soundKey.isEmpty()) {
            sound = SoundEvent.fromKey(soundKey.contains(":") ? soundKey : "minecraft:" + soundKey);
            if (sound == null) {
                reader.reportInvalid(path + ".sound", soundName, "a sound name like \"entity.bat.takeoff\", or \"\"");
            }
        }
        Particle particle = null;
        String particleKey = particleName == null ? "" : String.valueOf(particleName).strip().toLowerCase(Locale.ROOT);
        if (!particleKey.isEmpty()) {
            particle = Particle.fromKey(particleKey.contains(":") ? particleKey : "minecraft:" + particleKey);
            if (particle == null) {
                reader.reportInvalid(path + ".particle", particleName, "a particle name like \"cloud\", or \"\"");
            }
        }
        return new Effect(sound, particle);
    }

    private static double speed(ConfigReader reader, String path) {
        return reader.decimal(path, -MAX_SPEED, MAX_SPEED);
    }

    private static double number(@Nullable Object value, double fallback) {
        return value instanceof Number number ? clamp(number.doubleValue()) : fallback;
    }

    private static double clamp(double value) {
        return Math.clamp(value, -MAX_SPEED, MAX_SPEED);
    }

    private static double @Nullable [] triple(@Nullable Object value) {
        if (!(value instanceof List<?> list) || list.size() != 3) {
            return null;
        }
        double[] result = new double[3];
        for (int i = 0; i < 3; i++) {
            if (!(list.get(i) instanceof Number number)) {
                return null;
            }
            result[i] = number.doubleValue();
        }
        return result;
    }
}
