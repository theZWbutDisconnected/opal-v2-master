package wtf.opal.client.feature.module.impl.movement;

import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.option.KeyBinding;
import wtf.opal.client.OpalClient;
import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.client.feature.module.property.impl.mode.ModeProperty;
import wtf.opal.client.screen.click.dropdown.DropdownClickGUI;
import wtf.opal.event.impl.client.SetScreenEvent;
import wtf.opal.event.impl.game.PreGameTickEvent;
import wtf.opal.event.impl.game.player.interaction.InvCloseEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.utility.player.PlayerUtility;

import static wtf.opal.client.Constants.mc;

public final class InventoryMoveModule extends Module {
    public final ModeProperty<Mode> modeProperty = new ModeProperty<>("Mode", Mode.NORMAL);

    public InventoryMoveModule() {
        super("Inventory Move", "Allows you to move while in inventories.", ModuleCategory.MOVEMENT);
        addProperties(modeProperty);
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        if (this.isBlocked()) {
            return;
        }
        PlayerUtility.updateMovementKeyStates();
    }

    @Subscribe
    public void setScreen(final SetScreenEvent event) {
        if (isEnabled() && !isBlocked()) {
            if (event.screen == null && modeProperty.getValue() == InventoryMoveModule.Mode.GRIM) {
                mc.options.sprintKey.setPressed(false);
            } else {
                PlayerUtility.updateMovementKeyStates();
            }
        }
    }

    public boolean isBlocked() {
        return mc.currentScreen instanceof ChatScreen || mc.currentScreen instanceof DropdownClickGUI;
    }

    public enum Mode {
        NORMAL("Normal"),
        GRIM("Anti Multi Action");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}