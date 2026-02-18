package wtf.opal.client.feature.module.impl.world.scaffold.prediction;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import wtf.opal.client.OpalClient;
import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.client.feature.module.impl.movement.longjump.LongJumpModule;
import wtf.opal.client.feature.module.impl.world.breaker.BreakerModule;
import wtf.opal.client.feature.module.impl.world.scaffold.ScaffoldModule;
import wtf.opal.client.feature.module.impl.world.scaffold.ScaffoldSettings;
import wtf.opal.utility.misc.math.RandomUtility;
import wtf.opal.utility.player.InventoryUtility;
import wtf.opal.utility.player.MoveUtility;
import wtf.opal.utility.player.PlayerUtility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import static wtf.opal.client.Constants.mc;
import static wtf.opal.client.feature.module.impl.world.scaffold.prediction.ScaffoldPredictionSettings.*;

public class ScaffoldPrediction extends Module {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private final ScaffoldPredictionSettings settings = new ScaffoldPredictionSettings(this);

    private int stage;
    private boolean shouldKeepY;
    private int startY;
    private int lastSlot;
    private int blockCount;
    private int rotationTick;
    private float yaw;
    private float pitch;
    private boolean canRotate;
    private int towerTick;
    private int towerDelay;
    private boolean towering;

    private boolean shouldStopSprint() {
        if (this.isTowering()) {
            return false;
        } else {
            boolean stage = this.getSettings().getKeepYMode() == KeepYMode.VANILLA || this.getSettings().getKeepYMode() == KeepYMode.EXTRA;
            return (!stage || this.stage <= 0) && this.getSettings().getSprintMode() == SprintMode.NONE;
        }
    }

    private boolean canPlace() {
        BreakerModule bedNuker = OpalClient.getInstance().getModuleRepository().getModule(BreakerModule.class);
        if (bedNuker.isEnabled() && bedNuker.isBreaking()) {
            return false;
        } else {
            LongJumpModule longJump = OpalClient.getInstance().getModuleRepository().getModule(LongJumpModule.class);
            return !longJump.isEnabled();
        }
    }

    private Direction getBestFacing(BlockPos blockPos1, BlockPos blockPos3) {
        double offset = 0.0;
        Direction enumFacing = null;
        for (Direction facing : Arrays.stream(Direction.values()).sorted(Comparator.comparingInt((direction) -> direction.getIndex())).toArray((i) -> new Direction[i])) {
            if (facing != Direction.DOWN) {
                BlockPos pos = blockPos1.offset(facing);
                if (pos.getY() <= blockPos3.getY()) {
                    Vec3d targetCenter = new Vec3d((double) blockPos3.getX() + 0.5, (double) blockPos3.getY() + 0.5, (double) blockPos3.getZ() + 0.5);
                    double distance = pos.toCenterPos().squaredDistanceTo(targetCenter);
                    if (enumFacing == null || distance < offset || distance == offset && facing == Direction.UP) {
                        offset = distance;
                        enumFacing = facing;
                    }
                }
            }
        }
        return enumFacing;
    }

