package net.minecraft.server.advancements;

import it.unimi.dsi.fastutil.Stack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.DisplayInfo;

public class AdvancementVisibilityEvaluator {
    private static final int VISIBILITY_DEPTH = 2;

    private static AdvancementVisibilityEvaluator.VisibilityRule evaluateVisibilityRule(Advancement advancement, boolean isDone) {
        Optional<DisplayInfo> display = advancement.display();
        if (display.isEmpty()) {
            return AdvancementVisibilityEvaluator.VisibilityRule.HIDE;
        } else if (isDone) {
            return AdvancementVisibilityEvaluator.VisibilityRule.SHOW;
        } else {
            return display.get().isHidden() ? AdvancementVisibilityEvaluator.VisibilityRule.HIDE : AdvancementVisibilityEvaluator.VisibilityRule.NO_CHANGE;
        }
    }

    private static boolean evaluateVisiblityForUnfinishedNode(Stack<AdvancementVisibilityEvaluator.VisibilityRule> ascendants) {
        for (int i = 0; i <= 2; i++) {
            AdvancementVisibilityEvaluator.VisibilityRule visibility = ascendants.peek(i);
            if (visibility == AdvancementVisibilityEvaluator.VisibilityRule.SHOW) {
                return true;
            }

            if (visibility == AdvancementVisibilityEvaluator.VisibilityRule.HIDE) {
                return false;
            }
        }

        return false;
    }

    private static boolean evaluateVisibility(
        AdvancementNode node,
        Stack<AdvancementVisibilityEvaluator.VisibilityRule> ascendants,
        Predicate<AdvancementNode> isDoneTest,
        AdvancementVisibilityEvaluator.Output output
    ) {
        boolean isSelfDone = isDoneTest.test(node);
        AdvancementVisibilityEvaluator.VisibilityRule descendantVisibility = evaluateVisibilityRule(node.advancement(), isSelfDone);
        boolean isSelfOrDescendantDone = isSelfDone;
        ascendants.push(descendantVisibility);

        for (AdvancementNode child : node.children()) {
            isSelfOrDescendantDone |= evaluateVisibility(child, ascendants, isDoneTest, output);
        }

        boolean visiblity = isSelfOrDescendantDone || evaluateVisiblityForUnfinishedNode(ascendants);
        ascendants.pop();
        output.accept(node, visiblity);
        return isSelfOrDescendantDone;
    }

    public static void evaluateVisibility(AdvancementNode node, Predicate<AdvancementNode> isDone, AdvancementVisibilityEvaluator.Output output) {
        AdvancementNode root = node.root();
        Stack<AdvancementVisibilityEvaluator.VisibilityRule> visibilityStack = new ObjectArrayList<>();

        for (int i = 0; i <= 2; i++) {
            visibilityStack.push(AdvancementVisibilityEvaluator.VisibilityRule.NO_CHANGE);
        }

        evaluateVisibility(root, visibilityStack, isDone, output);
    }

    public static boolean isVisible(AdvancementNode advancement, Predicate<AdvancementNode> test) {
        Stack<AdvancementVisibilityEvaluator.VisibilityRule> stack = new ObjectArrayList<>();

        for(int i = 0; i <= 2; ++i) {
            stack.push(AdvancementVisibilityEvaluator.VisibilityRule.NO_CHANGE);
        }
        return evaluateVisibility(advancement.root(), stack, test, (adv, visible) -> {});
    }

    @FunctionalInterface
    public interface Output {
        void accept(AdvancementNode advancement, boolean visible);
    }

    private enum VisibilityRule {
        SHOW,
        HIDE,
        NO_CHANGE;
    }
}
