package deus.theyaresmarter.mixin;

import de.bsommerfeld.pathetic.api.pathing.NeighborStrategies;
import de.bsommerfeld.pathetic.api.pathing.heuristic.HeuristicWeights;
import deus.theyaresmarter.ai.PathfinderController;
import deus.theyaresmarter.ai.PathfinderSettings;
import deus.theyaresmarter.interfaces.IHasPathfinder;
import deus.theyaresmarter.util.PoscArea;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.Mob;
import net.minecraft.core.entity.MobPathfinder;
import net.minecraft.core.world.World;
import net.minecraft.core.world.pos.TilePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobPathfinder.class)
public abstract class MobPathfinderMixin extends Mob implements IHasPathfinder {
	@Shadow
	public abstract void setTarget(@Nullable Entity target);

	@Unique
	private PathfinderController brainless$pathfinder;


	public MobPathfinderMixin(@NotNull World world) {
		super(world);
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void brainless$init(World world, CallbackInfo ci) {

		PathfinderSettings settings = PathfinderSettings.builder()
			.maxLength(64)
			.maxIterations(8_000)
			.arrivalThreshold(1.2D)
			.recomputeInterval(60)
			.neighborStrategy(NeighborStrategies.VERTICAL_AND_HORIZONTAL)
			.heuristicWeights(HeuristicWeights.create(1.0, 1.0, 0.5, 0.2))
			.async(true)
			.build();


		brainless$pathfinder = new PathfinderController((Mob) this, settings);
	}

	@Inject(
		method = "updateAI",
		at = @At("HEAD"),
		cancellable = true
	)
	private void brainless$updateAI(CallbackInfo ci) {
		MobPathfinder self = (MobPathfinder) (Object) this;
		MobPathfinderAccessor accessor = (MobPathfinderAccessor) this;
		accessor.setHasAttacked(accessor.callIsMovementCeased());
		Entity target = self.getTarget();
		if (target == null) {
			target = accessor.callFindPlayerToAttack();
			self.setTarget(target);
		} else if (!target.isAlive()) {
			self.setTarget(null);
			target = null;
		}

		if (target != null) {
			float distance = target.distanceTo(self);
			if (distance <= 8) {
				if (self.canEntityBeSeen(target)) {
					accessor.callAttackEntity(target, distance);
				} else {
					accessor.callAttackBlockedEntity(target, distance);
				}
			}
			brainless$pathfinder.setTarget(
				new TilePos((int) target.x, (int) target.y, (int) target.z)
			);
			brainless$pathfinder.tick();
			self.lookAt(target, 30.0F, 30.0F);
		} else {
			if (accessor.getDoRandomWalk()
				&& !accessor.getHasAttacked()
				&& accessor.getRandom().nextInt(80) == 0) {

				int r = 8 + random.nextInt(8);
				TilePos roam = brainless$pathfinder.roamRandomPath(new PoscArea.Area2D(
					new TilePos(self.x - r, self.y, self.z - r),
					new TilePos(self.x + r, self.y, self.z + r)));
				if (roam != null) {
					brainless$pathfinder.setTarget(roam);
				} else {
					brainless$pathfinder.setTarget(null);
				}

			}
			brainless$pathfinder.tick();
		}



		if (self.horizontalCollision && !brainless$pathfinder.hasPath()) {
			accessor.setIsJumping(true);
		}
		if (accessor.getRandom().nextFloat() < 0.8F && (self.isInWater() || self.isInLava())) {
			accessor.setIsJumping(true);
		}
		ci.cancel();
	}

	@Override
	public PathfinderController theyaresmarter$getPathfinderController() {
		return brainless$pathfinder;
	}

	@Override
	public void theyaresmarter$setPathfinderController(PathfinderController pathfinderController) {
		this.brainless$pathfinder = pathfinderController;
	}
}
