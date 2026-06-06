package deus.theyaresmarter.entities;

import deus.brainless.ai.fsm.interfaces.FSMState;
import deus.brainless.fsm.FiniteStateMachine;
import deus.theyaresmarter.ai.PathfinderController;
import deus.theyaresmarter.entities.GenericStates.CombatStates;
import deus.theyaresmarter.entities.GenericStates.TerminalStates;
import deus.theyaresmarter.entities.GenericStates.WalkerStates;
import deus.theyaresmarter.interfaces.IHasPathfinder;
import deus.theyaresmarter.interfaces.IHasProtectAreas;
import deus.theyaresmarter.mixin.EntityAccessor;
import deus.theyaresmarter.mixin.MobPathfinderAccessor;
import deus.theyaresmarter.util.PoscArea;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.EntityItem;
import net.minecraft.core.entity.Mob;
import net.minecraft.core.entity.animal.MobAnimal;
import net.minecraft.core.entity.animal.MobWolf;
import net.minecraft.core.entity.monster.MobSkeleton;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.util.helper.MathHelper;
import net.minecraft.core.world.pos.TilePos;
import net.minecraft.core.world.pos.TilePosc;
import org.joml.primitives.AABBd;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class FSMMobWolf {

	public static Supplier<FiniteStateMachine<FSMState, MobWolf>> WOLF =
		FiniteStateMachine.factory(fsm -> fsm
			.start(WalkerStates.IDLE)
			.terminal(TerminalStates.DEAD)

			// Actions

			.on(WalkerStates.IDLE, ctx -> {})

			.on(WalkerStates.ROAM, ctx -> {
				PathfinderController controller = getController(ctx);
				int r = 8 + ((EntityAccessor) ctx).getRandom().nextInt(18);
				TilePos roam = controller.roamRandomPath(new PoscArea.Area2D(
					new TilePos(ctx.x - r, ctx.y, ctx.z - r),
					new TilePos(ctx.x + r, ctx.y, ctx.z + r)));
				controller.setTarget(roam);
			})

			.on(WalkerStates.WALKING, ctx -> {})

			.on(WolfStates.SIT, ctx -> {
				PathfinderController controller = getController(ctx);
				controller.stop();
				ctx.setTarget(null);
			})

			.on(CombatStates.ATTACKING, ctx -> {
				Entity target = ctx.getTarget();
				if (target == null) return;
				float distance = distanceTo(ctx, target);
				((MobPathfinderAccessor) ctx).callAttackEntity(target, distance);
			})

			.on(WolfStates.HUNT_ANIMAL, ctx -> {
				Entity target = ctx.getTarget();
				if (target == null) return;
				float distance = distanceTo(ctx, target);
				((MobPathfinderAccessor) ctx).callAttackEntity(target, distance);
			})

			.on(WolfStates.PROTECT_BONE, FSMMobWolf::tickProtectBone)

			// On-enter

			.onEnter(WolfStates.FOLLOW_OWNER, (ctx, state) -> {
				Player owner = getOwner(ctx);
				getController(ctx).setTrackedEntity(owner);
			})

			.onEnter(WolfStates.FIND_OBJECT, (ctx, state) -> {
				EntityItem item = findNearbyItem(ctx);
				if (item == null) return;
				PathfinderController controller = getController(ctx);
				controller.setTrackedEntity(null);
				controller.setTarget(new TilePos(item.x, item.y, item.z));
			})

			.onEnter(WolfStates.PROTECT_BONE, (ctx, state) -> {
				List<TilePosc> areas = getBonesPositions(ctx);
				if (areas.isEmpty()) return;
				TilePosc selected = areas.get(((MobPathfinderAccessor) ctx).getRandom().nextInt(areas.size()));
				getController(ctx).setTarget(new TilePos(selected));
			})

			.onEnter(WolfStates.HUNT_ANIMAL, (ctx, state) -> {
				Mob prey = findNearbyPrey(ctx);
				if (prey == null) return;
				PathfinderController controller = getController(ctx);
				controller.setTrackedEntity(prey);
				ctx.setTarget(prey);
			})

			.onEnter(CombatStates.ATTACKING, (ctx, state) -> {
				Entity target = ctx.getTarget();
				if (target == null) return;
				getController(ctx).setTrackedEntity(target);
			})

			// Transitions

			.transition(WalkerStates.IDLE, ctx -> {
				if (ctx.isWolfSitting()) return WolfStates.SIT;

				if (ctx.isWolfTamed()) {
					FSMState owner = checkOwnerTransition(ctx);
					if (owner != null) return owner;
				}

				if (getController(ctx).hasPath()) return WalkerStates.WALKING;

				FSMState combat = checkCombatTransition(ctx);
				if (combat != null) return combat;


				if (((MobPathfinderAccessor) ctx).getRandom().nextInt(40) == 0) {
					if (findNearbyItem(ctx) != null) return WolfStates.FIND_OBJECT;
				}

				if (!getBonesPositions(ctx).isEmpty()) return WolfStates.PROTECT_BONE;

				if (ctx.getTarget() == null
					&& ((MobPathfinderAccessor) ctx).getDoRandomWalk()
					&& !((MobPathfinderAccessor) ctx).getHasAttacked()
					&& ((MobPathfinderAccessor) ctx).getRandom().nextInt(60) == 0) {
					return WalkerStates.ROAM;
				}

				if (!ctx.isWolfTamed() && findNearbyPrey(ctx) != null) {
					return WolfStates.HUNT_ANIMAL;
				}

				return WalkerStates.IDLE;
			})

			.transition(WalkerStates.WALKING, ctx -> {
				if (!getController(ctx).hasPath()) return WalkerStates.IDLE;
				if (ctx.isWolfSitting()) return WolfStates.SIT;
				return WalkerStates.WALKING;
			})

			.transition(WalkerStates.ROAM, ctx -> {
				if (!getController(ctx).isMoving()) return WalkerStates.IDLE;
				return WalkerStates.ROAM;
			})

			.transition(WolfStates.SIT, ctx -> {
				if (!ctx.isWolfSitting()) return WalkerStates.IDLE;
				return WolfStates.SIT;
			})

			.transition(WolfStates.FOLLOW_OWNER, ctx -> {
				Player owner = getOwner(ctx);
				if (owner == null) return WalkerStates.IDLE;
				if (owner.distanceTo(ctx) < 5.0F) {
					getController(ctx).setTrackedEntity(null);
					return WalkerStates.IDLE;
				}
				return WolfStates.FOLLOW_OWNER;
			})

			.transition(WolfStates.FIND_OBJECT, ctx -> {
				if (!getController(ctx).hasPath()) return WalkerStates.IDLE;
				return WolfStates.FIND_OBJECT;
			})

			.transition(WolfStates.PROTECT_BONE, ctx -> {
				List<TilePosc> areas = getBonesPositions(ctx);
				if (areas.isEmpty()) return WalkerStates.IDLE;
				if (ctx.getTarget() != null) return CombatStates.ATTACKING;
				boolean near = areas.stream().anyMatch(pos -> pos.distance(ctx) < 5);
				if (near) return WalkerStates.IDLE;
				return WolfStates.PROTECT_BONE;
			})

			.transition(CombatStates.ATTACKING, ctx -> {
				Entity target = ctx.getTarget();
				if (!ctx.isWolfAngry()) return WalkerStates.IDLE;
				if (target == null) {
					ctx.setWolfAngry(false);
					return WalkerStates.IDLE;
				}
				if (!target.isAlive()) return WalkerStates.IDLE;
				return CombatStates.ATTACKING;
			})

			.transition(WolfStates.HUNT_ANIMAL, ctx -> {
				Entity target = ctx.getTarget();
				if (target == null || target.isRemoved() || !target.isAlive()) {
					getController(ctx).setTrackedEntity(null);
					return WalkerStates.IDLE;
				}
				if (ctx.isWolfAngry()) return CombatStates.ATTACKING;
				return WolfStates.HUNT_ANIMAL;
			})
		);

	private static PathfinderController getController(MobWolf ctx) {
		return ((IHasPathfinder) ctx).theyaresmarter$getPathfinderController();
	}

	// FSM helpers

	private static List<TilePosc> getBonesPositions(MobWolf ctx) {
		return ((IHasProtectAreas) ctx).theyaresmarter$getBonesPositions();
	}

	private static Player getOwner(MobWolf ctx) {
		return ctx.world.getPlayerEntityByUUID(ctx.getWolfOwner());
	}

	private static float distanceTo(MobWolf ctx, Entity target) {
		double dx = target.x - ctx.x;
		double dy = target.y - ctx.y;
		double dz = target.z - ctx.z;
		return MathHelper.sqrt((float) (dx * dx + dy * dy + dz * dz));
	}

	/**
	 * Returns the next combat state if combat conditions are met, null otherwise.
	 */
	private static FSMState checkCombatTransition(MobWolf ctx) {
		if (!ctx.isWolfAngry() && ctx.getTarget() == null) return null;

		PathfinderController controller = getController(ctx);

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

		return null;
	}

	/**
	 * Returns FOLLOW_OWNER or null (and handles teleport) based on owner distance.
	 */
	private static FSMState checkOwnerTransition(MobWolf ctx) {
		Player owner = getOwner(ctx);
		if (owner == null) return null;

		float dist = owner.distanceTo(ctx);
		if (dist > 12.0F) {
			tpToOwner(ctx, owner);
			return WalkerStates.IDLE;
		}
		if (dist > 9.0F) return WolfStates.FOLLOW_OWNER;

		return null;
	}

	private static void tickProtectBone(MobWolf ctx) {
		PathfinderController controller = getController(ctx);
		controller.setTarget(null);

		AABBd checkBB = new AABBd(
			ctx.x, ctx.y, ctx.z,
			ctx.x + 1.0F, ctx.y + 1.0F, ctx.z + 1.0F);
		MathHelper.aabbGrow(checkBB, 16.0F, 4.0F, 16.0F, checkBB);

		Player owner = getOwner(ctx);

		List<Mob> threats = ctx.world.getEntitiesWithinAABB(Mob.class, checkBB).stream()
			.filter(m -> isThreat(ctx, m, owner))
			.toList();

		if (threats.isEmpty()) {
			ctx.setWolfAngry(false);
			return;
		}

		ctx.setWolfAngry(true);
		Mob threat = threats.get(((MobPathfinderAccessor) ctx).getRandom().nextInt(threats.size()));
		ctx.setTarget(threat);
		controller.setTrackedEntity(threat);
	}

	private static boolean isThreat(MobWolf ctx, Mob m, Player owner) {
		if (m == ctx || m == owner) return false;
		if (owner == null) return false;

		if (m instanceof MobWolf ally) {
			return !Objects.equals(ally.getWolfOwner(), owner.uuid);
		}
		if (m instanceof IHasProtectAreas ally) {
			return ally.theyaresmarter$getBonesPositions().isEmpty();
		}
		return true;
	}

	// ! TP DOESN'T WORK
	private static boolean tpToOwner(MobWolf ctx, Player owner) {
		IHasPathfinder accessor = (IHasPathfinder) ctx;
		TilePosc pos = accessor.theyaresmarter$getPathfinderController().getlastNodeTilePosc();
		if (pos != null) {
			ctx.moveTo(pos.x(), pos.y(), pos.z(), ctx.yRot, ctx.xRot);
			ctx.fallDistance = 0.0F;
			return true;
		}
		return false;
	}

	public static EntityItem findNearbyItem(MobWolf wolf) {
		ItemStack held = wolf.getHeldItem();
		if (held != null && held.itemID > 0) return null;

		AABBd checkBB = new AABBd(wolf.x, wolf.y, wolf.z, wolf.x + 1.0, wolf.y + 1.0, wolf.z + 1.0);
		MathHelper.aabbGrow(checkBB, 16.0, 4.0, 16.0, checkBB);

		List<EntityItem> valid = new ArrayList<>();
		for (EntityItem item : wolf.world.getEntitiesWithinAABB(EntityItem.class, checkBB)) {
			if (item == null || item.isRemoved()) continue;
			if (item.isInWater() || item.isInLava() || item.isInWall()) continue;
			if (!item.onGround) continue;
			if (item.item == null || item.item.stackSize <= 0) continue;
			if (item.pickupDelay > 0) continue;
			valid.add(item);
		}

		if (valid.isEmpty()) return null;
		return valid.get(wolf.world.rand.nextInt(valid.size()));
	}

	// World queries

	public static Mob findNearbyPrey(MobWolf wolf) {
		AABBd checkBB = new AABBd(wolf.x, wolf.y, wolf.z, wolf.x + 1.0, wolf.y + 1.0, wolf.z + 1.0);
		MathHelper.aabbGrow(checkBB, 12.0, 4.0, 12.0, checkBB);

		List<Mob> prey = wolf.world.getEntitiesWithinAABB(Mob.class, checkBB).stream()
			.filter(m -> !m.isRemoved() && m.isAlive() && m != wolf)
			.filter(m -> m instanceof MobAnimal || m instanceof MobSkeleton)
			.toList();

		if (prey.isEmpty()) return null;
		return prey.get(wolf.world.rand.nextInt(prey.size()));
	}

	public enum WolfStates implements FSMState {
		PROTECT_BONE,
		SIT,
		FIND_OBJECT,
		FOLLOW_OWNER,
		HUNT_ANIMAL
	}

}
