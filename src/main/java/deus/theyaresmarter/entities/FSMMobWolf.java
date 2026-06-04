package deus.theyaresmarter.entities;

import deus.brainless.ai.fsm.interfaces.FSMState;
import deus.brainless.fsm.FiniteStateMachine;
import deus.theyaresmarter.ai.PathfinderController;
import deus.theyaresmarter.entities.GenericStates.TerminalStates;
import deus.theyaresmarter.entities.GenericStates.WalkerStates;
import deus.theyaresmarter.entities.GenericStates.CombatStates;
import deus.theyaresmarter.entities.GenericStates.SocialStates;
import deus.theyaresmarter.interfaces.IHasPathfinder;
import deus.theyaresmarter.mixin.EntityAccessor;
import deus.theyaresmarter.mixin.MobAccessor;
import deus.theyaresmarter.mixin.MobPathfinderAccessor;
import deus.theyaresmarter.util.PoscArea;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.EntityItem;
import net.minecraft.core.entity.MobPathfinder;
import net.minecraft.core.entity.animal.MobWolf;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.util.helper.MathHelper;
import net.minecraft.core.world.pos.TilePos;
import org.joml.primitives.AABBd;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
public class FSMMobWolf {

	public static Supplier<FiniteStateMachine<FSMState, MobWolf>> WOLF =
		FiniteStateMachine.factory(fsm -> fsm
			.start(WalkerStates.IDLE)
			.terminal(TerminalStates.DEAD)

			// .on(WalkerStates.WALKING, ctx -> ctx.moveToFarm())
			.on(WalkerStates.IDLE, ctx -> {
				System.out.println("WOLF IDLE");
			})

			.on(WalkerStates.ROAM, ctx -> {
				System.out.println("ROAM");

				PathfinderController controller = ((IHasPathfinder) ctx).theyaresmarter$getPathfinderController();
				int r = 8 + ((EntityAccessor) ctx).getRandom().nextInt(18);
				TilePos roam = controller.roamRandomPath(new PoscArea.Area2D(
					new TilePos(ctx.x - r, ctx.y, ctx.z - r),
					new TilePos(ctx.x + r, ctx.y, ctx.z + r)));

				controller.setTarget(roam);
			})

			.on(WalkerStates.WALKING, ctx -> {
				System.out.println("WALKING");
			})

			.on(WolfStates.SIT, ctx -> {
				System.out.println("WOLF SIT");
				PathfinderController controller =
					((IHasPathfinder) ctx).theyaresmarter$getPathfinderController();

				controller.stop();
				ctx.setTarget(null);

			})


			.on(CombatStates.ATTACKING, ctx -> {
				Entity target = ctx.getTarget();
				if (target == null) return;

				double dx = target.x - ctx.x;
				double dy = target.y - ctx.y;
				double dz = target.z - ctx.z;
				float distance = MathHelper.sqrt((float)(dx*dx + dy*dy + dz*dz));

				((MobPathfinderAccessor) ctx).callAttackEntity(target, distance);
			})

			.onEnter(WolfStates.FOLLOW_OWNER, (ctx, state) -> {
				PathfinderController controller = ((IHasPathfinder) ctx).theyaresmarter$getPathfinderController();
				Player owner = ctx.world.getPlayerEntityByUUID(ctx.getWolfOwner());

				controller.setTrackedEntity(owner);
			})

			.onEnter(WolfStates.FIND_OBJECT, (ctx, state) -> {

				EntityItem item = findNearbyItem(ctx);

				if (item == null) {
					return;
				}

				PathfinderController controller = ((IHasPathfinder) ctx).theyaresmarter$getPathfinderController();

				controller.setTrackedEntity(null);
				controller.setTarget(
					new TilePos(item.x, item.y, item.z)
				);
			})

			.transition(WolfStates.FOLLOW_OWNER, ctx -> {
				PathfinderController controller = ((IHasPathfinder) ctx).theyaresmarter$getPathfinderController();
				Player owner = ctx.world.getPlayerEntityByUUID(ctx.getWolfOwner());
				if (owner != null) {
					float ownerDistance = owner.distanceTo(ctx);
					if (ownerDistance < 5.0F) {
						controller.setTrackedEntity(null);
						return WalkerStates.IDLE;
					}
					return WolfStates.FOLLOW_OWNER;
				} else {
					return WalkerStates.IDLE;
				}
			})

			.transition(WolfStates.FIND_OBJECT, ctx -> {
				System.out.println("WOLF FIND_OBJECT");

				PathfinderController controller =
					((IHasPathfinder) ctx).theyaresmarter$getPathfinderController();

				if (!controller.hasPath()) {
					return WalkerStates.IDLE;
				}

				return WolfStates.FIND_OBJECT;
			})

			.transition(CombatStates.ATTACKING, ctx -> {
				if (!ctx.isWolfAngry()) return WalkerStates.IDLE;
				return CombatStates.ATTACKING;
			})

			.transition(WolfStates.SIT, ctx -> {
				if (!ctx.isWolfSitting()) return WalkerStates.IDLE;
				return WolfStates.SIT;
			})

			.transition(WalkerStates.IDLE, ctx -> {
				PathfinderController controller = ((IHasPathfinder) ctx).theyaresmarter$getPathfinderController();
				MobPathfinderAccessor accessor = (MobPathfinderAccessor) ctx;

				if (ctx.isWolfSitting()) return WolfStates.SIT;
				if (controller.hasPath()) return WalkerStates.WALKING;

				EntityItem item = findNearbyItem(ctx);

				if (item != null) {
					return WolfStates.FIND_OBJECT;
				}



				if (ctx.isWolfAngry() || ctx.getTarget() != null) {
					if (ctx.hasCurrentTarget()) return CombatStates.ATTACKING;

					Entity attackTarget = ctx.getTarget();
					if (attackTarget != null) {
						controller.setTrackedEntity(attackTarget);
						return CombatStates.ATTACKING;
					}

					Player nearPlayer = ctx.world.getClosestPlayerToEntity(ctx, 16.0D);
					if (nearPlayer != null) {
						controller.setTrackedEntity(nearPlayer);
						return CombatStates.ATTACKING;
					}
				}

				Player owner = ctx.world.getPlayerEntityByUUID(ctx.getWolfOwner());
				if (owner != null) {
					float ownerDistance = owner.distanceTo(ctx);
					if (ownerDistance > 12.0F) return WolfStates.FOLLOW_OWNER;
				}

				if (ctx.getTarget() == null && accessor.getDoRandomWalk()
					&& !accessor.getHasAttacked()
					&& accessor.getRandom().nextInt(40) == 0) return WalkerStates.ROAM;


				return WalkerStates.IDLE;
			})

			.transition(WalkerStates.ROAM, ctx -> {
				PathfinderController controller = ((IHasPathfinder) ctx).theyaresmarter$getPathfinderController();
				if (!controller.isMoving()) return WalkerStates.IDLE;

				return WalkerStates.ROAM;
			})

			.transition(WalkerStates.WALKING, ctx -> {
				PathfinderController controller = ((IHasPathfinder) ctx).theyaresmarter$getPathfinderController();

				if (!controller.hasPath()) return WalkerStates.IDLE;
				if (ctx.isWolfSitting()) return WolfStates.SIT;

				return WalkerStates.WALKING;
			})
		);

