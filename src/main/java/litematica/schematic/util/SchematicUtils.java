package litematica.schematic.util;

import javax.annotation.Nullable;
import org.apache.commons.lang3.tuple.Pair;

import net.minecraft.block.state.IBlockState;

import malilib.util.MathUtils;
import malilib.util.position.BlockMirror;
import malilib.util.position.BlockPos;
import malilib.util.position.BlockRotation;
import malilib.util.position.Direction;
import malilib.util.position.LayerRange;
import malilib.util.position.Vec3i;
import litematica.schematic.ISchematic;
import litematica.schematic.ISchematicRegion;
import litematica.schematic.container.ILitematicaBlockStateContainer;
import litematica.schematic.placement.SchematicPlacement;
import litematica.schematic.placement.SubRegionPlacement;
import litematica.util.PositionUtils;

public class SchematicUtils
{
    @Nullable
    public static BlockPos getSchematicContainerPositionFromWorldPosition(BlockPos worldPos, ISchematic schematic, String regionName,
                                                                          SchematicPlacement schematicPlacement, SubRegionPlacement regionPlacement, ILitematicaBlockStateContainer container)
    {
        ISchematicRegion region = schematic.getSchematicRegion(regionName);

        if (region == null)
        {
            return null;
        }

        BlockPos boxMinRel = getReverseTransformedWorldPosition(worldPos, schematic, schematicPlacement, regionPlacement, region.getSize());

        if (boxMinRel == null)
        {
            return null;
        }

        final int startX = boxMinRel.getX();
        final int startY = boxMinRel.getY();
        final int startZ = boxMinRel.getZ();
        Vec3i size = container.getSize();

        /*
        if (startX < 0 || startY < 0 || startZ < 0 || startX >= size.getX() || startY >= size.getY() || startZ >= size.getZ())
        {
            System.out.printf("DEBUG ============= OUT OF BOUNDS - region: %s, startX: %d, startY %s, startZ: %d - size x: %d y: %s z: %d =============\n",
                    regionName, startX, startY, startZ, size.getX(), size.getY(), size.getZ());
            return null;
        }

        return boxMinRel;
        */

        return new BlockPos(MathUtils.clamp(startX, 0, size.getX() - 1),
                            MathUtils.clamp(startY, 0, size.getY() - 1),
                            MathUtils.clamp(startZ, 0, size.getZ() - 1));
    }

    @Nullable
    private static BlockPos getReverseTransformedWorldPosition(BlockPos worldPos, ISchematic schematic,
                                                               SchematicPlacement schematicPlacement, SubRegionPlacement regionPlacement, Vec3i regionSize)
    {
        BlockPos origin = schematicPlacement.getPosition();
        BlockPos regionPos = regionPlacement.getPosition();

        // These are the untransformed relative positions
        BlockPos posEndRel = (new BlockPos(PositionUtils.getRelativeEndPositionFromAreaSize(regionSize))).add(regionPos);
        BlockPos posMinRel = malilib.util.position.PositionUtils.getMinCorner(regionPos, posEndRel);

        // The transformed sub-region origin position
        BlockPos regionPosTransformed = PositionUtils.getTransformedBlockPos(regionPos, schematicPlacement.getMirror(), schematicPlacement.getRotation());

        // The relative offset of the affected region's corners, to the sub-region's origin corner
        BlockPos relPos = new BlockPos(worldPos.getX() - origin.getX() - regionPosTransformed.getX(),
                                       worldPos.getY() - origin.getY() - regionPosTransformed.getY(),
                                       worldPos.getZ() - origin.getZ() - regionPosTransformed.getZ());

        // Reverse transform that relative offset, to get the untransformed orientation's offsets
        relPos = PositionUtils.getReverseTransformedBlockPos(relPos, regionPlacement.getMirror(), regionPlacement.getRotation());

        relPos = PositionUtils.getReverseTransformedBlockPos(relPos, schematicPlacement.getMirror(), schematicPlacement.getRotation());

        // Get the offset relative to the sub-region's minimum corner, instead of the origin corner (which can be at any corner)
        relPos = relPos.subtract(posMinRel.subtract(regionPos));

        return relPos;
    }

    @Nullable
    public static Pair<Vec3i, Vec3i> getLayerRangeClampedSubRegion(LayerRange range,
                                                                   SchematicPlacement schematicPlacement,
                                                                   SubRegionPlacement placement,
                                                                   Vec3i regionSize)
    {
        int minX = range.getClampedValue(-30000000, Direction.Axis.X);
        int minY = range.getClampedValue(        0, Direction.Axis.Y);
        int minZ = range.getClampedValue(-30000000, Direction.Axis.Z);
        int maxX = range.getClampedValue( 30000000, Direction.Axis.X);
        int maxY = range.getClampedValue(      255, Direction.Axis.Y);
        int maxZ = range.getClampedValue( 30000000, Direction.Axis.Z);

        BlockPos posMinRange = new BlockPos(minX, minY, minZ);
        BlockPos posMaxRange = new BlockPos(maxX, maxY, maxZ);

        ISchematic schematic = schematicPlacement.getSchematic();
        BlockPos pos1 = getReverseTransformedWorldPosition(posMinRange, schematic, schematicPlacement, placement, regionSize);
        BlockPos pos2 = getReverseTransformedWorldPosition(posMaxRange, schematic, schematicPlacement, placement, regionSize);

        if (pos1 == null || pos2 == null)
        {
            return null;
        }

        BlockPos posMinReversed = malilib.util.position.PositionUtils.getMinCorner(pos1, pos2);
        BlockPos posMaxReversed = malilib.util.position.PositionUtils.getMaxCorner(pos1, pos2);

        final int startX = Math.max(posMinReversed.getX(), 0);
        final int startY = Math.max(posMinReversed.getY(), 0);
        final int startZ = Math.max(posMinReversed.getZ(), 0);
        final int endX = Math.min(posMaxReversed.getX(), Math.abs(regionSize.getX()) - 1);
        final int endY = Math.min(posMaxReversed.getY(), Math.abs(regionSize.getY()) - 1);
        final int endZ = Math.min(posMaxReversed.getZ(), Math.abs(regionSize.getZ()) - 1);

        return Pair.of(new Vec3i(startX, startY, startZ), new Vec3i(endX, endY, endZ));
    }

    public static IBlockState getUntransformedBlockState(IBlockState state, SchematicPlacement schematicPlacement, String subRegionName)
    {
        SubRegionPlacement placement = schematicPlacement.getSubRegion(subRegionName);

        if (placement != null)
        {
            final BlockRotation rotationCombined = schematicPlacement.getRotation().add(placement.getRotation()).getReverseRotation();
            final BlockMirror mirrorMain = schematicPlacement.getMirror();
            BlockMirror mirrorSub = placement.getMirror();

            if (mirrorSub != BlockMirror.NONE &&
                (schematicPlacement.getRotation() == BlockRotation.CW_90 ||
                 schematicPlacement.getRotation() == BlockRotation.CCW_90))
            {
                mirrorSub = mirrorSub == BlockMirror.X ? BlockMirror.Z : BlockMirror.X;
            }

            if (rotationCombined != BlockRotation.NONE)
            {
                state = state.withRotation(rotationCombined.getVanillaRotation());
            }

            if (mirrorSub != BlockMirror.NONE)
            {
                state = state.withMirror(mirrorSub.getVanillaMirror());
            }

            if (mirrorMain != BlockMirror.NONE)
            {
                state = state.withMirror(mirrorMain.getVanillaMirror());
            }
        }

        return state;
    }
}
