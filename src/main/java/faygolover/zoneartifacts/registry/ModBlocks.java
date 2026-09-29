package faygolover.zoneartifacts.registry;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.block.PukhBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Block anomalies. */
public final class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ZoneArtifacts.MODID);

    /** Burning Fluff: the fleshy base (the hanging strands are its block entity's). */
    public static final RegistryObject<Block> PUKH = BLOCKS.register("pukh",
            () -> new PukhBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_GREEN)
                    .strength(0.6f)
                    .sound(SoundType.SLIME_BLOCK)
                    .noOcclusion()
                    .pushReaction(PushReaction.DESTROY)));

    private ModBlocks() {
    }
}
