package wtf.opal.mixin;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.MessageToByteEncoder;
import net.minecraft.network.packet.Packet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MessageToByteEncoder.class, priority = 1000)
public abstract class NettyEncoderMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("OpalNettyEncoder");

    @Inject(
            method = "write",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void onEncode(ChannelHandlerContext ctx, Object msg, ChannelPromise promise, CallbackInfo ci) {
        if (msg instanceof Packet) {
            Packet<?> packet = (Packet<?>) msg;
            if (packet.getClass().getName().contains("Lambda")) {
                LOGGER.error("在Netty编码器层面阻止Lambda对象: {}", packet.getClass().getName());
                ci.cancel();
                return;
            }
            if (packet.getClass().getName().contains("NetworkStateTransitions")) {
                LOGGER.error("在Netty编码器层面阻止NetworkStateTransitions对象: {}", packet.getClass().getName());
                ci.cancel();
                return;
            }
        }
    }
}
