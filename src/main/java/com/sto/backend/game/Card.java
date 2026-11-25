package com.sto.backend.game;

/**
 * A card a player holds from the start of the game and plays (at most one per round) in the
 * {@link RoundPhase#CARDS} phase. Cards are based on the {@link RoundEvent}s but are not the same.
 */
public enum Card {
	/** Another player must bet 100/0. */
	VA_BANQUE(true),
	/** Own points of the round x2. */
	DOUBLE(false),
	/** Another player bets first (bindingly) and only the card's player sees that bet. */
	PEEK(true),
	/** After the betting, the player sees everyone's bets and may take over one of them. */
	COPY(false),
	/** Another player has only {@link GameEngine#BLITZ_BET_SECONDS} to bet. */
	BLITZ(true),
	/** The card's player also gets half of another player's gain of the round; the target loses nothing. */
	TAX(true);

	private final boolean targetsOther;

	Card(boolean targetsOther) {
		this.targetsOther = targetsOther;
	}

	/** Aimed at another player (needs a target); otherwise at the player who plays it. */
	public boolean targetsOther() {
		return targetsOther;
	}
}
