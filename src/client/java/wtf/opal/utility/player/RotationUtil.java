package wtf.opal.utility.player;

import net.minecraft.entity.EntityPose;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class RotationUtil {
    
    private RotationUtil() {
    }

    public static float wrapAngleDiff(float angle, float target) {
        return target + MathHelper.wrapAngleTo180_float(angle - target);
    }

    public static float smoothAngle(float angle, float smoothFactor) {
        return angle * (0.5f + 0.5f * (1.0f - Math.max(0.0f, Math.min(1.0f, smoothFactor + RandomUtil.nextFloat(-0.1f, 0.1f)))));
    }

    public static float quantizeAngle(float angle) {
        return (float) ((double) angle - (double) angle % (double) 0.0096f);
    }


    public static float clampAngle(float angle, float maxAngle) {
        maxAngle = Math.max(0.0f, Math.min(180.0f, maxAngle));
        if (angle > maxAngle) {
            angle = maxAngle;
        } else if (angle < -maxAngle) {
            angle = -maxAngle;
        }
        return angle;
    }

    public static float[] getRotationsTo(double targetX, double targetY, double targetZ, float currentYaw, float currentPitch) {
        return RotationUtil.getRotations(targetX, targetY, targetZ, currentYaw, currentPitch, 180.0f, 0.0f);
    }

    public static float[] getRotations(double targetX, double targetY, double targetZ, float currentYaw, float currentPitch, float maxAngle, float smoothFactor) {
        double horizontalDistance = Math.sqrt(targetX * targetX + targetZ * targetZ);
        float yawDelta = MathHelper.wrapDegrees((float) (Math.atan2(targetZ, targetX) * 180.0 / Math.PI) - 90.0f - currentYaw);
        float pitchDelta = MathHelper.wrapDegrees((float) (-Math.atan2(targetY, horizontalDistance) * 180.0 / Math.PI) - currentPitch);
        yawDelta = Math.abs(yawDelta) <= 1.0f ? 0.0f : RotationUtil.smoothAngle(RotationUtil.clampAngle(yawDelta, maxAngle), smoothFactor);
        pitchDelta = Math.abs(pitchDelta) <= 1.0f ? 0.0f : RotationUtil.smoothAngle(RotationUtil.clampAngle(pitchDelta, maxAngle), smoothFactor);
        return new float[]{RotationUtil.quantizeAngle(currentYaw + yawDelta), RotationUtil.quantizeAngle(currentPitch + pitchDelta)};
    }

    public static HitResult rayTrace(float yaw, float pitch, double reachDistance, float partialTicks) {
        return RaycastUtility.raycastBlock(reachDistance, false, yaw, pitch, 
            net.minecraft.client.MinecraftClient.getInstance().player.getEyePos());
    }
}