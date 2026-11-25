package com.sto.backend.game;

/** Special event of a round; at most one per round, {@code null} means a plain round. */
public enum RoundEvent {
	/** A chooser (drawn without repetition) picks a target who must bet 100/0. */
	VA_BANQUE,
	/** Gains of the round are doubled. */
	DOUBLE,
	/** The leader bets first, everyone sees the bet before betting. */
	OPEN_BET,
	/** A drawn player bets, then may take over another player's bet without seeing it. */
	BLIND_COPY,
	/** Everyone must bet 100/0. */
	ALL_IN,
	/** Shorter betting time ({@link GameEngine#BLITZ_BET_SECONDS}). */
	BLITZ
}
