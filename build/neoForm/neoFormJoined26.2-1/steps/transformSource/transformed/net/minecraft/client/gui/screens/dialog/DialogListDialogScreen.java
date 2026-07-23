package net.minecraft.client.gui.screens.dialog;

import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogListDialog;
import net.minecraft.server.dialog.action.StaticAction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class DialogListDialogScreen extends ButtonListDialogScreen<DialogListDialog> {
    public DialogListDialogScreen(@Nullable Screen previousScreen, DialogListDialog dialog, DialogConnectionAccess connectionAccess) {
        super(previousScreen, dialog, connectionAccess);
    }

    protected Stream<ActionButton> createListActions(DialogListDialog data, DialogConnectionAccess connectionAccess) {
        return data.dialogs().stream().map(subDialog -> createDialogClickAction(data, (Holder<Dialog>)subDialog));
    }

    private static ActionButton createDialogClickAction(DialogListDialog data, Holder<Dialog> subDialog) {
        return new ActionButton(
            new CommonButtonData(subDialog.value().common().computeExternalTitle(), data.buttonWidth()),
            Optional.of(new StaticAction(new ClickEvent.ShowDialog(subDialog)))
        );
    }
}
