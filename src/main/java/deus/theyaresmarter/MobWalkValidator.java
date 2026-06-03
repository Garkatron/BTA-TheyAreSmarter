package deus.theyaresmarter;

import de.bsommerfeld.pathetic.api.pathing.processing.ValidationProcessor;
import de.bsommerfeld.pathetic.api.pathing.processing.context.EvaluationContext;
import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import net.minecraft.core.block.BlockLogicFlower;
import net.minecraft.core.block.tag.BlockTags;
import net.minecraft.core.world.World;
import net.minecraft.core.world.pos.TilePos;

public class MobWalkValidator implements ValidationProcessor {

	private final World world;

	public MobWalkValidator(World world) {
		this.world = world;
	}

	@Override
	public boolean isValid(EvaluationContext context) {
		PathPosition pos = context.getCurrentPathPosition();
		TilePos top  = new TilePos().set(pos.getX(), pos.getY() + 1,     pos.getZ());
		TilePos current  = new TilePos().set(pos.getX(), pos.getY(),     pos.getZ());
		TilePos below    = new TilePos().set(pos.getX(), pos.getY() - 1, pos.getZ());
		TilePos below2   = new TilePos().set(pos.getX(), pos.getY() - 2, pos.getZ());

		var currentBlock = world.getBlockType(current);
		var belowBlock   = world.getBlockType(below);
		var topBlock   = world.getBlockType(top);

		boolean currentPassable = !currentBlock.isCollidable() && !topBlock.isCollidable() || currentBlock.getLogic() instanceof BlockLogicFlower;


		var below2Block = world.getBlockType(below2);

		boolean solidFloor = (belowBlock.isCollidable() && !(belowBlock.getLogic() instanceof BlockLogicFlower)) || (below2Block.isCollidable() && !(below2Block.getLogic() instanceof BlockLogicFlower));

		boolean lava = currentBlock.hasTag(BlockTags.IS_LAVA)
			|| belowBlock.hasTag(BlockTags.IS_LAVA)
			|| below2Block.hasTag(BlockTags.IS_LAVA) || topBlock.hasTag(BlockTags.IS_LAVA);

		boolean hasFloor = solidFloor && !lava;

		boolean water = currentBlock.hasTag(BlockTags.IS_WATER) || belowBlock.hasTag(BlockTags.IS_WATER) || topBlock.hasTag(BlockTags.IS_WATER);

		System.out.println("PASSABLE: " + currentPassable + " : HasFloor : " +  hasFloor + " : water : " + !water);
		return currentPassable && hasFloor;
	}
}
