package deus.theyaresmarter.entities;

import deus.brainless.ai.fsm.interfaces.FSMState;

public final class GenericStates {

	public enum TerminalStates implements FSMState {
		DONE,
		DEAD,
	}

	public enum WalkerStates implements FSMState {
		IDLE,
		WALKING,
		ROAM
	}

	public enum CombatStates implements FSMState {
		CHASING,
		ATTACKING,
		RETREATING,
		CIRCLING;
	}

	public enum SocialStates implements FSMState {
		FOLLOWING,
		TRADING,
		TALKING,
		GUARDING;
	}
}
