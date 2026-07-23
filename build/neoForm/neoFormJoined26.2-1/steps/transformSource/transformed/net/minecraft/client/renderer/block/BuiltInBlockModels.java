package net.minecraft.client.renderer.block;

import com.mojang.math.Axis;
import com.mojang.math.Transformation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.MultiblockChestResources;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.BlockStateModelWrapper;
import net.minecraft.client.renderer.block.model.CompositeBlockModel;
import net.minecraft.client.renderer.block.model.ConditionalBlockModel;
import net.minecraft.client.renderer.block.model.EmptyBlockModel;
import net.minecraft.client.renderer.block.model.SpecialBlockModelWrapper;
import net.minecraft.client.renderer.block.model.properties.conditional.IsXmas;
import net.minecraft.client.renderer.block.model.properties.select.DisplayContext;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.renderer.blockentity.ConduitRenderer;
import net.minecraft.client.renderer.blockentity.CopperGolemStatueBlockRenderer;
import net.minecraft.client.renderer.blockentity.DecoratedPotRenderer;
import net.minecraft.client.renderer.blockentity.ShulkerBoxRenderer;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.blockentity.TheEndPortalRenderer;
import net.minecraft.client.renderer.entity.CopperGolemRenderer;
import net.minecraft.client.renderer.special.BannerSpecialRenderer;
import net.minecraft.client.renderer.special.BellSpecialRenderer;
import net.minecraft.client.renderer.special.BookSpecialRenderer;
import net.minecraft.client.renderer.special.ChestSpecialRenderer;
import net.minecraft.client.renderer.special.ConduitSpecialRenderer;
import net.minecraft.client.renderer.special.CopperGolemStatueSpecialRenderer;
import net.minecraft.client.renderer.special.DecoratedPotSpecialRenderer;
import net.minecraft.client.renderer.special.EndCubeSpecialRenderer;
import net.minecraft.client.renderer.special.PlayerHeadSpecialRenderer;
import net.minecraft.client.renderer.special.ShulkerBoxSpecialRenderer;
import net.minecraft.client.renderer.special.SkullSpecialRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ColorCollection;
import net.minecraft.world.level.block.CopperGolemStatueBlock;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.level.block.PlayerHeadBlock;
import net.minecraft.world.level.block.PlayerWallHeadBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public class BuiltInBlockModels {
    private static void addDefaults(BuiltInBlockModels.Builder builder) {
        createAir(builder, Blocks.AIR);
        createAir(builder, Blocks.CAVE_AIR);
        createAir(builder, Blocks.VOID_AIR);
        createMobHeads(builder, SkullBlock.Types.SKELETON, Blocks.SKELETON_SKULL, Blocks.SKELETON_WALL_SKULL);
        createMobHeads(builder, SkullBlock.Types.ZOMBIE, Blocks.ZOMBIE_HEAD, Blocks.ZOMBIE_WALL_HEAD);
        createMobHeads(builder, SkullBlock.Types.CREEPER, Blocks.CREEPER_HEAD, Blocks.CREEPER_WALL_HEAD);
        createMobHeads(builder, SkullBlock.Types.DRAGON, Blocks.DRAGON_HEAD, Blocks.DRAGON_WALL_HEAD);
        createMobHeads(builder, SkullBlock.Types.PIGLIN, Blocks.PIGLIN_HEAD, Blocks.PIGLIN_WALL_HEAD);
        createMobHeads(builder, SkullBlock.Types.WITHER_SKELETON, Blocks.WITHER_SKELETON_SKULL, Blocks.WITHER_SKELETON_WALL_SKULL);
        builder.put(createPlayerHead(), Blocks.PLAYER_HEAD);
        builder.put(createPlayerWallHead(), Blocks.PLAYER_WALL_HEAD);
        ColorCollection.zipApply(ColorCollection.VALUES, Blocks.BANNER, (color, banner) -> builder.put(createBanner(color), banner));
        ColorCollection.zipApply(ColorCollection.VALUES, Blocks.WALL_BANNER, (color, wallBanner) -> builder.put(createWallBanner(color), wallBanner));
        builder.put(createShulkerBox(), Blocks.SHULKER_BOX);
        ColorCollection.zipApply(ColorCollection.VALUES, Blocks.DYED_SHULKER_BOX, (color, box) -> builder.put(createDyedShulkerBox(color), box));
        builder.put(createSingletonChest(ChestSpecialRenderer.ENDER_CHEST), Blocks.ENDER_CHEST);
        builder.put(createXmasChest(ChestSpecialRenderer.REGULAR), Blocks.CHEST);
        builder.put(createXmasChest(ChestSpecialRenderer.TRAPPED), Blocks.TRAPPED_CHEST);
        WeatheringCopper.WeatherState.forEach(
            state -> {
                builder.put(
                    createChest(ChestSpecialRenderer.COPPER.pick(state)), Blocks.COPPER_CHEST.weathering().pick(state), Blocks.COPPER_CHEST.waxed().pick(state)
                );
                builder.put(createCopperGolem(state), Blocks.COPPER_GOLEM_STATUE.weathering().pick(state), Blocks.COPPER_GOLEM_STATUE.waxed().pick(state));
            }
        );
        builder.put(special(new BellSpecialRenderer.Unbaked()), Blocks.BELL);
        builder.put(special(new ConduitSpecialRenderer.Unbaked(), ConduitRenderer.DEFAULT_TRANSFORMATION), Blocks.CONDUIT);
        builder.put(createDecoratedPot(), Blocks.DECORATED_POT);
        builder.put(createEnchantingTable(), Blocks.ENCHANTING_TABLE);
        builder.put(special(new EndCubeSpecialRenderer.Unbaked(EndCubeSpecialRenderer.Type.GATEWAY)), Blocks.END_GATEWAY);
        builder.put(special(new EndCubeSpecialRenderer.Unbaked(EndCubeSpecialRenderer.Type.PORTAL), TheEndPortalRenderer.TRANSFORMATION), Blocks.END_PORTAL);
        builder.put(BuiltInBlockModels::createFlowerBedModel, Blocks.WILDFLOWERS, Blocks.PINK_PETALS);
    }

    public static void createAir(BuiltInBlockModels.Builder builder, Block block) {
        builder.put(new EmptyBlockModel.Unbaked(), block);
    }

    public static BlockModel.Unbaked special(SpecialModelRenderer.Unbaked<?> model) {
        return new SpecialBlockModelWrapper.Unbaked<>(model, Optional.empty());
    }

    public static BlockModel.Unbaked special(SpecialModelRenderer.Unbaked<?> model, Transformation transformation) {
        return new SpecialBlockModelWrapper.Unbaked<>(model, Optional.of(transformation));
    }

    public static BuiltInBlockModels.SpecialModelFactory createMobHead(SkullBlock.Types type) {
        return createMobHead((SkullBlock.Type) type);
    }

    public static BuiltInBlockModels.SpecialModelFactory createMobHead(SkullBlock.Type type) {
        return specialModelWithPropertyDispatch(
            SkullBlock.ROTATION, rotation -> special(new SkullSpecialRenderer.Unbaked(type), SkullBlockRenderer.TRANSFORMATIONS.freeTransformations(rotation))
        );
    }

    public static BuiltInBlockModels.SpecialModelFactory createMobWallHead(SkullBlock.Types type) {
        return createMobWallHead((SkullBlock.Type) type);
    }

    public static BuiltInBlockModels.SpecialModelFactory createMobWallHead(SkullBlock.Type type) {
        return specialModelWithPropertyDispatch(
            WallSkullBlock.FACING, facing -> special(new SkullSpecialRenderer.Unbaked(type), SkullBlockRenderer.TRANSFORMATIONS.wallTransformation(facing))
        );
    }

    public static void createMobHeads(BuiltInBlockModels.Builder builder, SkullBlock.Types type, Block ground, Block wall) {
        createMobHeads(builder, (SkullBlock.Type) type, ground, wall);
    }

    public static void createMobHeads(BuiltInBlockModels.Builder builder, SkullBlock.Type type, Block ground, Block wall) {
        builder.put(createMobHead(type), ground);
        builder.put(createMobWallHead(type), wall);
    }

    public static BuiltInBlockModels.SpecialModelFactory createPlayerHead() {
        return specialModelWithPropertyDispatch(
            PlayerHeadBlock.ROTATION,
            rotation -> special(new PlayerHeadSpecialRenderer.Unbaked(), SkullBlockRenderer.TRANSFORMATIONS.freeTransformations(rotation))
        );
    }

    public static BuiltInBlockModels.SpecialModelFactory createPlayerWallHead() {
        return specialModelWithPropertyDispatch(
            PlayerWallHeadBlock.FACING,
            facing -> special(new PlayerHeadSpecialRenderer.Unbaked(), SkullBlockRenderer.TRANSFORMATIONS.wallTransformation(facing))
        );
    }

    public static BuiltInBlockModels.SpecialModelFactory createBanner(DyeColor color) {
        return specialModelWithPropertyDispatch(
            BannerBlock.ROTATION,
            rotation -> special(
                new BannerSpecialRenderer.Unbaked(color, BannerBlock.AttachmentType.GROUND), BannerRenderer.TRANSFORMATIONS.freeTransformations(rotation)
            )
        );
    }

    public static BuiltInBlockModels.SpecialModelFactory createWallBanner(DyeColor color) {
        return specialModelWithPropertyDispatch(
            WallBannerBlock.FACING,
            facing -> special(
                new BannerSpecialRenderer.Unbaked(color, BannerBlock.AttachmentType.WALL), BannerRenderer.TRANSFORMATIONS.wallTransformation(facing)
            )
        );
    }

    public static BuiltInBlockModels.SpecialModelFactory createShulkerBox() {
        return specialModelWithPropertyDispatch(
            ShulkerBoxBlock.FACING, facing -> special(new ShulkerBoxSpecialRenderer.Unbaked(), ShulkerBoxRenderer.modelTransform(facing))
        );
    }

    public static BuiltInBlockModels.SpecialModelFactory createDyedShulkerBox(DyeColor color) {
        return specialModelWithPropertyDispatch(
            ShulkerBoxBlock.FACING, facing -> special(new ShulkerBoxSpecialRenderer.Unbaked(color), ShulkerBoxRenderer.modelTransform(facing))
        );
    }

    public static BlockModel.Unbaked createChest(Identifier texture, ChestType chestType, Direction facing) {
        return special(new ChestSpecialRenderer.Unbaked(texture, chestType), ChestRenderer.modelTransformation(facing));
    }

    public static BuiltInBlockModels.SpecialModelFactory createSingletonChest(Identifier texture) {
        return specialModelWithPropertyDispatch(ChestBlock.FACING, facing -> createChest(texture, ChestType.SINGLE, facing));
    }

    public static BuiltInBlockModels.SpecialModelFactory createChest(MultiblockChestResources<Identifier> textures) {
        return specialModelWithPropertyDispatch(ChestBlock.FACING, ChestBlock.TYPE, (facing, type) -> createChest(textures.select(type), type, facing));
    }

    public static BuiltInBlockModels.SpecialModelFactory createXmasChest(MultiblockChestResources<Identifier> textures) {
        return specialModelWithPropertyDispatch(
            ChestBlock.FACING,
            ChestBlock.TYPE,
            (facing, type) -> new ConditionalBlockModel.Unbaked(
                Optional.empty(),
                new IsXmas(),
                createChest(ChestSpecialRenderer.CHRISTMAS.select(type), type, facing),
                createChest(textures.select(type), type, facing)
            )
        );
    }

    public static BuiltInBlockModels.SpecialModelFactory createCopperGolem(WeatheringCopper.WeatherState weatherState) {
        return specialModelWithPropertyDispatch(
            CopperGolemStatueBlock.FACING,
            CopperGolemStatueBlock.POSE,
            (facing, pose) -> special(
                new CopperGolemStatueSpecialRenderer.Unbaked(weatherState, pose), CopperGolemStatueBlockRenderer.modelTransformation(facing)
            )
        );
    }

    public static BuiltInBlockModels.SpecialModelFactory createDecoratedPot() {
        return specialModelWithPropertyDispatch(
            DecoratedPotBlock.HORIZONTAL_FACING, facing -> special(new DecoratedPotSpecialRenderer.Unbaked(), DecoratedPotRenderer.modelTransformation(facing))
        );
    }

    public static BlockStateModelWrapper.Unbaked createBlockStateModelWrapper(BlockColors blockColors, BlockState blockState) {
        return new BlockStateModelWrapper.Unbaked(blockState, blockColors.getTintSources(blockState), Optional.empty());
    }

    public static CompositeBlockModel.Unbaked combineSpecialAndBlockModels(BlockModel.Unbaked specialModel, BlockColors blockColors, BlockState blockState) {
        return new CompositeBlockModel.Unbaked(createBlockStateModelWrapper(blockColors, blockState), specialModel, Optional.empty());
    }

    public static SelectBlockModel.Unbaked createFlowerBedModel(BlockColors blockColors, BlockState blockState) {
        List<BlockTintSource> tintSources = blockColors.getTintSources(blockState);
        Transformation customFlowerTransform = new Transformation(new Vector3f(0.25F, 0.0F, 0.25F), null, null, null);
        BlockStateModelWrapper.Unbaked customTransformModel = new BlockStateModelWrapper.Unbaked(blockState, tintSources, Optional.of(customFlowerTransform));
        BlockStateModelWrapper.Unbaked normalTransformModel = new BlockStateModelWrapper.Unbaked(blockState, tintSources, Optional.empty());
        return new SelectBlockModel.Unbaked(
            Optional.empty(),
            new SelectBlockModel.UnbakedSwitch<>(
                new DisplayContext(), List.of(new SelectBlockModel.SwitchCase<>(List.of(CopperGolemRenderer.BLOCK_DISPLAY_CONTEXT), customTransformModel))
            ),
            Optional.of(normalTransformModel)
        );
    }

    public static BlockModel.Unbaked createEnchantingTable() {
        return special(
            new BookSpecialRenderer.Unbaked(0.0F, 0.0F, 0.0F),
            new Transformation(new Vector3f(0.5F, 0.8125F, 0.5F), Axis.ZP.rotationDegrees(180.0F), null, Axis.XP.rotationDegrees(90.0F))
        );
    }

    public static <P extends Comparable<P>> BuiltInBlockModels.SpecialModelFactory specialModelWithPropertyDispatch(
        Property<P> property, Function<P, BlockModel.Unbaked> blockModel
    ) {
        return state -> {
            P value = state.getValue(property);
            return blockModel.apply(value);
        };
    }

    public static <P1 extends Comparable<P1>, P2 extends Comparable<P2>> BuiltInBlockModels.SpecialModelFactory specialModelWithPropertyDispatch(
        Property<P1> property1, Property<P2> property2, BiFunction<P1, P2, BlockModel.Unbaked> blockModel
    ) {
        return state -> {
            P1 value1 = state.getValue(property1);
            P2 value2 = state.getValue(property2);
            return blockModel.apply(value1, value2);
        };
    }

    public static Map<BlockState, BlockModel.Unbaked> createBlockModels(BlockColors blockColors) {
        BuiltInBlockModels.Builder builder = new BuiltInBlockModels.Builder(blockColors);
        addDefaults(builder);
        net.neoforged.fml.ModLoader.postEvent(new net.neoforged.neoforge.client.event.RegisterBlockModelsEvent(builder));
        return builder.build();
    }

    public static class Builder {
        private final BlockColors blockColors;
        private final Map<BlockState, BlockModel.Unbaked> result = new HashMap<>();

        private Builder(BlockColors blockColors) {
            this.blockColors = blockColors;
        }

        public void put(BuiltInBlockModels.ModelFactory factory, Block a, Block b) {
            this.put(factory, a);
            this.put(factory, b);
        }

        public void put(BlockModel.Unbaked specialModel, Block block) {
            this.put((BuiltInBlockModels.SpecialModelFactory)var1 -> specialModel, block);
        }

        public void put(BuiltInBlockModels.ModelFactory factory, Block block) {
            for (BlockState blockState : block.getStateDefinition().getPossibleStates()) {
                this.result.put(blockState, factory.create(this.blockColors, blockState));
            }
        }

        public Map<BlockState, BlockModel.Unbaked> build() {
            return Map.copyOf(this.result);
        }
    }

    @FunctionalInterface
    public interface ModelFactory {
        BlockModel.Unbaked create(BlockColors colors, BlockState state);
    }

    @FunctionalInterface
    public interface SpecialModelFactory extends BuiltInBlockModels.ModelFactory {
        @Override
        default BlockModel.Unbaked create(BlockColors colors, BlockState state) {
            return BuiltInBlockModels.combineSpecialAndBlockModels(this.createSpecial(state), colors, state);
        }

        BlockModel.Unbaked createSpecial(BlockState state);
    }
}
