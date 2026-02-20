package wtf.opal.mixin;

import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Mouse.class)
public interface MouseAccessor {
    @Invoker("onMouseButton")
    void callOnMouseButton(long window, MouseInput input, int action);

    @Invoker("onCursorPos")
    void callOnCursorPos(long window, double x, double y);
}
