package deus.theyaresmarter.mixin;

import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Random;

@Mixin(value = Mob.class, remap = false)
public interface MobAccessor extends EntityAccessor {

	@Accessor("isJumping")
	void setIsJumping(boolean jumping);

	@Accessor("isJumping")
	boolean getIsJumping();

	@Accessor("moveForward")
	void setMoveForward(float forward);

	@Accessor("moveForward")
	float getMoveForward();

	@Accessor("moveSpeed")
	float getMoveSpeed();

	@Accessor("moveStrafing")
	void setMoveStrafing(float value);

	@Invoker("getLookingTilt")
	int callGetLookingTilt();
}
