package deus.theyaresmarter.ai;

import de.bsommerfeld.pathetic.api.pathing.INeighborStrategy;
import de.bsommerfeld.pathetic.api.pathing.NeighborStrategies;
import de.bsommerfeld.pathetic.api.pathing.heuristic.HeuristicStrategies;
import de.bsommerfeld.pathetic.api.pathing.heuristic.HeuristicWeights;
import de.bsommerfeld.pathetic.api.pathing.heuristic.IHeuristicStrategy;
import de.bsommerfeld.pathetic.api.provider.NavigationPointProvider;

/**
 * Immutable configuration for {@link PathfinderController}.
 * Build via {@link #builder()}.
 */
public final class PathfinderSettings {

	// ── Navigation ────────────────────────────────────────────────────────────
	/**
	 * Max A* iterations before giving up.
	 */
	public final int maxIterations;
	/**
	 * Max path length in blocks.
	 */
	public final int maxLength;
	/**
	 * Neighbor expansion strategy (horizontal, vertical+horizontal, etc.).
	 */
	public final INeighborStrategy neighborStrategy;

	// ── Movement ──────────────────────────────────────────────────────────────
	/**
	 * Distance to next node that counts as "reached".
	 */
	public final double arrivalThreshold;
	/**
	 * Ticks between full path recomputes when target hasn't moved.
	 */
	public final int recomputeInterval;
	/**
	 * Threshold (blocks²) — if new target is closer than this to the current
	 * one, the target update is ignored.
	 */
	public final double retargetThreshold;

	// ── Heuristic ─────────────────────────────────────────────────────────────
	public final HeuristicWeights heuristicWeights;
	public final IHeuristicStrategy heuristicStrategy;

	// ── Engine flags ──────────────────────────────────────────────────────────
	/**
	 * Run pathfinding asynchronously.
	 */
	public final boolean async;
	/**
	 * Allow fallback pathfinding when primary fails.
	 */
	public final boolean fallback;

	// ── Provider ──────────────────────────────────────────────────────────────
	/**
	 * Navigation point provider (passability overrides).
	 */
	public final NavigationPointProvider navigationPointProvider;

	// ── Retry ─────────────────────────────────────────────────────────────────
	/**
	 * Timer value set after a failed path attempt. Controls how soon the
	 * controller retries. Defaults to {@code recomputeInterval / 2}.
	 * Set to -1 to use the default formula automatically.
	 */
	public final int failureRetryTimer;

	// ─────────────────────────────────────────────────────────────────────────

	private PathfinderSettings(Builder b) {
		this.maxIterations = b.maxIterations;
		this.maxLength = b.maxLength;
		this.neighborStrategy = b.neighborStrategy;
		this.arrivalThreshold = b.arrivalThreshold;
		this.recomputeInterval = b.recomputeInterval;
		this.retargetThreshold = b.retargetThreshold;
		this.heuristicWeights = b.heuristicWeights;
		this.heuristicStrategy = b.heuristicStrategy;
		this.async = b.async;
		this.fallback = b.fallback;
		this.navigationPointProvider = b.navigationPointProvider;
		this.failureRetryTimer = b.failureRetryTimer < 0
			? b.recomputeInterval / 2
			: b.failureRetryTimer;
	}

	/**
	 * Returns a builder pre-filled with the default values used in the original code.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Returns a copy of this settings object with one field changed.
	 */
	public Builder toBuilder() {
		return new Builder(this);
	}

	// ─────────────────────────────────────────────────────────────────────────

	public static final class Builder {

		private int maxIterations = 10_000;
		private int maxLength = 128;
		private INeighborStrategy neighborStrategy = NeighborStrategies.VERTICAL_AND_HORIZONTAL;
		private double arrivalThreshold = 1.2D;
		private int recomputeInterval = 60;
		private double retargetThreshold = 2.5D;
		private HeuristicWeights heuristicWeights =
			HeuristicWeights.create(0.5, 1, 0.7, 0.3);
		private IHeuristicStrategy heuristicStrategy = HeuristicStrategies.SQUARED;
		private boolean async = true;
		private boolean fallback = true;
		private NavigationPointProvider navigationPointProvider =
			(pos, ctx) -> () -> true;
		private int failureRetryTimer = -1; // -1 = auto (recomputeInterval / 2)

		private Builder() {
		}

		private Builder(PathfinderSettings s) {
			this.maxIterations = s.maxIterations;
			this.maxLength = s.maxLength;
			this.neighborStrategy = s.neighborStrategy;
			this.arrivalThreshold = s.arrivalThreshold;
			this.recomputeInterval = s.recomputeInterval;
			this.retargetThreshold = s.retargetThreshold;
			this.heuristicWeights = s.heuristicWeights;
			this.heuristicStrategy = s.heuristicStrategy;
			this.async = s.async;
			this.fallback = s.fallback;
			this.navigationPointProvider = s.navigationPointProvider;
			this.failureRetryTimer = s.failureRetryTimer;
		}

		public Builder maxIterations(int v) {
			maxIterations = v;
			return this;
		}

		public Builder maxLength(int v) {
			maxLength = v;
			return this;
		}

		public Builder neighborStrategy(INeighborStrategy v) {
			neighborStrategy = v;
			return this;
		}

		public Builder arrivalThreshold(double v) {
			arrivalThreshold = v;
			return this;
		}

		public Builder recomputeInterval(int v) {
			recomputeInterval = v;
			return this;
		}

		public Builder retargetThreshold(double v) {
			retargetThreshold = v;
			return this;
		}

		public Builder heuristicWeights(HeuristicWeights v) {
			heuristicWeights = v;
			return this;
		}

		public Builder heuristicStrategy(IHeuristicStrategy v) {
			heuristicStrategy = v;
			return this;
		}

		public Builder async(boolean v) {
			async = v;
			return this;
		}

		public Builder fallback(boolean v) {
			fallback = v;
			return this;
		}

		public Builder navigationPointProvider(NavigationPointProvider v) {
			navigationPointProvider = v;
			return this;
		}

		/**
		 * Override the retry timer after failure. Use -1 for auto (recomputeInterval/2).
		 */
		public Builder failureRetryTimer(int v) {
			failureRetryTimer = v;
			return this;
		}

		public PathfinderSettings build() {
			if (maxLength < 1) throw new IllegalArgumentException("maxLength must be >= 1");
			if (maxIterations < 1) throw new IllegalArgumentException("maxIterations must be >= 1");
			if (arrivalThreshold <= 0) throw new IllegalArgumentException("arrivalThreshold must be > 0");
			if (recomputeInterval < 1) throw new IllegalArgumentException("recomputeInterval must be >= 1");
			return new PathfinderSettings(this);
		}
	}
}
