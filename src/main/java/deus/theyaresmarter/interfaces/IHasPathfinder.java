package deus.theyaresmarter.interfaces;

import deus.theyaresmarter.ai.PathfinderController;

public interface IHasPathfinder {
	PathfinderController theyaresmarter$getPathfinderController();
	void theyaresmarter$setPathfinderController(PathfinderController pathfinderController);
}
