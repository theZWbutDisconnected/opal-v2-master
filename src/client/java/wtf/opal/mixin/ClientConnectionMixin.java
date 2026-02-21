package wtf.opal.mixin;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.OffThreadException;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import net.minecraft.network.packet.s2c.play.EnterReconfigurationS2CPacket;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.opal.client.feature.helper.impl.player.packet.blockage.impl.InboundNetworkBlockage;
import wtf.opal.client.feature.helper.impl.player.packet.blockage.impl.OutboundNetworkBlockage;
import wtf.opal.duck.ClientConnectionAccess;
import wtf.opal.event.EventDispatcher;
import wtf.opal.event.impl.game.packet.InstantaneousReceivePacketEvent;
import wtf.opal.event.impl.game.packet.InstantaneousSendPacketEvent;
import wtf.opal.event.impl.game.packet.ReceivePacketEvent;
import wtf.opal.event.impl.game.packet.SendPacketEvent;

import java.util.concurrent.RejectedExecutionException;

@Mixin(ClientConnection.class)
public abstract class ClientConnectionMixin implements ClientConnectionAccess {

    @Shadow
    private static <T extends PacketListener> void handlePacket(Packet<T> packet, PacketListener listener) {
    }

    @Shadow private volatile @Nullable PacketListener packetListener;

    @Shadow public abstract void disconnect(Text disconnectReason);

    @Shadow @Final private static Logger LOGGER;

    @Shadow private int packetsReceivedCounter;

    @Shadow
    private Channel channel;
    
    @Unique
    private boolean isHandlingReconfiguration = false;
    
    @Unique
    private int nettyErrorCount = 0;
    
    @Unique
    private static final int MAX_NETTY_ERRORS = 3;

    @Shadow protected abstract void channelRead0(ChannelHandlerContext channelHandlerContext, Packet<?> packet);

    @Shadow public abstract NetworkSide getSide();

    private ClientConnectionMixin() {
    }

    @Inject(
            method = "<init>",
            at = @At("TAIL")
    )
    private void init(NetworkSide side, CallbackInfo ci) {
        InboundNetworkBlockage.get().reset();
        OutboundNetworkBlockage.get().reset();
        nettyErrorCount = 0;
    }
    
    @Inject(
            method = "disconnect",
            at = @At("HEAD")
    )
    private void onDisconnect(Text disconnectReason, CallbackInfo ci) {
        nettyErrorCount = 0;
        LOGGER.debug("连接断开，Netty错误计数已重置");
    }

