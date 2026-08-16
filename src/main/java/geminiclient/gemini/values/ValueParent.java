package geminiclient.gemini.values;

import geminiclient.gemini.base.I18n;

import java.util.function.Supplier;

public class ValueParent {
    protected final String name;
    public Supplier<Boolean> visibility = () -> true;

    private Runnable onChange;

    public ValueParent(String name) {
        this.name = name;
    }

    public ValueParent(String name,Supplier<Boolean> visibility) {
        this.name = name;
        this.visibility = visibility;
    }

    public String getName() {
        return this.name;
    }

    /**
     * 面向用户的显示名：内部 {@link #name} 是配置键与同步 key，保持英文；
     * 渲染时通过 I18n 转成当前语言，未收录时回退原文。
     */
    public String getDisplayName() {
        return I18n.tr(name);
    }

    /** 注册值变更回调，当值变化时自动通知 */
    public void setOnChange(Runnable callback) {
        this.onChange = callback;
    }

    /** 子类 setter 在修改值后调用此方法触发回调 */
    public void notifyChange() {
        if (onChange != null) {
            onChange.run();
        }
    }
}
