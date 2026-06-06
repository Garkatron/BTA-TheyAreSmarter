package deus.theyaresmarter.entities;

import deus.brainless.ai.fsm.interfaces.FSMState;
import deus.brainless.fsm.FiniteStateMachine;
import deus.theyaresmarter.ai.PathfinderController;
import deus.theyaresmarter.entities.GenericStates.TerminalStates;
import deus.theyaresmarter.entities.GenericStates.WalkerStates;
import deus.theyaresmarter.entities.GenericStates.CombatStates;
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

			.onEnter(WolfStates.PROTECT_BONE, (ctx, state) -> {
				MobPathfinderAccessor accessor = (MobPathfinderAccessor) ctx;
				List<TilePosc> areas = ((IHasProtectAreas)ctx).theyaresmarter$getBonesPositions();
				TilePosc selected = areas.get(accessor.getRandom().nextInt(areas.size()));
				PathfinderController controller = ((IHasPathfinder) ctx).theyaresmarter$getPathfinderController();
				controller.setTarget(new TilePos(selected));
			})
			.onEnter(WolfStates.HUNT_ANIMAL, (ctx, state) -> {
				Mob prey = findNearbyPrey(ctx);
				if (prey == null) return;
				PathfinderController controller = ((IHasPathfinder) ctx).theyaresmarter$getPathfinderController();
				controller.setTrackedEntity(prey);
				ctx.setTarget(prey);
			})


			.on(WolfStates.HUNT_ANIMAL, ctx -> {
				Entity target = ctx.getTarget();
				if (target == null) return;
				double dx = target.x - ctx.x;
				double dy = target.y - ctx.y;
				double dz = target.z - ctx.z;
				float distance = MathHelper.sqrt((float)(dx*dx + dy*dy + dz*dz));
				((MobPathfinderAccessor) ctx).callAttackEntity(target, distance);
			})

			.on(WolfStates.PROTECT_BONE, ctx -> {
				System.out.println("WOLF PROTECT BONE");
				PathfinderController controller = ((IHasPathfinder) ctx).theyaresmarter$getPathfinderController();
				controller.setTarget(null);
				AABBd checkBB = new AABBd(ctx.x, ctx.y, ctx.z, ctx.x + (double)1.0F, ctx.y + (double)1.0F, ctx.z + (double)1.0F);
				MathHelper.aabbGrow(checkBB, 16.0F, 4.0F, 16.0F, checkBB);
				Player owner = ctx.world.getPlayerEntityByUUID(ctx.getWolfOwner());

				List<Mob> mobs = ctx.world.getEntitiesWithinAABB(Mob.class, checkBB).stream()
					.filter(m -> {
						if (m == ctx || m == owner) return false;
						if (owner == null) return false;

						// same owner wolf = ally, exclude
						if (m instanceof MobWolf ally) {
							return !Objects.equals(ally.getWolfOwner(), owner.uuid);
						}

						// wolf with bones = ally, exclude
						if (m instanceof IHasProtectAreas ally) {
							return ally.theyaresmarter$getBonesPositions().isEmpty();
						}

						return true;
					}).toList();

				if (mobs.isEmpty()) {
					return;
				}

				Mob threat = mobs.get(((MobPathfinderAccessor) ctx).getRandom().nextInt(mobs.size()));
				ctx.setTarget(threat);
				controller.setTrackedEntity(threat);
			})

			.transition(WolfStates.PROTECT_BONE, ctx -> {

				List<TilePosc> areas = ((IHasProtectAreas) ctx).theyaresmarter$getBonesPositions();

				if (areas.isEmpty()) return WalkerStates.IDLE;
				if (ctx.getTarget() != null) return CombatStates.ATTACKING;



				boolean nearAnyArea = areas.stream().anyMatch(pos -> pos.distance(ctx) < 5);
				if (nearAnyArea) return WalkerStates.IDLE;

				return WolfStates.PROTECT_BONE;
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

			.transition(WolfStates.HUNT_ANIMAL, ctx -> {
				Entity target = ctx.getTarget();
				if (target == null || target.isRemoved() || !target.isAlive()) {
					((IHasPathfinder) ctx).theyaresmarter$getPathfinderController().setTrackedEntity(null);
					return WalkerStates.IDLE;
				}
				if (ctx.isWolfAngry()) return CombatStates.ATTACKING;
				return WolfStates.HUNT_ANIMAL;
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

				if (ctx.isWolfTamed()) {
					Player owner = ctx.world.getPlayerEntityByUUID(ctx.getWolfOwner());
					if (owner != null) {
						float ownerDistance = owner.distanceTo(ctx);
						if (ownerDistance > 12.0F) {
							tpToOwner(ctx, owner);
							return WalkerStates.IDLE;
						} else if (ownerDistance > 9.0F) {
							return WolfStates.FOLLOW_OWNER;
						}
					}
				}


				List<TilePosc> areas = ((IHasProtectAreas)ctx).theyaresmarter$getBonesPositions();
				if (!areas.isEmpty()) {
					return WolfStates.PROTECT_BONE;
				}

				if (ctx.getTarget() == null && accessor.getDoRandomWalk()
					&& !accessor.getHasAttacked()
					&& accessor.getRandom().nextInt(60) == 0) return WalkerStates.ROAM;

				if (!ctx.isWolfTamed()) {
					Mob prey = findNearbyPrey(ctx);
					if (prey != null) return WolfStates.HUNT_ANIMAL;
				}

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

	private static boolean tpToOwner(MobWolf ctx, Player owner) {
		int targetX = MathHelper.floor(owner.x);
		int targetY = MathHelper.floor(owner.bb.minY);
		int targetZ = MathHelper.floor(owner.z);

		for(int _x = -2; _x <= 2; ++_x) {
			for(int _z = -2; _z <= 2; ++_z) {
				if ((Math.abs(_x) > 1 || Math.abs(_z) > 1) && ctx.world.isBlockNormalCube(targetX + _x, targetY - 1, targetZ + _z) && !ctx.world.isBlockNormalCube(targetX + _x, targetY, targetZ + _z) && !ctx.world.isBlockNormalCube(targetX + _x, targetY + 1, targetZ + _z)) {
					ctx.moveTo((double)((float)(targetX + _x) + 0.5F), (double)targetY, (double)((float)(targetZ + _z) + 0.5F), ctx.yRot, ctx.xRot);
					ctx.fallDistance = 0.0F;
					return true;
				}
			}
		}
		return false;
	}

	public enum WolfStates implements FSMState {
		PROTECT_BONE,
		SIT,
		FIND_OBJECT,
		FOLLOW_OWNER,
		HUNT_ANIMAL
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

	public static Mob findNearbyPrey(MobWolf wolf) {
		AABBd checkBB = new AABBd(wolf.x, wolf.y, wolf.z,
			wolf.x + 1.0D, wolf.y + 1.0D, wolf.z + 1.0D);
		MathHelper.aabbGrow(checkBB, 12.0D, 4.0D, 12.0D, checkBB);

		List<Mob> prey = wolf.world.getEntitiesWithinAABB(Mob.class, checkBB)
			.stream()
			.filter(m -> !m.isRemoved() && m.isAlive() && m != wolf)
			.filter(m -> m instanceof MobAnimal || m instanceof MobSkeleton)
			.toList();

		if (prey.isEmpty()) return null;
		return prey.get(wolf.world.rand.nextInt(prey.size()));
	}
}
