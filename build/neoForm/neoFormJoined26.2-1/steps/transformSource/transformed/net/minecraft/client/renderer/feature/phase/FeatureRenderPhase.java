package net.minecraft.client.renderer.feature.phase;

import java.util.Collection;
import net.minecraft.client.renderer.feature.FeatureRendererType;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface FeatureRenderPhase<Submit extends SubmitNode> {
    void submit(Submit submit);

    void sortInto(FeatureRenderPhase.Output output);

    boolean isEmpty();

    @FunctionalInterface
    interface Output {
        void accept(SubmitNode submit, boolean strictlyOrdered);

        default <Submit extends SubmitNode> void acceptFeatureGroup(
            FeatureRendererType<Submit> featureType, Collection<Submit> submits, boolean strictlyOrdered
        ) {
            for (Submit submit : submits) {
                if (submit.featureType() != featureType) {
                    throw new IllegalArgumentException(submit + " was not of feature type " + featureType);
                }

                this.accept(submit, strictlyOrdered);
            }
        }
    }
}
