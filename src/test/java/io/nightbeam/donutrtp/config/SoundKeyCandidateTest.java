package io.nightbeam.donutrtp.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class SoundKeyCandidateTest {

    @Test
    void enumNameBecomesMinecraftDottedKey() {
        assertEquals(
                List.of("minecraft:entity.enderman.teleport", "minecraft:entity_enderman_teleport"),
                SoundNames.keyCandidates("ENTITY_ENDERMAN_TELEPORT")
        );
    }

    @Test
    void namespacedKeyIsNormalized() {
        assertEquals(
                List.of("minecraft:block.note_block.hat"),
                SoundNames.keyCandidates("Minecraft:block.note_block.hat")
        );
        assertEquals("ENTITY_ENDERMAN_TELEPORT", SoundNames.enumName("entity.enderman.teleport"));
        assertEquals("BLOCK_NOTE_BLOCK_HAT", SoundNames.enumName("minecraft:block.note_block.hat"));
    }
}
