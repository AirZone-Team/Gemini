package net.minecraft.client.gui.screens.dialog;

import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.server.ServerLinks;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.ServerLinksDialog;
import net.minecraft.server.dialog.action.StaticAction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class ServerLinksDialogScreen extends ButtonListDialogScreen<ServerLinksDialog> {
    public ServerLinksDialogScreen(@Nullable Screen previousScreen, ServerLinksDialog dialog, DialogConnectionAccess connectionAccess) {
        super(previousScreen, dialog, connectionAccess);
    }

    protected Stream<ActionButton> createListActions(ServerLinksDialog dialog, DialogConnectionAccess connectionAccess) {
        return connectionAccess.serverLinks().entries().stream().map(entry -> createDialogClickAction(dialog, entry));
    }

    private static ActionButton createDialogClickAction(ServerLinksDialog data, ServerLinks.Entry entry) {
        return new ActionButton(
            new CommonButtonData(entry.displayName(), data.buttonWidth()), Optional.of(new StaticAction(new ClickEvent.OpenUrl(entry.link())))
        );
    }
}
