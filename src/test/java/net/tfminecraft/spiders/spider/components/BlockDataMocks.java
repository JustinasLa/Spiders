package net.tfminecraft.spiders.spider.components;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.mockito.MockedStatic;

import java.util.Objects;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

// Voxel-world harness that runs the real body, leg and locomotion code without a server.

/** Block data without a server registry. Install before building body plans. */
final class BlockDataMocks {
    private static MockedStatic<Bukkit> bukkit = null;

    private BlockDataMocks() {
    }

    static void install() {
        MockedStatic<Bukkit> mocked = mockStatic(Bukkit.class);
        mocked.when(() -> Bukkit.createBlockData(any(Material.class))).thenAnswer(call -> blockData(call.getArgument(0)));
        mocked.when(() -> Bukkit.createBlockData(anyString())).thenAnswer(call -> {
            String name = call.getArgument(0);
            int bracket = name.indexOf('[');
            return blockData(Objects.requireNonNull(Material.matchMaterial(bracket < 0 ? name : name.substring(0, bracket))));
        });
        bukkit = mocked;
    }

    static void restore() {
        if (bukkit != null) bukkit.close();
        bukkit = null;
    }

    private static BlockData blockData(Material material) {
        BlockData data = mock(BlockData.class);
        when(data.getMaterial()).thenReturn(material);
        when(data.getAsString()).thenReturn(material.getKey().toString());
        when(data.clone()).thenAnswer(call -> blockData(material));
        return data;
    }
}
