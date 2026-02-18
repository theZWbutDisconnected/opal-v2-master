package wtf.opal.utility.player;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class BlockUtil {
    
    private BlockUtil() {
    }
    
    public static Vec3d getHitVec(BlockPos blockPos, Direction facing, float yaw, float pitch) {
        Vec3d blockCenter = blockPos.toCenterPos();
        
        double hitX = blockCenter.getX();
        double hitY = blockCenter.getY();
        double hitZ = blockCenter.getZ();
        switch (facing) {
            case DOWN:
                hitY = blockPos.getY();
                break;
            case UP:
                hitY = blockPos.getY() + 1;
                break;
            case NORTH:
                hitZ = blockPos.getZ();
                break;
            case SOUTH:
                hitZ = blockPos.getZ() + 1;
                break;
            case WEST:
                hitX = blockPos.getX();
                break;
            case EAST:
                hitX = blockPos.getX() + 1;
                break;
        }
        
        return new Vec3d(hitX, hitY, hitZ);
    }
    
    public static Vec3d getClickVec(BlockPos blockPos, Direction facing) {
        return getHitVec(blockPos, facing, 0, 0);
    }
}