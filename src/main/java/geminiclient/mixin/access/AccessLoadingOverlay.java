package geminiclient.mixin.access;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * 暴露原版 {@link LoadingOverlay} 的构造参数：MixinMinecraft 在
 * {@code Minecraft.<init>} 中拦截初始加载画面时，用这些字段原样构造
 * {@code GeminiLoadingOverlay}，保证资源重载实例与 onFinish 流转
 * （失败回滚 / 成功进主菜单）不受替换影响。
 */
@Mixin(LoadingOverlay.class)
public interface AccessLoadingOverlay {

    @Accessor("minecraft")
    Minecraft minecraft();

    @Accessor("reload")
    ReloadInstance reload();

    @Accessor("onFinish")
    Consumer<Optional<Throwable>> onFinish();

    @Accessor("fadeIn")
    boolean fadeIn();
}
