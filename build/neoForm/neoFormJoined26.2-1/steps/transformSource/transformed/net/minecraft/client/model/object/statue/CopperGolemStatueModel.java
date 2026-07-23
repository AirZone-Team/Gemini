package net.minecraft.client.model.object.statue;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Unit;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class CopperGolemStatueModel extends Model<Unit> {
    public CopperGolemStatueModel(ModelPart root) {
        super(root, RenderTypes::entityCutout);
    }

    public void setupAnim(Unit ignored) {
        this.root.y = 0.0F;
        this.root.zRot = (float) Math.PI;
    }
}
