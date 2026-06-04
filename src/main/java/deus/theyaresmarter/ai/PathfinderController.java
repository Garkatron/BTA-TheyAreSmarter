package deus.theyaresmarter.ai;

import de.bsommerfeld.pathetic.api.pathing.Pathfinder;
import de.bsommerfeld.pathetic.api.pathing.configuration.PathfinderConfiguration;
import de.bsommerfeld.pathetic.api.pathing.processing.ValidationProcessor;
import de.bsommerfeld.pathetic.api.pathing.result.Path;
import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import de.bsommerfeld.pathetic.engine.factory.AStarPathfinderFactory;
import deus.theyaresmarter.BlockCostProcessor;
import deus.theyaresmarter.TheyAreSmarter;
import deus.theyaresmarter.mixin.MobAccessor;
import deus.theyaresmarter.mixin.MobPathfinderAccessor;
import deus.theyaresmarter.util.PoscArea;
import deus.theyaresmarter.util.Signal;
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
	private final PathfinderSettings settings;

	private final AtomicBoolean computing = new AtomicBoolean(false);

	private final PathfinderConfiguration pathFinderConfig;
	private final Pathfinder pathfinder;

	@Nullable
	private volatile Path pendingPath = null;
	@Nullable
	private TilePos targetTilePos;
	@Nullable
	private TilePos lastTargetTilePos;
	@Nullable
	private Path currentPath;
	@Nullable
	private List<PathPosition> nodes;

	@Nullable
	private Entity trackedEntity = null;

	private boolean pathValid = false;
	private int pathRetryTimer = 0;
	private int pathIndex = 0;

	public Signal<Void> onReachTarget = new Signal<>();


	/**
	 * Creates a controller with default settings.
	 */
	public PathfinderController(Mob mob) {
		this(mob, PathfinderSettings.builder().build());
	}

	/**
	 * Creates a controller with fully custom settings.
	 */
	public PathfinderController(Mob mob, PathfinderSettings settings) {
		this.mob = mob;
		this.settings = settings;

		this.pathFinderConfig = PathfinderConfiguration.builder()
			.async(settings.async)
			.fallback(settings.fallback)
			.maxIterations(settings.maxIterations)
			.maxLength(settings.maxLength)
			.neighborStrategy(settings.neighborStrategy)
			.heuristicWeights(settings.heuristicWeights)
			.heuristicStrategy(settings.heuristicStrategy)
			.costProcessor(List.of(new BlockCostProcessor(mob.world)))
			.validationProcessors(buildValidators())
			.provider(settings.navigationPointProvider)
			.build();

		this.pathfinder = new AStarPathfinderFactory().createPathfinder(pathFinderConfig);
	}

	private static float wrapDegrees(float deg) {
		while (deg < -180.0F) deg += 360.0F;
		while (deg >= 180.0F) deg -= 360.0F;
		return deg;
	}

	public PathfinderSettings getSettings() {
		return settings;
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

	public void setTarget(@Nullable TilePos target) {
		if (target == null) {
			if (this.targetTilePos == null) return;
			this.targetTilePos = null;
			this.lastTargetTilePos = null;
			invalidatePath("target cleared");
			return;
		}

		if (this.targetTilePos != null) {
			double dx = target.x - this.targetTilePos.x;
			double dz = target.z - this.targetTilePos.z;
			if (dx * dx + dz * dz < settings.retargetThreshold * settings.retargetThreshold) {
				return;
			}
		}

		this.lastTargetTilePos = this.targetTilePos;
		this.targetTilePos = new TilePos(target);
		invalidatePath("target moved significantly");
	}

	public void tick() {
		pathThinking();
		pathMotion();
	}

	protected List<ValidationProcessor> buildValidators() {
		return List.of(new MobWalkValidator(mob.world));
	}

	public void setTrackedEntity(@Nullable Entity entity) {
		this.trackedEntity = entity;
		if (entity == null) setTarget(null);
	}

	public void stop() {
		trackedEntity = null;
		targetTilePos = null;
		lastTargetTilePos = null;

		invalidatePath("stopped");

		MobAccessor accessor = (MobAccessor) mob;
		accessor.setMoveForward(0.0F);
		accessor.setMoveStrafing(0.0F);
		accessor.setIsJumping(false);
	}

	protected void pathThinking() {
		if (trackedEntity != null) {
			if (!trackedEntity.isAlive()) {
				trackedEntity = null;
				setTarget(null);
				return;
			}
			setTarget(new TilePos(
				MathHelper.floor(trackedEntity.x),
				MathHelper.floor(trackedEntity.y),
				MathHelper.floor(trackedEntity.z)
			));
		}

		if (pendingPath != null) {
			applyPath(pendingPath);
			pendingPath = null;
		}
		if (!mob.onGround) return;
		if (targetTilePos == null) return;
		if (computing.get()) return;

		double dx = targetTilePos.x - mob.x;
		double dy = targetTilePos.y - mob.y;
		double dz = targetTilePos.z - mob.z;
		double threshold = settings.arrivalThreshold;

		if (dx * dx + dy * dy + dz * dz <= threshold * threshold) return;

		pathRetryTimer++;

		boolean targetChanged = currentPath == null
			|| nodes == null
			|| !targetTilePos.equals(lastTargetTilePos);

		if (pathValid && pathRetryTimer < settings.recomputeInterval && !targetChanged) return;

		PathPosition start = new PathPosition(mob.x, mob.y, mob.z);
		PathPosition target = new PathPosition(targetTilePos.x, targetTilePos.y, targetTilePos.z);

		computing.set(true);

		pathfinder.findPath(start, target)
			.ifPresent(result -> {
				pendingPath = result.getPath();
				computing.set(false);
				applyPath(result.getPath());
				TheyAreSmarter.LOGGER.info(
					"[AI] PATH FOUND mob={} pos=({},{},{}) target=({},{},{}) length={}",
					mob.getClass().getSimpleName(),
					(int) mob.x, (int) mob.y, (int) mob.z,
					targetTilePos.x, targetTilePos.y, targetTilePos.z,
					result.getPath().length()
				);
			})
			.orElse(result -> {
				computing.set(false);
				pathRetryTimer = settings.failureRetryTimer;
			})
			.exceptionally(ex -> {
				computing.set(false);
				TheyAreSmarter.LOGGER.error(
					"[AI] PATH EXCEPTION mob={} pos=({},{},{})",
					mob.getClass().getSimpleName(),
					(int) mob.x, (int) mob.y, (int) mob.z,
					ex
				);
				pathRetryTimer = settings.failureRetryTimer;
			});
	}

	protected void pathMotion() {
		if (!pathValid || nodes == null) return;

		MobAccessor accessor = (MobAccessor) mob;
		MobPathfinder self = (MobPathfinder) mob;
		Entity target = self.getTarget();

		if (pathIndex >= nodeCount()) {
			onReachTarget.emit(null);
			invalidatePath("path complete");
			accessor.setMoveForward(0.0F);
			accessor.setMoveStrafing(0.0F);
			return;
		}

		accessor.setMoveStrafing(0.0F);
		accessor.setMoveForward(0.0F);

		int lookahead = Math.min(pathIndex + 1, nodeCount() - 1);
		PathPosition next = nodes.get(lookahead);

		double tx = next.getX() + 0.5;
		double tz = next.getZ() + 0.5;
		double dx = tx - mob.x;
		double dy = next.getY() - mob.y;
		double dz = tz - mob.z;

		steerToward(accessor, dx, dz);
		accessor.setIsJumping((dy > 1.0D || mob.horizontalCollision) && mob.onGround);

		double dist = Math.sqrt(dx * dx + dz * dz);
		if (dist < settings.arrivalThreshold) {
			pathIndex++;
		}
	}

	private void steerToward(MobAccessor accessor, double dx, double dz) {
		float targetYaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
		float diff = wrapDegrees(targetYaw - mob.yRot);
		mob.yRot += diff * 0.95F;
		accessor.setMoveForward(accessor.getMoveSpeed());
		accessor.setMoveStrafing(0.0F);
	}

	private void applyPath(Path path) {
		this.currentPath = path;
		this.nodes = buildNodeList(path);
		this.pathValid = true;
		this.pathRetryTimer = 0;
		this.pathIndex       = 0;
		this.lastTargetTilePos = this.targetTilePos;
	}

	private void invalidatePath(String reason) {
		if (pathValid) {
			TheyAreSmarter.LOGGER.info("[AI] PATH INVALIDATED mob={} reason={} index={}/{}",
				mob.getClass().getSimpleName(), reason, pathIndex, nodeCount());
		}
		pathValid = false;
		pathRetryTimer = settings.recomputeInterval;
		currentPath = null;
		nodes = null;
		pathIndex       = 0;
	}

	private List<PathPosition> buildNodeList(Path path) {
		List<PathPosition> list = new ArrayList<>();
		for (PathPosition pos : path) list.add(pos);
		return list;
	}

	private int nodeCount() {
		return nodes == null ? 0 : nodes.size();
	}

	@Nullable
	public TilePos roamRandomPath(PoscArea.Area2D area) {
		int minX = Math.min(area.a().x(), area.b().x());
		int maxX = Math.max(area.a().x(), area.b().x());
		int minZ = Math.min(area.a().z(), area.b().z());
		int maxZ = Math.max(area.a().z(), area.b().z());
		int y = area.a().y();

		for (int i = 0; i < 10; i++) {
			int x = minX + ((MobAccessor) mob).getRandom().nextInt(maxX - minX + 1);
			int z = minZ + ((MobAccessor) mob).getRandom().nextInt(maxZ - minZ + 1);
			if (!mob.world.isAirBlock(x, y, z)) continue;
			if (mob.world.isAirBlock(x, y - 1, z)) continue;
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
			float weight = ((MobPathfinderAccessor) mob).callGetBlockPathWeight(candidate);
			if (weight < 0) continue;
			if (weight > bestWeight) {
				bestWeight = weight;
				if (best != null) best.set(x, y, z);
				else best = new TilePos(x, y, z);
			}
		}

		if (best != null) {
			setTarget(best);
			TheyAreSmarter.LOGGER.info("[AI] ROAM mob={} pos=({},{},{}) best=({},{},{})",
				mob.getClass().getSimpleName(),
				(int) mob.x, (int) mob.y, (int) mob.z,
				best.x, best.y, best.z);
		}
	}
}
