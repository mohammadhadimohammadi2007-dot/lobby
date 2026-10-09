package io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram;

import org.jetbrains.annotations.Nullable;

/**
 * The name tag holograms of NPCs, as far as the hologram importer needs them: FancyHolograms can link
 * a hologram to a FancyNpcs NPC, and here such a hologram becomes that NPC's name tag. Implemented by
 * the NPC service, so this package does not depend on the NPC package.
 */
public interface NameTags {

    /** No NPCs: every linked hologram is reported instead of imported. */
    NameTags NONE = new NameTags() {
        @Override
        public @Nullable HologramData nameTagOf(String npcName) {
            return null;
        }

        @Override
        public void changed(String npcName) {
        }
    };

    /** The name tag of the NPC with that name, or {@code null} if there is no such NPC. */
    @Nullable HologramData nameTagOf(String npcName);

    /** Saves and shows a name tag after it was changed. */
    void changed(String npcName);
}
