import com.autorotate.solver.Aim;
import com.autorotate.solver.Bias;
import com.autorotate.solver.StateSolver;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.properties.RotationSegment;

public final class VerifyMath {

    private static int failures;
    private static int mark;

    public static void main(String[] args) {
        horizontalYaw();
        keepHorizontal();
        keepHorizontalAtAnyYaw();
        lookVertical();
        lookVerticalAtAnyYaw();
        segmentYaw();
        hingeBias();
        System.out.println(failures == 0 ? "ALL MATH CHECKS PASSED" : "TOTAL FAILURES: " + failures);
        if (failures != 0) {
            System.exit(1);
        }
    }

    private static void horizontalYaw() {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            for (int start = -360; start <= 360; start++) {
                float solved = Aim.horizontalYaw(direction, start);
                check(Direction.fromYRot(solved) == direction, "horizontalYaw " + direction + " from " + start);
                float turn = Math.abs(Mth.wrapDegrees(solved - start));
                float snap = Math.abs(Mth.wrapDegrees(direction.toYRot() - start));
                check(turn <= snap + 0.001F, "horizontalYaw turns further than a snap " + direction + " from " + start);
            }
        }
        report("horizontalYaw bucket and minimality");
    }

    private static void keepHorizontal() {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            for (int start = -180; start <= 180; start += 3) {
                for (int pitch = -90; pitch <= 90; pitch += 3) {
                    float yaw = Aim.horizontalYaw(direction, start);
                    check(nearest(yaw, pitched(pitch)) == direction, "keepHorizontal " + direction + " yaw " + yaw + " pitch " + pitch);
                }
            }
        }
        report("keepHorizontal keeps the wanted face");
    }

    private static void keepHorizontalAtAnyYaw() {
        for (int pitch = -90; pitch <= 90; pitch += 5) {
            for (int stale = -180; stale <= 180; stale += 5) {
                check(nearest(stale, pitched(pitch)).getAxis().isHorizontal(), "keepHorizontal at stale yaw " + stale + " pitch " + pitch);
            }
        }
        report("keepHorizontal holds at any yaw");
    }

    private static void lookVertical() {
        for (Direction vertical : new Direction[]{Direction.UP, Direction.DOWN}) {
            for (int yaw = -180; yaw <= 180; yaw += 3) {
                for (int pitch = -90; pitch <= 90; pitch += 3) {
                    check(nearest(yaw, Aim.lookVertical(vertical, pitch)) == vertical, "lookVertical " + vertical + " yaw " + yaw + " pitch " + pitch);
                }
            }
        }
        report("lookVertical reaches the vertical face");
    }

    private static void lookVerticalAtAnyYaw() {
        for (Direction vertical : new Direction[]{Direction.UP, Direction.DOWN}) {
            float pitch = Aim.lookVertical(vertical, 0);
            for (int stale = -180; stale <= 180; stale += 5) {
                check(nearest(stale, pitch) == vertical, "lookVertical " + vertical + " at stale yaw " + stale);
            }
        }
        report("lookVertical holds at any yaw");
    }

    private static void segmentYaw() {
        for (float offset : new float[]{180.0F, 0.0F}) {
            for (int segment = 0; segment <= 15; segment++) {
                for (int start = -180; start <= 180; start++) {
                    float solved = Aim.segmentYaw(segment, start, offset);
                    check(RotationSegment.convertToSegment(solved + offset) == segment, "segmentYaw " + segment + " offset " + offset + " from " + start);
                }
            }
        }
        report("segmentYaw reproduces ROTATION_16");
    }

    private static void hingeBias() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (boolean wantLeft : new boolean[]{true, false}) {
                Bias bias = StateSolver.hingeBias(facing, wantLeft);
                double d = bias.x() == null ? 0.5 : bias.x();
                double e = bias.z() == null ? 0.5 : bias.z();
                int j = facing.getStepX();
                int k = facing.getStepZ();
                boolean left = (j >= 0 || !(e < 0.5)) && (j <= 0 || !(e > 0.5)) && (k >= 0 || !(d > 0.5)) && (k <= 0 || !(d < 0.5));
                check(left == wantLeft, "hingeBias " + facing + " wantLeft " + wantLeft);
            }
        }
        report("hingeBias matches the door tie-break");
    }

    private static float pitched(int pitch) {
        Float kept = Aim.keepHorizontal(pitch);
        return kept == null ? pitch : kept;
    }

    private static Direction nearest(float yaw, float pitch) {
        double y = Math.toRadians(yaw);
        double p = Math.toRadians(pitch);
        return Direction.getApproximateNearest(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
    }

    private static void check(boolean ok, String message) {
        if (!ok && failures++ < 25) {
            System.out.println("FAIL " + message);
        }
    }

    private static void report(String name) {
        System.out.println(name + ": " + (failures == mark ? "OK" : (failures - mark) + " fails"));
        mark = failures;
    }
}
