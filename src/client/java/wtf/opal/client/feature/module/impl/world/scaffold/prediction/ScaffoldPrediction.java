package wtf.opal.client.feature.module.impl.world.scaffold.prediction;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EntityPose;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
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
import wtf.opal.event.impl.game.PreGameTickEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.utility.misc.math.RandomUtility;
import wtf.opal.utility.player.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import static wtf.opal.client.Constants.mc;
import static wtf.opal.client.feature.module.impl.world.scaffold.prediction.ScaffoldPredictionSettings.*;

public class ScaffoldPrediction extends Module {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private final ScaffoldPredictionSettings settings = new ScaffoldPredictionSettings(this);

    private static final double[] placeOffsets = new double[]{
            0.03125,
            0.09375,
            0.15625,
            0.21875,
            0.28125,
            0.34375,
            0.40625,
            0.46875,
            0.53125,
            0.59375,
            0.65625,
            0.71875,
            0.78125,
            0.84375,
            0.90625,
            0.96875
    };
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
    private Direction targetFacing = null;

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

    @Subscribe
    public void onUpdate(PreGameTickEvent event) {
        if (this.rotationTick > 0) {
            this.rotationTick--;
        }
        if (mc.player.isOnGround()) {
            if (this.stage > 0) {
                this.stage--;
            }
            if (this.stage < 0) {
                this.stage++;
            }
            if (this.stage == 0
                    && this.getSettings().getKeepYMode() != KeepYMode.NONE
                    && (!this.getSettings().isKeepYonPress() || mc.player.isUsingItem())
                    && (!this.getSettings().isDisableWhileJumpActive() || !mc.player.hasStatusEffect(StatusEffects.JUMP_BOOST))
                    && !mc.options.jumpKey.isPressed()) {
                this.stage = 1;
            }
            this.startY = this.shouldKeepY ? this.startY : MathHelper.floor(mc.player.getY());
            this.shouldKeepY = false;
            this.towering = false;
        }
        if (this.canPlace()) {
            var mainHand = mc.player.getMainHandStack();
            var offHand = mc.player.getOffHandStack();
            int count = 0;
            if (mainHand.getItem() instanceof BlockItem) {
                count = mainHand.getCount();
            } else if (offHand.getItem() instanceof BlockItem) {
                count = offHand.getCount();
            }
            this.blockCount = Math.min(this.blockCount, count);
            if (this.blockCount <= 0) {
                int currentSlot = mc.player.getInventory().getSelectedSlot();
                if (this.blockCount == 0) {
                    currentSlot--;
                }
                for (int i = currentSlot; i > currentSlot - 9; i--) {
                    int hotbarSlot = (i % 9 + 9) % 9;
                    var candidate = mc.player.getInventory().getStack(hotbarSlot);
                    if (candidate.getItem() instanceof BlockItem) {
                        mc.player.getInventory().setSelectedSlot(hotbarSlot);
                        this.blockCount = candidate.getCount();
                        break;
                    }
                }
                float currentYaw = this.getCurrentYaw();
                float yawDiffTo180 = RotationUtil.wrapAngleDiff(currentYaw - 180.0F, mc.player.lastYaw);
                float diagonalYaw = this.isDiagonal(currentYaw)
                        ? yawDiffTo180
                        : RotationUtil.wrapAngleDiff(currentYaw - 135.0F * ((currentYaw + 180.0F) % 90.0F < 45.0F ? 1.0F : -1.0F), mc.player.lastYaw);
                if (!this.canRotate) {
                    switch (this.settings.getRotationMode()) {
                        case DEFAULT:
                            if (this.yaw == -180.0F && this.pitch == 0.0F) {
                                this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
                                this.pitch = RotationUtil.quantizeAngle(85.0F);
                            } else {
                                this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
                            }
                            break;
                        case BACKWARDS:
                            if (this.yaw == -180.0F && this.pitch == 0.0F) {
                                this.yaw = RotationUtil.quantizeAngle(yawDiffTo180);
                                this.pitch = RotationUtil.quantizeAngle(85.0F);
                            } else {
                                this.yaw = RotationUtil.quantizeAngle(yawDiffTo180);
                            }
                            break;
                        case SIDEWAYS:
                            if (this.yaw == -180.0F && this.pitch == 0.0F) {
                                this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
                                this.pitch = RotationUtil.quantizeAngle(85.0F);
                            } else {
                                this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
                            }
                    }
                }
                BlockData blockData = this.getBlockData();
                Vec3d hitVec = null;
                if (blockData != null) {
                    double[] x = placeOffsets;
                    double[] y = placeOffsets;
                    double[] z = placeOffsets;
                    switch (blockData.facing()) {
                        case NORTH:
                            z = new double[]{0.0};
                            break;
                        case EAST:
                            x = new double[]{1.0};
                            break;
                        case SOUTH:
                            z = new double[]{1.0};
                            break;
                        case WEST:
                            x = new double[]{0.0};
                            break;
                        case DOWN:
                            y = new double[]{0.0};
                            break;
                        case UP:
                            y = new double[]{1.0};
                    }
                    float bestYaw = -180.0F;
                    float bestPitch = 0.0F;
                    float bestDiff = 0.0F;
                    for (double dx : x) {
                        for (double dy : y) {
                            for (double dz : z) {
                                double relX = (double) blockData.blockPos().getX() + dx - mc.player.getX();
                                double relY = (double) blockData.blockPos().getY() + dy - mc.player.getY() - (double) mc.player.getEyeHeight(EntityPose.STANDING);
                                double relZ = (double) blockData.blockPos().getZ() + dz - mc.player.getZ();
                                float baseYaw = RotationUtil.wrapAngleDiff(this.yaw, mc.player.lastYaw);
                                float[] rotations = RotationUtil.getRotationsTo(relX, relY, relZ, baseYaw, this.pitch);
                                HitResult mop = RotationUtil.rayTrace(rotations[0], rotations[1], mc.player.getBlockInteractionRange(), 1.0F);
                                if (mop != null
                                        && mop.getType() == HitResult.Type.BLOCK
                                        && mop.getPos() == blockData.blockPos().toCenterPos()
                                        && ((BlockHitResult) mop).getSide() == blockData.facing()) {
                                    float totalDiff = Math.abs(rotations[0] - baseYaw) + Math.abs(rotations[1] - this.pitch);
                                    if (bestYaw == -180.0F && bestPitch == 0.0F || totalDiff < bestDiff) {
                                        bestYaw = rotations[0];
                                        bestPitch = rotations[1];
                                        bestDiff = totalDiff;
                                        hitVec = mop.getPos();
                                    }
                                }
                            }
                        }
                    }
                    if (bestYaw != -180.0F || bestPitch != 0.0F) {
                        this.yaw = bestYaw;
                        this.pitch = bestPitch;
                        this.canRotate = true;
                    }
                }
                if (this.canRotate && mc.options.forwardKey.isPressed() && Math.abs(MathHelper.wrapDegrees(yawDiffTo180 - this.yaw)) < 90.0F) {
                    switch (this.settings.getRotationMode()) {
                        case RotationMode.BACKWARDS:
                            this.yaw = RotationUtil.quantizeAngle(yawDiffTo180);
                            break;
                        case RotationMode.SIDEWAYS:
                            this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
                    }
                }
                if (this.settings.getRotationMode() != RotationMode.NONE) {
                    float targetYaw = this.yaw;
                    float targetPitch = this.pitch;
                    if (this.towering && (mc.player.getVelocity().getY() > 0.0 || mc.player.getEntityPos().getY() > (double) (this.startY + 1))) {
                        float yawDiff = MathHelper.wrapDegrees(this.yaw - mc.player.lastYaw);
                        float tolerance = (this.rotationTick >= 2 ? RandomUtility.getRandomFloat(90.0F, 95.0F) : RandomUtility.getRandomFloat(30.0F, 35.0F)) * (this.getSettings().getRotationSpeed());
                        if (Math.abs(yawDiff) > tolerance) {
                            float clampedYaw = RotationUtil.clampAngle(yawDiff, tolerance);
                            targetYaw = RotationUtil.quantizeAngle(mc.player.lastYaw + clampedYaw);
                            this.rotationTick = Math.max(this.rotationTick, 1);
                        }
                    }
                    if (this.isTowering()) {
                        float yawDelta = MathHelper.wrapDegrees(mc.player.getYaw() - mc.player.lastYaw);
                        targetYaw = RotationUtil.quantizeAngle(mc.player.lastYaw + yawDelta * RandomUtility.getRandomFloat(0.98F, 0.99F));
                        targetPitch = RotationUtil.quantizeAngle(RandomUtility.getRandomFloat(30.0F, 80.0F));
                        this.rotationTick = 3;
                        this.towering = true;
                    }
                    mc.player.setYaw(targetYaw);
                    mc.player.setPitch(targetPitch);
                }
                if (blockData != null && hitVec != null && this.rotationTick <= 0) {
                    this.place(blockData.blockPos(), blockData.facing(), hitVec);
                    if (this.settings.isMultiplace()) {
                        for (int i = 0; i < 3; i++) {
                            blockData = this.getBlockData();
                            if (blockData == null) {
                                break;
                            }
                            HitResult mop = RotationUtil.rayTrace(this.yaw, this.pitch, mc.player.getBlockInteractionRange(), 1.0F);
                            if (mop != null
                                    && mop.getType() == HitResult.Type.BLOCK
                                    && mop.getPos().equals(blockData.blockPos())
                                    && ((BlockHitResult) mop).getSide() == blockData.facing()) {
                                this.place(blockData.blockPos(), blockData.facing(), mop.getPos());
                            } else {
                                hitVec = BlockUtil.getClickVec(blockData.blockPos(), blockData.facing());
                                double dx = hitVec.getX() - mc.player.getX();
                                double dy = hitVec.getY() - mc.player.getY() - (double) mc.player.getEyeHeight(EntityPose.STANDING);
                                double dz = hitVec.getZ() - mc.player.getZ();
                                float[] rotations = RotationUtil.getRotationsTo(dx, dy, dz, mc.player.lastYaw, mc.player.lastPitch);
                                if (!(Math.abs(rotations[0] - this.yaw) < 120.0F) || !(Math.abs(rotations[1] - this.pitch) < 60.0F)) {
                                    break;
                                }
                                mop = RotationUtil.rayTrace(rotations[0], rotations[1], mc.player.getBlockInteractionRange(), 1.0F);
                                if (mop == null
                                        || mop.getType() != HitResult.Type.BLOCK
                                        || !mop.getPos().equals(blockData.blockPos())
                                        || ((BlockHitResult) mop).getSide() != blockData.facing()) {
                                    break;
                                }
                                this.place(blockData.blockPos(), blockData.facing(), mop.getPos());
                            }
                        }
                    }
                }
                if (this.targetFacing != null) {
                    if (this.rotationTick <= 0) {
                        int playerBlockX = MathHelper.floor(mc.player.getX());
                        int playerBlockY = MathHelper.floor(mc.player.getY());
                        int playerBlockZ = MathHelper.floor(mc.player.getZ());
                        BlockPos belowPlayer = new BlockPos(playerBlockX, playerBlockY - 1, playerBlockZ);
                        hitVec = BlockUtil.getHitVec(belowPlayer, this.targetFacing, this.yaw, this.pitch);
                        this.place(belowPlayer, this.targetFacing, hitVec);
                    }
                    this.targetFacing = null;
                } else if (this.getSettings().getKeepYMode() == KeepYMode.EXTRA && this.stage > 0 && !mc.player.isOnGround()) {
                    int nextBlockY = MathHelper.floor(mc.player.getY() + mc.player.getVelocity().getY());
                    if (nextBlockY <= this.startY && mc.player.getY() > (double) (this.startY + 1)) {
                        this.shouldKeepY = true;
                        blockData = this.getBlockData();
                        if (blockData != null && this.rotationTick <= 0) {
                            hitVec = BlockUtil.getHitVec(blockData.blockPos(), blockData.facing(), this.yaw, this.pitch);
                            this.place(blockData.blockPos(), blockData.facing(), hitVec);
                        }
                    }
                }
            }
        }
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