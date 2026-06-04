package deus.theyaresmarter;

import de.bsommerfeld.pathetic.api.pathing.processing.Cost;
import de.bsommerfeld.pathetic.api.pathing.processing.CostProcessor;
import de.bsommerfeld.pathetic.api.pathing.processing.context.EvaluationContext;
import net.minecraft.core.block.tag.BlockTags;
import net.minecraft.core.world.World;
import net.minecraft.core.world.pos.TilePos;

public class BlockCostProcessor implements CostProcessor {

	private final World world;

	public BlockCostProcessor(World world) {
		this.world = world;
	}

	@Override
	public Cost calculateCostContribution(EvaluationContext context) {
		var pos = context.getCurrentPathPosition();
		int cx = (int) pos.getX();
		int cy = (int) pos.getY();
		int cz = (int) pos.getZ();

		TilePos scan = new TilePos();

		scan.set(cx, cy - 1, cz);

		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {

				if (dx == 0 && dz == 0) continue;

				scan.set(cx + dx, cy - 1, cz + dz);

				if (world.getBlockType(scan).hasTag(BlockTags.IS_LAVA)) {
					return Cost.of(1000);
				}
			}
		}

		boolean hasSupportAround = false;

		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {

				scan.set(cx + dx, cy - 1, cz + dz);

				if (world.getBlockType(scan).isCollidable()) {
					hasSupportAround = true;
					break;
				}
			}
			if (hasSupportAround) break;
		}

		if (!hasSupportAround) {
			return Cost.of(30.0);
		}

		scan.set(cx, cy - 1, cz);
		if (!world.getBlockType(scan).isCollidable()) {
			return Cost.of(4.0);
		}

		scan.set(cx, cy, cz);
		if (world.getBlockType(scan).hasTag(BlockTags.IS_WATER)) {
			return Cost.of(5.0);
		}

		scan.set(cx, cy - 1, cz);

		if (!world.getBlockType(scan).isCollidable()) {
			return Cost.of(6.0);
		}

		return Cost.ZERO;
	}
}
