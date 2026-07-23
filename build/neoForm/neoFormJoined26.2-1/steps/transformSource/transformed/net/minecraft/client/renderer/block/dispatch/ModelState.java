package net.minecraft.client.renderer.block.dispatch;

import com.mojang.math.Transformation;
import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import net.neoforged.neoforge.client.extensions.ModelStateExtension;

@OnlyIn(Dist.CLIENT)
public interface ModelState extends ModelStateExtension {
    Matrix4fc NO_TRANSFORM = new Matrix4f();

    default Transformation transformation() {
        return Transformation.IDENTITY;
    }

    default Matrix4fc faceTransformation(Direction face) {
        return NO_TRANSFORM;
    }

    default Matrix4fc inverseFaceTransformation(Direction face) {
        return NO_TRANSFORM;
    }
}
