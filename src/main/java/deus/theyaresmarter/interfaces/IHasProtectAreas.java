package deus.theyaresmarter.interfaces;

import net.minecraft.core.world.pos.TilePosc;

import java.util.List;

public interface IHasProtectAreas {
	List<TilePosc> theyaresmarter$getBonesPositions();
	void theyaresmarter$setBonesPositions(List<TilePosc> positions);
	void theyaresmarter$addBonesPositions(TilePosc position);
	void theyaresmarter$removeBonesPositions(TilePosc position);
	int theyaresmarter$maxBonesPositions();
}
