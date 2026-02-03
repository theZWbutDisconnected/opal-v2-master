package wtf.opal.event.impl.client;

import net.minecraft.client.gui.screen.Screen;
import wtf.opal.event.EventCancellable;

public class SetScreenEvent extends EventCancellable {
    public final Screen screen;

    public SetScreenEvent(Screen screen) {
        this.screen = screen;
    }
}
