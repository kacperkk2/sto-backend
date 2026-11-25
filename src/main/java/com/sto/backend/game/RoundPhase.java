package com.sto.backend.game;

public enum RoundPhase {
	/**
	 * First phase of a regular round when someone still holds cards: everyone with cards plays one or
	 * passes, without seeing the others' choices. The played cards are public once the phase is over.
	 */
	CARDS,
	/** Only with {@link RoundEvent#VA_BANQUE}: the chooser picks the target. */
	VA_BANQUE_PICK,
	/** Open bettors (the leader in an Open bet round, targets of Open bet cards) bet first, at the same time. */
	OPEN_BET,
	/** Everyone (still without a bet) bets. */
	BETTING,
	/**
	 * After the betting, when someone copies (Blind copy event or card): each copy actor keeps their own bet
	 * or takes over another player's bet without seeing it. Everyone else waits.
	 */
	COPY_PICK,
	/** Correct answer, bets and gains are public; the host starts the next round. */
	REVEALED
}
