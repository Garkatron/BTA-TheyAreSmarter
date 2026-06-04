package deus.theyaresmarter.mixin;

import de.bsommerfeld.pathetic.api.pathing.NeighborStrategies;
import de.bsommerfeld.pathetic.api.pathing.heuristic.HeuristicWeights;
import deus.brainless.ai.fsm.interfaces.FSMState;
import deus.brainless.fsm.FiniteStateMachine;
import deus.theyaresmarter.ai.PathfinderController;
import deus.theyaresmarter.ai.PathfinderSettings;
import deus.theyaresmarter.entities.FSMMobWolf;
import deus.theyaresmarter.interfaces.IHasPathfinder;
import net.minecraft.core.entity.Mob;
import net.minecraft.core.entity.animal.MobAnimal;
import net.minecraft.core.entity.animal.MobWolf;
import net.minecraft.core.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobWolf.class)
public class MobWolfMixin extends MobAnimal {

	@Unique
	FiniteStateMachine<FSMState, MobWolf> fsm = FSMMobWolf.WOLF.get();


	public MobWolfMixin(World world) {
		super(world);
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void brainless$init(World world, CallbackInfo ci) {
		MobWolf self = (MobWolf) (Object) this;

		PathfinderSettings settings = PathfinderSettings.builder()
			.maxLength(64)
			.maxIterations(8_000)
			.arrivalThreshold(1.0D)
			.recomputeInterval(60)
			.neighborStrategy(NeighborStrategies.VERTICAL_AND_HORIZONTAL)
			.heuristicWeights(HeuristicWeights.create(1.0, 1.0, 0.5, 0.2))
			.async(true)
			.build();


		((IHasPathfinder)self).theyaresmarter$setPathfinderController(new PathfinderController((Mob) this, settings));
	}

	@Inject(
		method = "updateAI",
		at = @At("HEAD"),
		cancellable = true
	)
	private void brainless$updateAI(CallbackInfo ci) {
		MobWolf self = (MobWolf) (Object) this;
		PathfinderController controller = ((IHasPathfinder)self).theyaresmarter$getPathfinderController();

		fsm.update(self);
		controller.tick();

		ci.cancel();
	}

}