	public enum WolfStates implements FSMState {

		SIT,
		FIND_OBJECT,
		FOLLOW_OWNER
	}

	public static EntityItem findNearbyItem(MobWolf wolf) {
		ItemStack heldItem = wolf.getHeldItem();

		if (heldItem != null && heldItem.itemID > 0) {
			return null;
		}

		AABBd checkBB = new AABBd(
			wolf.x, wolf.y, wolf.z,
			wolf.x + 1.0D, wolf.y + 1.0D, wolf.z + 1.0D
		);

		MathHelper.aabbGrow(
			checkBB,
			16.0D,
			4.0D,
			16.0D,
			checkBB
		);

		List<EntityItem> nearbyItems =
			wolf.world.getEntitiesWithinAABB(EntityItem.class, checkBB);

		if (nearbyItems.isEmpty()) {
			return null;
		}

		List<EntityItem> valid = new ArrayList<>();

		for (EntityItem item : nearbyItems) {
			if (item == null) continue;

			if (item.isInWater()) continue;
			if (item.isInLava()) continue;
			if (item.isInWall()) continue;
			if (!item.onGround) continue;
			if (item.isRemoved()) continue;

			if (item.item == null) continue;
			if (item.item.stackSize <= 0) continue;
			if (item.pickupDelay > 0) continue;
			valid.add(item);

		}


		if (valid.isEmpty()) {
			return null;
		}

		return valid.get(wolf.world.rand.nextInt(valid.size()));
	}
}
