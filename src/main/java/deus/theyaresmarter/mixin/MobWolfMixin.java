package deus.theyaresmarter.mixin;

import de.bsommerfeld.pathetic.api.pathing.NeighborStrategies;
import de.bsommerfeld.pathetic.api.pathing.heuristic.HeuristicWeights;
import deus.brainless.ai.fsm.interfaces.FSMState;
import deus.brainless.fsm.FiniteStateMachine;
import deus.theyaresmarter.ai.PathfinderController;
import deus.theyaresmarter.ai.PathfinderSettings;
import deus.theyaresmarter.entities.FSMMobWolf;
import deus.theyaresmarter.interfaces.IHasPathfinder;
import deus.theyaresmarter.interfaces.IHasProtectAreas;
import net.minecraft.core.block.Blocks;
import net.minecraft.core.entity.Mob;
import net.minecraft.core.entity.animal.MobAnimal;
import net.minecraft.core.entity.animal.MobWolf;
import net.minecraft.core.item.ItemFood;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.tag.ItemTags;
import net.minecraft.core.util.helper.MathHelper;
import net.minecraft.core.world.World;
import net.minecraft.core.world.pos.TilePos;
import net.minecraft.core.world.pos.TilePosc;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(MobWolf.class)
public abstract class MobWolfMixin extends MobAnimal implements IHasProtectAreas {

	@Shadow
	public abstract @Nullable ItemStack getHeldItem();

	@Unique
	FiniteStateMachine<FSMState, MobWolf> fsm = FSMMobWolf.WOLF.get();

	@Unique
	List<TilePosc> bonePositions = new ArrayList<>();

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

	@Inject(method = "tick", at = @At("TAIL"))
	private void brainless$updateTick(CallbackInfo ci) {
		MobWolf self = (MobWolf) (Object) this;
		ItemStack held = getHeldItem();
		if (held != null && held.getItem() instanceof ItemFood itemFood) {
			if (this.attackTime <= 0) {
				held.consumeItem(null);
				heal(itemFood.getHealAmount(held));
				if (held.stackSize <= 0) self.setHeldItem(null);
				this.attackTime = 20;
			}
		}

		int bx = MathHelper.floor(self.x);
		int by = MathHelper.floor(self.y);
		int bz = MathHelper.floor(self.z);


		TilePosc pos = new TilePos(bx, by, bz);

		if (self.world.getBlockType(pos).id() == Blocks.BONE_PILE.id()) {

			if (bonePositions.stream().noneMatch(p -> p.equals(pos))) {
				bonePositions.add(pos);
				System.out.println("AAAAAAAAAAaa");
			}
		}
	}

	@Override
	public List<TilePosc> theyaresmarter$getBonesPositions() {
		return new ArrayList<>(bonePositions);
	}

	@Override
	public void theyaresmarter$setBonesPositions(List<TilePosc> positions) {
		bonePositions = positions;
	}

	@Override
	public void theyaresmarter$addBonesPositions(TilePosc position) {
		bonePositions.add(position);
	}

	@Override
	public int theyaresmarter$maxBonesPositions() {
		return 3;
	}

	@Override
	public void theyaresmarter$removeBonesPositions(TilePosc position) {
		bonePositions.removeIf(p ->
			p.x() == position.x() &&
				p.y() == position.y() &&
				p.z() == position.z()
		);
	}
}