    private BlockData getBlockData() {
        int startY = MathHelper.floor(mc.player.getEntityPos().getY());
        BlockPos targetPos = new BlockPos(
                MathHelper.floor(mc.player.getEntityPos().getX()),
                (this.stage != 0 && !this.shouldKeepY ? Math.min(startY, this.startY) : startY) - 1,
                MathHelper.floor(mc.player.getEntityPos().getZ())
        );
        if (!mc.world.getBlockState(targetPos).isReplaceable()) {
            return null;
        } else {
            ArrayList<BlockPos> positions = new ArrayList<>();
            for (int x = -4; x <= 4; x++) {
                for (int y = -4; y <= 0; y++) {
                    for (int z = -4; z <= 4; z++) {
                        BlockPos pos = targetPos.add(x, y, z);
                        if (!mc.world.getBlockState(pos).isReplaceable()
                                && !InventoryUtility.isBlockInteractable(mc.world.getBlockState(pos).getBlock())
                                && !(
                                PlayerUtility.getDistanceToBlock(pos) > mc.player.getBlockInteractionRange()
                        )
                                && (this.stage == 0 || this.shouldKeepY || pos.getY() < this.startY)) {
                            for (Direction facing : Arrays.stream(Direction.values()).sorted(Comparator.comparingInt((direction) -> direction.getIndex())).toArray((i) -> new Direction[i])) {
                                if (facing != Direction.DOWN) {
                                    BlockPos blockPos = pos.offset(facing);
                                    if (mc.world.getBlockState(blockPos).isReplaceable()) {
                                        positions.add(pos);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (positions.isEmpty()) {
                return null;
            } else {
                positions.sort(
                        Comparator.comparingDouble(
                                o -> o.getSquaredDistance(targetPos)
                        )
                );
                BlockPos blockPos = positions.get(0);
                Direction facing = this.getBestFacing(blockPos, targetPos);
                return facing == null ? null : new BlockData(blockPos, facing);
            }
        }
    }

    private void place(BlockPos blockPos, Direction enumFacing, Vec3d vec3) {
        final boolean isBlock = mc.player.getMainHandStack().getItem() instanceof BlockItem;
        if (isBlock && this.blockCount > 0) {
            BlockHitResult hitResult = new BlockHitResult(vec3, enumFacing, blockPos, false);
            if (mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult).isAccepted()) {
                if (!mc.player.isInCreativeMode()) {
                    this.blockCount--;
                }
                if (this.getSettings().isSwing()) {
                    mc.player.swingHand(Hand.MAIN_HAND);
                } else {
                    mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
                }
            }
        }
    }

    private Direction yawToFacing(float yaw) {
        if (yaw < -135.0F || yaw > 135.0F) {
            return Direction.NORTH;
        } else if (yaw < -45.0F) {
            return Direction.EAST;
        } else {
            return yaw < 45.0F ? Direction.SOUTH : Direction.WEST;
        }
    }

    private double distanceToEdge(Direction enumFacing) {
        switch (enumFacing) {
            case NORTH:
                return mc.player.getEntityPos().getZ() - Math.floor(mc.player.getEntityPos().getZ());
            case EAST:
                return Math.ceil(mc.player.getEntityPos().getX()) - mc.player.getEntityPos().getX();
            case SOUTH:
                return Math.ceil(mc.player.getEntityPos().getZ()) - mc.player.getEntityPos().getZ();
            case WEST:
            default:
                return mc.player.getEntityPos().getX() - Math.floor(mc.player.getEntityPos().getX());
        }
    }

    private float getSpeed() {
        if (!mc.player.isOnGround()) {
            return (float) this.getSettings().getAirMotion();
        } else {
            return mc.player.hasStatusEffect(StatusEffects.SPEED)
                    ? (float) this.getSettings().getSpeedMotion()
                    : (float) this.getSettings().getGroundMotion();
        }
    }

    private double getRandomOffset() {
        return 0.2155 - RandomUtility.getRandomDouble(1.0E-4, 9.0E-4);
    }

    private float getCurrentYaw() {
        return (float) MoveUtility.getDirection(mc.player.getYaw(), mc.player.forwardSpeed, mc.player.sidewaysSpeed);
    }

    private boolean isDiagonal(float yaw) {
        float absYaw = Math.abs(yaw % 90.0F);
        return absYaw > 20.0F && absYaw < 70.0F;
    }

    private boolean isTowering() {
        if (mc.player.isOnGround() && MoveUtility.isMoving() && !PlayerUtility.isNoAirBelow()) {
            boolean keepY = this.getSettings().getKeepYMode() == KeepYMode.TELLY;
            boolean tower = this.getSettings().getTowerMode() == TowerMode.TELLY;
            return keepY && this.stage > 0 || tower && mc.options.jumpKey.isPressed();
        } else {
            return false;
        }
    }

    public ScaffoldPrediction() {
        super("ScaffoldPrediction", "Port from OpenMYAU.", ModuleCategory.WORLD);
    }

    public int getSlot() {
        return this.lastSlot;
    }

    public ScaffoldPredictionSettings getSettings() {
        return settings;
    }

    @Override
    public void onEnable() {
        if (mc.player != null) {
            this.lastSlot = mc.player.getInventory().getSelectedSlot();
        } else {
            this.lastSlot = -1;
        }
        this.blockCount = -1;
        this.rotationTick = 3;
        this.yaw = -180.0F;
        this.pitch = 0.0F;
        this.canRotate = false;
        this.towerTick = 0;
        this.towerDelay = 0;
        this.towering = false;
    }

    @Override
    public void onDisable() {
        if (mc.player != null && this.lastSlot != -1) {
            mc.player.getInventory().setSelectedSlot(this.lastSlot);
        }
    }

    public static class BlockData {
        private final BlockPos blockPos;
        private final Direction facing;

        public BlockData(BlockPos blockPos, Direction enumFacing) {
            this.blockPos = blockPos;
            this.facing = enumFacing;
        }

        public BlockPos blockPos() {
            return this.blockPos;
        }

        public Direction facing() {
            return this.facing;
        }
    }
}