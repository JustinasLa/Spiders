package net.tfminecraft.spiders.spider.presets;

import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.utilities.maths.Maths;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class MimicPresetTest {
    private MockedStatic<Bukkit> bukkit;

    @BeforeEach
    void blockData() {
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.createBlockData(any(Material.class))).thenAnswer(call -> {
            BlockData data = mock(BlockData.class);
            when(data.getMaterial()).thenReturn(call.getArgument(0));
            return data;
        });
    }

    @AfterEach
    void restore() {
        bukkit.close();
    }

    @Test
    @DisplayName("the mimic's options: a four-legged chest that walks slowly, gallops fast and steps softly")
    void mimicOptions() {
        SpiderOptions options = MimicPreset.mimic();
        assertEquals(4, options.getBodyPlan().getLegs().size());
        assertEquals(.12, options.getWalkGait().getMaxSpeed(), 1e-9);
        assertEquals(MimicShape.STANDING_HEIGHT, options.getWalkGait().getStationary().getBodyHeight(), 1e-9);
        assertEquals(MimicShape.GALLOP_HEIGHT, options.getGallopGait().getMoving().getBodyHeight(), 1e-9);
        assertTrue(options.getGallopGait().getMaxSpeed() > options.getWalkGait().getMaxSpeed());
        assertEquals(Sound.ENTITY_ZOMBIE_STEP, options.getSound().getStep().getSound());
        assertEquals(.12f, options.getSound().getStep().getVolume());
        assertEquals(.05f, options.getSound().getStepBlockOverlay().getVolume());
    }

    @Test
    @DisplayName("the lid opens at most 80 degrees")
    void maxLidAngle() {
        assertEquals(Maths.toRadians(80f), MimicShape.getMAX_LID_ANGLE());
    }

    @Test
    @DisplayName("a NaN lid angle is refused rather than snapping the lid shut")
    void nanLidAngle() {
        MimicPose pose = new MimicPose(MimicPreset.mimicChestModel());
        assertThrows(IllegalArgumentException.class, () -> pose.apply(Float.NaN, 0f, 0f));
    }
}
