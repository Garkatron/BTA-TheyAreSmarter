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
		double maxPenalty = 0.0;

		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				for (int dy = -1; dy <= 1; dy++) {
					scan.set(cx + dx, cy + dy, cz + dz);
					var type = world.getBlockType(scan);

					if (type.hasTag(BlockTags.IS_LAVA)) {
						double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
						double penalty = 20.0 / (dist + 0.5);
						if (penalty > maxPenalty) maxPenalty = penalty;
					}
				}
			}
		}

		if (maxPenalty > 0) return Cost.of(maxPenalty);

		scan.set(cx, cy - 1, cz);
		if (!world.getBlockType(scan).isCollidable()) return Cost.of(4.0);

		scan.set(cx, cy, cz);
		if (world.getBlockType(scan).hasTag(BlockTags.IS_WATER)) return Cost.of(5.0);

		return Cost.ZERO;
	}
}
