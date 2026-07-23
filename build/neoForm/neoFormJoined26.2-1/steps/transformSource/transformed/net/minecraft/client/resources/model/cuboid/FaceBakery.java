package net.minecraft.client.resources.model.cuboid;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.blaze3d.platform.Transparency;
import com.mojang.math.MatrixUtil;
import com.mojang.math.Quadrant;
import com.mojang.math.Transformation;
import java.util.Objects;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.FaceInfo;
import net.minecraft.client.renderer.block.dispatch.ModelState;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.GeometryUtils;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class FaceBakery {
    private static final Vector3fc BLOCK_MIDDLE = new Vector3f(0.5F, 0.5F, 0.5F);

    @VisibleForTesting
    public static CuboidFace.UVs defaultFaceUV(Vector3fc from, Vector3fc to, Direction facing) {
        return switch (facing) {
            case DOWN -> new CuboidFace.UVs(from.x(), 16.0F - to.z(), to.x(), 16.0F - from.z());
            case UP -> new CuboidFace.UVs(from.x(), from.z(), to.x(), to.z());
            case NORTH -> new CuboidFace.UVs(16.0F - to.x(), 16.0F - to.y(), 16.0F - from.x(), 16.0F - from.y());
            case SOUTH -> new CuboidFace.UVs(from.x(), 16.0F - to.y(), to.x(), 16.0F - from.y());
            case WEST -> new CuboidFace.UVs(from.z(), 16.0F - to.y(), to.z(), 16.0F - from.y());
            case EAST -> new CuboidFace.UVs(16.0F - to.z(), 16.0F - to.y(), 16.0F - from.z(), 16.0F - from.y());
        };
    }

    private static Transparency computeMaterialTransparency(Material.Baked material, CuboidFace.UVs uvs) {
        return material.forceTranslucent()
            ? Transparency.TRANSLUCENT
            : material.sprite()
                .contents()
                .computeTransparency(
                    Math.min(uvs.minU(), uvs.maxU()) / 16.0F,
                    Math.min(uvs.minV(), uvs.maxV()) / 16.0F,
                    Math.max(uvs.minU(), uvs.maxU()) / 16.0F,
                    Math.max(uvs.minV(), uvs.maxV()) / 16.0F
                );
    }

    public static BakedQuad bakeQuad(
        ModelBaker modelBaker,
        Vector3fc from,
        Vector3fc to,
        CuboidFace face,
        Material.Baked material,
        Direction facing,
        ModelState modelState,
        @Nullable CuboidRotation elementRotation,
        boolean shade,
        int lightEmission
    ) {
        CuboidFace.UVs uvs = face.uvs();
        if (uvs == null) {
            uvs = defaultFaceUV(from, to, facing);
        }

        Transparency transparency = computeMaterialTransparency(material, uvs);
        ModelBaker.Interner interner = modelBaker.interner();
        var faceData = face.faceData();
        lightEmission = Math.max(lightEmission, faceData.lightEmission());
        BakedQuad.MaterialInfo materialInfo = interner.materialInfo(BakedQuad.MaterialInfo.of(material, transparency, face.tintIndex(), shade, lightEmission, faceData.ambientOcclusion()));
        return bakeQuad(interner, from, to, uvs, face.rotation(), materialInfo, facing, modelState, elementRotation, faceData);
    }

    /// @deprecated Neo: use [#bakeQuad(ModelBaker.Interner, Vector3fc, Vector3fc, CuboidFace.UVs, Quadrant, int, BakedQuad.MaterialInfo, Direction, ModelState, CuboidRotation, boolean, int, net.neoforged.neoforge.client.model.ExtraFaceData)] instead
    @Deprecated
    public static BakedQuad bakeQuad(
        ModelBaker.Interner interner,
        Vector3fc from,
        Vector3fc to,
        CuboidFace.UVs uvs,
        Quadrant uvRotation,
        BakedQuad.MaterialInfo materialInfo,
        Direction facing,
        ModelState modelState,
        @Nullable CuboidRotation elementRotation
    ) {
        return bakeQuad(interner, from, to, uvs, uvRotation, materialInfo, facing, modelState, elementRotation, net.neoforged.neoforge.client.model.ExtraFaceData.DEFAULT);
    }

    public static BakedQuad bakeQuad(
        ModelBaker.Interner interner,
        Vector3fc from,
        Vector3fc to,
        CuboidFace.UVs uvs,
        Quadrant uvRotation,
        BakedQuad.MaterialInfo materialInfo,
        Direction facing,
        ModelState modelState,
        @Nullable CuboidRotation elementRotation,
        net.neoforged.neoforge.client.model.ExtraFaceData faceData
    ) {
        Matrix4fc uvTransform = modelState.inverseFaceTransformation(facing);
        Vector3fc[] vertexPositions = new Vector3fc[4];
        long[] vertexPackedUvs = new long[4];
        FaceInfo faceInfo = FaceInfo.fromFacing(facing);

        for (int i = 0; i < 4; i++) {
            bakeVertex(
                i,
                faceInfo,
                uvs,
                uvRotation,
                uvTransform,
                from,
                to,
                materialInfo,
                modelState.transformation(),
                elementRotation,
                vertexPositions,
                vertexPackedUvs,
                interner
            );
        }

        Direction finalDirection = calculateFacing(vertexPositions);
        if (elementRotation == null && finalDirection != null) {
            // Neo: Suppress winding re-calculation when the quads may not be axis-aligned due to root transforms
            if (!modelState.mayApplyArbitraryRotation())
            recalculateWinding(vertexPositions, vertexPackedUvs, finalDirection);
        }

        return new BakedQuad(
            vertexPositions[0],
            vertexPositions[1],
            vertexPositions[2],
            vertexPositions[3],
            vertexPackedUvs[0],
            vertexPackedUvs[1],
            vertexPackedUvs[2],
            vertexPackedUvs[3],
            Objects.requireNonNullElse(finalDirection, Direction.UP),
            materialInfo,
            interner.normals(net.neoforged.neoforge.client.model.quad.BakedNormals.of(net.neoforged.neoforge.client.model.quad.BakedNormals.computeQuadNormal(vertexPositions[0], vertexPositions[1], vertexPositions[2], vertexPositions[3]))),
            interner.colors(net.neoforged.neoforge.client.model.quad.BakedColors.of(faceData.color()))
        );
    }

    private static void bakeVertex(
        int index,
        FaceInfo faceInfo,
        CuboidFace.UVs uvs,
        Quadrant uvRotation,
        Matrix4fc uvTransform,
        Vector3fc from,
        Vector3fc to,
        BakedQuad.MaterialInfo materialInfo,
        Transformation rotation,
        @Nullable CuboidRotation elementRotation,
        Vector3fc[] positionOutput,
        long[] uvOutput,
        ModelBaker.Interner interner
    ) {
        FaceInfo.VertexInfo vertexInfo = faceInfo.getVertexInfo(index);
        Vector3f vertex = vertexInfo.select(from, to).div(16.0F);
        if (elementRotation != null) {
            rotateVertexBy(vertex, elementRotation.origin(), elementRotation.transform());
        }

        if (rotation != Transformation.IDENTITY) {
            rotateVertexBy(vertex, BLOCK_MIDDLE, rotation.getMatrix());
        }

        float rawU = CuboidFace.getU(uvs, uvRotation, index);
        float rawV = CuboidFace.getV(uvs, uvRotation, index);
        float transformedV;
        float transformedU;
        if (MatrixUtil.isIdentity(uvTransform)) {
            transformedU = rawU;
            transformedV = rawV;
        } else {
            Vector3f transformedUV = uvTransform.transformPosition(new Vector3f(cornerToCenter(rawU), cornerToCenter(rawV), 0.0F));
            transformedU = centerToCorner(transformedUV.x);
            transformedV = centerToCorner(transformedUV.y);
        }

        positionOutput[index] = interner.vector(vertex);
        uvOutput[index] = UVPair.pack(materialInfo.sprite().getU(transformedU), materialInfo.sprite().getV(transformedV));
    }

    private static float cornerToCenter(float value) {
        return value - 0.5F;
    }

    private static float centerToCorner(float value) {
        return value + 0.5F;
    }

    private static void rotateVertexBy(Vector3f vertex, Vector3fc origin, Matrix4fc transformation) {
        vertex.sub(origin);
        transformation.transformPosition(vertex);
        vertex.add(origin);
    }

    private static @Nullable Direction calculateFacing(Vector3fc[] positions) {
        Vector3f normal = new Vector3f();
        GeometryUtils.normal(positions[0], positions[1], positions[2], normal);
        return findClosestDirection(normal);
    }

    private static @Nullable Direction findClosestDirection(Vector3f direction) {
        if (!direction.isFinite()) {
            return null;
        }

        Direction best = null;
        float closestProduct = 0.0F;

        for (Direction candidate : Direction.values()) {
            float product = direction.dot(candidate.getUnitVec3f());
            if (product >= 0.0F && product > closestProduct) {
                closestProduct = product;
                best = candidate;
            }
        }

        return best;
    }

    public static void recalculateWinding(Vector3fc[] positions, long[] uvs, Direction direction) {
        recalculateWinding(positions, uvs, direction, null, null);
    }

    // Neo: Allow swapping colors and normals alongside the positions/uvs
    public static void recalculateWinding(Vector3fc[] positions, long[] uvs, Direction direction, int @Nullable [] colors, int @Nullable [] normals) {
        float minX = 999.0F;
        float minY = 999.0F;
        float minZ = 999.0F;
        float maxX = -999.0F;
        float maxY = -999.0F;
        float maxZ = -999.0F;

        for (int i = 0; i < 4; i++) {
            Vector3fc position = positions[i];
            float x = position.x();
            float y = position.y();
            float z = position.z();
            if (x < minX) {
                minX = x;
            }

            if (y < minY) {
                minY = y;
            }

            if (z < minZ) {
                minZ = z;
            }

            if (x > maxX) {
                maxX = x;
            }

            if (y > maxY) {
                maxY = y;
            }

            if (z > maxZ) {
                maxZ = z;
            }
        }

        FaceInfo info = FaceInfo.fromFacing(direction);

        for (int vertex = 0; vertex < 4; vertex++) {
            FaceInfo.VertexInfo vertInfo = info.getVertexInfo(vertex);
            float newX = vertInfo.xFace().select(minX, minY, minZ, maxX, maxY, maxZ);
            float newY = vertInfo.yFace().select(minX, minY, minZ, maxX, maxY, maxZ);
            float newZ = vertInfo.zFace().select(minX, minY, minZ, maxX, maxY, maxZ);
            int vertexToSwap = findVertex(positions, vertex, newX, newY, newZ);
            if (vertexToSwap == -1) {
                throw new IllegalStateException("Can't find vertex to swap");
            }

            if (vertexToSwap != vertex) {
                swap(positions, vertexToSwap, vertex);
                swap(uvs, vertexToSwap, vertex);
                if (colors != null) {
                    swap(colors, vertexToSwap, vertex);
                }
                if (normals != null) {
                    swap(normals, vertexToSwap, vertex);
                }
            }
        }
    }

    private static int findVertex(Vector3fc[] positions, int start, float x, float y, float z) {
        for (int i = start; i < 4; i++) {
            Vector3fc position = positions[i];
            if (x == position.x() && y == position.y() && z == position.z()) {
                return i;
            }
        }

        return -1;
    }

    private static void swap(Vector3fc[] array, int indexA, int indexB) {
        Vector3fc tmp = array[indexA];
        array[indexA] = array[indexB];
        array[indexB] = tmp;
    }

    private static void swap(long[] array, int indexA, int indexB) {
        long tmp = array[indexA];
        array[indexA] = array[indexB];
        array[indexB] = tmp;
    }

    // Neo: Used by the winding patch to swap colors/normals
    private static void swap(int[] array, int indexA, int indexB) {
        int tmp = array[indexA];
        array[indexA] = array[indexB];
        array[indexB] = tmp;
    }
}
