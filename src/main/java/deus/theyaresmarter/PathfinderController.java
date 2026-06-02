package deus.theyaresmarter;

import de.bsommerfeld.pathetic.api.pathing.INeighborStrategy;
import de.bsommerfeld.pathetic.api.pathing.NeighborStrategies;
import de.bsommerfeld.pathetic.api.pathing.Pathfinder;
import de.bsommerfeld.pathetic.api.pathing.configuration.PathfinderConfiguration;
import de.bsommerfeld.pathetic.api.pathing.processing.ValidationProcessor;
import de.bsommerfeld.pathetic.api.pathing.result.Path;
import de.bsommerfeld.pathetic.api.provider.NavigationPointProvider;
import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import de.bsommerfeld.pathetic.engine.factory.AStarPathfinderFactory;
import deus.brainless.Brainless;
import deus.brainless.pathfinding.DefaultWalkValidator;
import deus.theyaresmarter.mixin.MobAccessor;
import deus.theyaresmarter.mixin.MobPathfinderAccessor;
import deus.theyaresmarter.util.PoscArea;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.Mob;
import net.minecraft.core.entity.MobPathfinder;
import net.minecraft.core.util.helper.MathHelper;
import net.minecraft.core.world.pos.TilePos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class PathfinderController {

	private final Mob mob;

	private final AtomicBoolean computing = new AtomicBoolean(false);

	protected int maxIterations = 10_000;
	protected int maxLength = 128;
	protected INeighborStrategy strategy = NeighborStrategies.DIAGONAL_3D;
	protected double arrivalThreshold = 1.0D;
	protected int recomputeInterval = 60;

	protected PathfinderConfiguration pathFinderConfig;
	protected NavigationPointProvider provider = (pos, ctx) -> () -> true;
	protected Pathfinder pathfinder;
	private static final double RETARGET_THRESHOLD = 2.0;

	@Nullable
	private TilePos targetTilePos;

	@Nullable
	private TilePos lastTargetTilePos;

	@Nullable
	private Path currentPath;

	@Nullable
	private List<PathPosition> nodes;

	private boolean pathValid = false;
	private int pathRetryTimer = 0;
	private int pathIndex = 0;

	public PathfinderController(Mob mob) {
		this.mob = mob;

		this.pathFinderConfig = PathfinderConfiguration.builder()
			.async(true)
			.fallback(true)
			.maxIterations(maxIterations)
			.maxLength(maxLength)
			.neighborStrategy(strategy)
			.validationProcessors(buildValidators())
			.provider(provider)
			.build();

		this.pathfinder =
			new AStarPathfinderFactory().createPathfinder(pathFinderConfig);
	}

	private static float wrapDegrees(float deg) {

		while (deg < -180.0F) {
			deg += 360.0F;
		}

		while (deg >= 180.0F) {
			deg -= 360.0F;
		}

		return deg;
	}

	protected List<ValidationProcessor> buildValidators() {
		return List.of(
			new MobWalkValidator(mob.world)
		);
	}

	public void tick() {
		pathThinking();
		pathMotion();
	}

	public void setTarget(TilePos target) {
		if (target == null || target.equals(this.targetTilePos)) {
			return;
		}
		if (this.targetTilePos != null) {
			double dx = target.x - this.targetTilePos.x;
			double dz = target.z - this.targetTilePos.z;
			if (dx*dx + dz*dz < RETARGET_THRESHOLD * RETARGET_THRESHOLD) {
				this.targetTilePos = target;
				return;
			}
		}

		this.lastTargetTilePos = this.targetTilePos;
		this.targetTilePos = target;
		invalidatePath("target moved significantly");
	}

	public Optional<TilePos> getTargetTilePos() {
		return targetTilePos == null
			? Optional.empty()
			: Optional.of(new TilePos(targetTilePos));
	}

	public boolean hasPath() {
		return pathValid && currentPath != null;
	}

	public boolean isMoving() {
		return hasPath() && pathIndex < nodeCount();
	}

	protected void pathThinking() {

		if (!mob.onGround) return;
		if (targetTilePos == null) return;
		if (computing.get()) return;

		double dx = targetTilePos.x - mob.x;
		double dy = targetTilePos.y - mob.y;
		double dz = targetTilePos.z - mob.z;

		if (dx * dx + dy * dy + dz * dz <=
			arrivalThreshold * arrivalThreshold) {
			return;
		}

		pathRetryTimer++;

		boolean targetChanged =
			currentPath == null ||
				nodes == null ||
				!targetTilePos.equals(lastTargetTilePos);

		if (pathValid &&
			pathRetryTimer < recomputeInterval &&
			!targetChanged) {
			return;
		}

		PathPosition start =
			new PathPosition((int) mob.x, (int) mob.y, (int) mob.z);

		PathPosition target =
			new PathPosition(
				targetTilePos.x,
				targetTilePos.y,
				targetTilePos.z
			);

		computing.set(true);

		pathfinder.findPath(start, target)
			.ifPresent(result -> {
				computing.set(false);

				applyPath(result.getPath());

				TheyAreSmarter.LOGGER.debug(
					"[AI] PATH FOUND length={}",
					result.getPath().length()
				);
			})
			.orElse(result -> {
				computing.set(false);

				TheyAreSmarter.LOGGER.debug(
					"[AI] PATH FAILED start={} target={} status={}",
					start,
					target,
					result
				);

				pathRetryTimer = recomputeInterval / 2;
			})
			.exceptionally(ex -> {
				computing.set(false);

				TheyAreSmarter.LOGGER.error(
					"[AI] PATH EXCEPTION",
					ex
				);

				pathRetryTimer = recomputeInterval / 2;
			});
	}

	protected void pathMotion() {
		if (!pathValid || nodes == null) return;
		if (pathIndex >= nodeCount()) {
			invalidatePath("path complete");
			return;
		}

		int lookahead = Math.min(pathIndex + 1, nodeCount() - 1);
		PathPosition next = nodes.get(lookahead);

		double tx = next.getX() + 0.5;
		double tz = next.getZ() + 0.5;

		double dx = tx - mob.x;
		double dy = next.getY() - mob.y;
		double dz = tz - mob.z;

		MobAccessor accessor = (MobAccessor) mob;
		MobPathfinder self = (MobPathfinder)(Object) mob;
		Entity target = self.getTarget();

		accessor.setMoveStrafing(0.0F);
		accessor.setMoveForward(0.0F);

		if (target != null) {
			// Face target, strafe toward path node (mirrors vanilla hasAttacked block)
			float nodeYaw = (float)(Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;

			double tdx = target.x - mob.x;
			double tdz = target.z - mob.z;
			mob.yRot = (float)(Math.atan2(tdz, tdx) * 180.0D / Math.PI) - 90.0F;

			float strafeDelta = (nodeYaw - mob.yRot + 90.0F) * (float)Math.PI / 180.0F;
			accessor.setMoveStrafing(-MathHelper.sin(strafeDelta) * accessor.getMoveSpeed());
			accessor.setMoveForward(MathHelper.cos(strafeDelta) * accessor.getMoveSpeed());
		} else {
			// No target — steer toward path node normally
			float targetYaw = (float)(Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
			float diff = wrapDegrees(targetYaw - mob.yRot);
			diff = Math.max(-30.0F, Math.min(30.0F, diff));
			mob.yRot += diff;
			accessor.setMoveForward(accessor.getMoveSpeed());
			accessor.setMoveStrafing(0.0F);
		}

		accessor.setIsJumping(dy > 1.0D && mob.onGround);

		double dist = Math.sqrt(dx * dx + dz * dz);
		if (dist < arrivalThreshold) {
			pathIndex++;
		}
	}

	private void applyPath(Path path) {

		this.currentPath = path;
		this.nodes = buildNodeList(path);

		this.pathValid = true;
		this.pathRetryTimer = 0;
		this.pathIndex = 0;

		this.lastTargetTilePos = this.targetTilePos;
	}

	private void invalidatePath(String reason) {

		TheyAreSmarter.LOGGER.debug(
			"[AI] PATH INVALIDATED reason={}",
			reason
		);

		pathValid = false;
		pathRetryTimer = recomputeInterval;

		currentPath = null;
		nodes = null;

		pathIndex = 0;
	}

	private List<PathPosition> buildNodeList(Path path) {

		List<PathPosition> list =
			new ArrayList<>();

		for (PathPosition pos : path) {
			list.add(pos);
		}

		return list;
	}

	private int nodeCount() {
		return nodes == null
			? 0
			: nodes.size();
	}

	@Nullable
	public TilePos roamRandomPath(PoscArea.Area2D area) {

		int minX = Math.min(area.a().x(), area.b().x());
		int maxX = Math.max(area.a().x(), area.b().x());
		int minZ = Math.min(area.a().z(), area.b().z());
		int maxZ = Math.max(area.a().z(), area.b().z());

		int y = area.a().y();

		for (int i = 0; i < 10; i++) {

			int x = minX + ((MobAccessor)this.mob).getRandom().nextInt(maxX - minX + 1);
			int z = minZ + ((MobAccessor)this.mob).getRandom().nextInt(maxZ - minZ + 1);

			if (!mob.world.isAirBlock(x, y, z)) {
				continue;
			}

			if (mob.world.isAirBlock(x, y - 1, z)) {
				continue;
			}

			return new TilePos(x, y, z);
		}

		return null;
	}

	public void roamRandomPath() {
		MobAccessor accessor = (MobAccessor) mob;
		TilePos best = null;
		float bestWeight = -99999.0F;

		for (int i = 0; i < 10; i++) {
			int x = MathHelper.floor(mob.x + accessor.getRandom().nextInt(13) - 6.0F);
			int y = MathHelper.floor(mob.y + accessor.getRandom().nextInt(7) - 3.0F);
			int z = MathHelper.floor(mob.z + accessor.getRandom().nextInt(13) - 6.0F);
			TilePos candidate = new TilePos(x, y, z);
			float weight = ((MobPathfinderAccessor)(Object)mob).callGetBlockPathWeight(candidate);
			if (weight > bestWeight) {
				bestWeight = weight;
				if (best != null) best.set(x, y, z);
				else best = new TilePos(x, y, z);
			}
		}

		if (best != null) setTarget(best);
	}
}