    @Inject(
            method = "send(Lnet/minecraft/network/packet/Packet;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void hookSendPacket(Packet<?> packet, CallbackInfo ci) {
        if (packet instanceof EnterReconfigurationS2CPacket && nettyErrorCount >= MAX_NETTY_ERRORS) {
            LOGGER.debug("临时阻止发送EnterReconfigurationS2CPacket");
            ci.cancel();
            return;
        }
        
        if (packet != null && packet.getClass().getName().contains("NetworkStateTransitions$EncoderTransitioner$Lambda")) {
            ci.cancel();
            LOGGER.error("阻止网络状态转换Lambda对象: {}", packet.getClass().getName());
            nettyErrorCount++;
            return;
        }
        
        InstantaneousSendPacketEvent event = new InstantaneousSendPacketEvent(packet);
        EventDispatcher.dispatch(event);
        if (event.isCancelled() || OutboundNetworkBlockage.get().isBlocked(packet)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "send(Lnet/minecraft/network/packet/Packet;Lio/netty/channel/ChannelFutureListener;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void hookSendPacket(Packet<?> packet, @Nullable ChannelFutureListener channelFutureListener, CallbackInfo ci) {
        if (packet instanceof EnterReconfigurationS2CPacket && nettyErrorCount >= MAX_NETTY_ERRORS) {
            ci.cancel();
            LOGGER.debug("临时阻止发送EnterReconfigurationS2CPacket");
            return;
        }
        
        if (packet != null && packet.getClass().getName().contains("NetworkStateTransitions$EncoderTransitioner$Lambda")) {
            ci.cancel();
            LOGGER.error("阻止网络状态转换Lambda对象: {}", packet.getClass().getName());
            nettyErrorCount++;
            return;
        }
        
        final SendPacketEvent event = new SendPacketEvent(packet);
        EventDispatcher.dispatch(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "handlePacket", at = @At("HEAD"), cancellable = true, require = 1)
    private static <T extends PacketListener> void hookReceivePacket(Packet<T> packet, T listener, CallbackInfo ci) {
        if (packet != null && packet.getClass().getName().contains("NetworkStateTransitions$EncoderTransitioner$$Lambda")) {
            ci.cancel();
            LOGGER.error("阻止handlePacket中的Lambda对象: {}", packet.getClass().getName());
            return;
        }
        
        if (packet instanceof BundleS2CPacket bundleS2CPacket) {
            ci.cancel();

            for (Packet<?> packetInBundle : bundleS2CPacket.getPackets()) {
                try {
                    handlePacket(packetInBundle, listener);
                } catch (OffThreadException ignored) {}
            }
            return;
        }

        final ReceivePacketEvent event = new ReceivePacketEvent(packet);
        EventDispatcher.dispatch(event);

        if (event.isCancelled()) {
            ci.cancel();
        }
    }
    
    @Inject(
            method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V",
            at = @At(value = "HEAD"),
            cancellable = true
    )
    @SuppressWarnings("unchecked")
    private void hookChannelRead0Smart(ChannelHandlerContext channelHandlerContext, Packet<?> packet, CallbackInfo ci) {
        if (this.getSide() == NetworkSide.CLIENTBOUND && packet.getClass().getName().contains("EnterReconfiguration")) {
            if (nettyErrorCount >= MAX_NETTY_ERRORS) {
                LOGGER.warn("错误计数过高，临时阻止EnterReconfiguration");
                ci.cancel();
                return;
            }
            LOGGER.debug("处理EnterReconfiguration包，当前错误计数: {}", nettyErrorCount);
            return;
        }
    }

    @Inject(
            method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V",
            at = @At(value = "INVOKE", target = "Lio/netty/channel/Channel;isOpen()Z"),
            cancellable = true
    )
    private void hookChannelRead(ChannelHandlerContext channelHandlerContext, Packet<?> packet, CallbackInfo ci) {
        if (this.getSide() == NetworkSide.CLIENTBOUND) {
            if (nettyErrorCount > 0) {
                nettyErrorCount = 0;
                LOGGER.debug("Netty错误计数已重置");
            }
            
            if (packet instanceof BundleS2CPacket bundleS2CPacket) {
                ci.cancel();

                for (Packet<?> packetInBundle : bundleS2CPacket.getPackets()) {
                    try {
                        channelRead0(channelHandlerContext, packetInBundle);
                    } catch (OffThreadException ignored) {}
                }
                return;
            }
            
            InstantaneousReceivePacketEvent event = new InstantaneousReceivePacketEvent(packet);
            EventDispatcher.dispatch(event);
            if (event.isCancelled() || InboundNetworkBlockage.get().isBlocked(packet)) {
                ci.cancel();
            }
        }
    }

    @Inject(
            method = "send(Lnet/minecraft/network/packet/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void hookSendPacketWithBypass(Packet<?> packet, @Nullable ChannelFutureListener channelFutureListener, boolean bypassEventHandler, CallbackInfo ci) {
        if (packet instanceof EnterReconfigurationS2CPacket && nettyErrorCount >= MAX_NETTY_ERRORS) {
            ci.cancel();
            LOGGER.debug("临时阻止发送EnterReconfigurationS2CPacket (bypass模式)");
            return;
        }
        
        if (packet != null && packet.getClass().getName().contains("NetworkStateTransitions$EncoderTransitioner$$Lambda")) {
            ci.cancel();
            LOGGER.error("阻止bypass模式的Lambda对象: {}", packet.getClass().getName());
            nettyErrorCount++;
            return;
        }
        
        if (nettyErrorCount >= MAX_NETTY_ERRORS) {
            LOGGER.error("Netty错误次数过多，暂时阻止所有发包以防止死循环");
            ci.cancel();
            return;
        }
        
        try {
            if (packet == null) {
                ci.cancel();
                return;
            }
        } catch (Exception e) {
            nettyErrorCount++;
            LOGGER.warn("阻止发送可能导致Netty错误的包 (错误计数: {}): {}", nettyErrorCount, e.getMessage());
            ci.cancel();
        }
    }

    @Unique
    @Override
    public void opal$channelReadSilent(Packet<?> packet) {
        if (this.channel.isOpen()) {
            PacketListener packetListener = this.packetListener;
            if (packetListener == null) {
                throw new IllegalStateException("Received a packet before the packet listener was initialized");
            } else
            if (packetListener.accepts(packet)) {
                try {
                    handlePacket(packet, packetListener);
                } catch (OffThreadException var5) {
                } catch (RejectedExecutionException var6) {
                    this.disconnect(Text.translatable("multiplayer.disconnect.server_shutdown"));
                } catch (ClassCastException var7) {
                    LOGGER.error("Received {} that couldn't be processed", packet.getClass(), var7);
                    this.disconnect(Text.translatable("multiplayer.disconnect.invalid_packet"));
                } catch (UnsupportedOperationException var8) {
                    if (packet instanceof EnterReconfigurationS2CPacket) {
                        LOGGER.warn("抑制EnterReconfigurationS2CPacket的Netty错误，保持连接: {}", var8.getMessage());
                        return;
                    }
                    if (var8.getMessage() != null && var8.getMessage().contains("unsupported message type")) {
                        LOGGER.warn("抑制网络状态转换Netty错误: {}", var8.getMessage());
                        return;
                    }
                    LOGGER.error("Netty error processing packet {}: {}", packet.getClass(), var8.getMessage());
                    throw var8;
                } catch (Exception var9) {
                    LOGGER.error("Exception processing packet {}: {}", packet.getClass(), var9.getMessage());
                    throw var9;
                }

                this.packetsReceivedCounter++;
            }
        }
    }
}