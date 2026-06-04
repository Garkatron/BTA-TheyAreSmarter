package deus.theyaresmarter.mixin;

import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.Mob;
import net.minecraft.core.entity.MobPathfinder;
import net.minecraft.core.world.pos.TilePosc;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = MobPathfinder.class, remap = false)
public interface MobPathfinderAccessor extends MobAccessor {

	@Accessor("doRandomWalk")
	boolean getDoRandomWalk();

	@Accessor("hasAttacked")
	boolean getHasAttacked();

	@Accessor("doRandomWalk")
	void setDoRandomWalk(boolean value);

	@Accessor("hasAttacked")
	void setHasAttacked(boolean hasAttacked);

	@Invoker("roamRandomPath")
	void callRoamRandomPath();

	@Invoker("attackBlockedEntity")
	void callAttackBlockedEntity(Entity entity, float f);

	@Invoker("attackEntity")
	void callAttackEntity(Entity entity, float distance);

	@Invoker("findPlayerToAttack")
	@Nullable Entity callFindPlayerToAttack();

	@Invoker("isMovementCeased")
	boolean callIsMovementCeased();

	@Invoker("getBlockPathWeight")
	float callGetBlockPathWeight(TilePosc blockPos);

}
