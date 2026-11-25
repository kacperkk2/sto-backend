package com.sto.backend.game;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

import com.sto.backend.error.ValidationException;

/**
 * Host's settings of a game.
 *
 * @param rounds          number of regular rounds (tie-break rounds come on top)
 * @param eventChance     chance (0-100 %) that a round gets a special event
 * @param betSeconds      time for betting (and for the open bet)
 * @param lastRoundDouble last regular round is always {@link RoundEvent#DOUBLE}
 * @param events          events that can be drawn (in enum order); empty means plain rounds only
 * @param cards           cards every player gets once at the start (in enum order); empty means no cards
 * @param cardSeconds     time of the cards phase
 * @param scenario        id of the question scenario ({@link QuestionBank})
 */
public record GameSettings(int rounds, int eventChance, int betSeconds, boolean lastRoundDouble,
		List<RoundEvent> events, List<Card> cards, int cardSeconds, String scenario) {

	public static final int DEFAULT_ROUNDS = 10;
	public static final int DEFAULT_EVENT_CHANCE = 50;
	public static final int DEFAULT_BET_SECONDS = 30;
	public static final int DEFAULT_CARD_SECONDS = 15;

	static final int MIN_ROUNDS = 1;
	static final int MAX_ROUNDS = 30;
	static final int MIN_BET_SECONDS = 10;
	static final int MAX_BET_SECONDS = 120;
	static final int MIN_CARD_SECONDS = 5;
	static final int MAX_CARD_SECONDS = 60;

	public static GameSettings defaults(String scenario) {
		return of(null, null, null, null, null, null, null, scenario);
	}

	/**
	 * Builds settings from a request; missing values get defaults, out-of-range ones are rejected. The scenario
	 * must already be resolved ({@link QuestionBank#resolveScenarioId}).
	 */
	public static GameSettings of(Integer rounds, Integer eventChance, Integer betSeconds, Boolean lastRoundDouble,
			List<RoundEvent> events, List<Card> cards, Integer cardSeconds, String scenario) {
		Objects.requireNonNull(scenario);
		int r = rounds == null ? DEFAULT_ROUNDS : rounds;
		int chance = eventChance == null ? DEFAULT_EVENT_CHANCE : eventChance;
		int seconds = betSeconds == null ? DEFAULT_BET_SECONDS : betSeconds;
		int cardTime = cardSeconds == null ? DEFAULT_CARD_SECONDS : cardSeconds;
		if (r < MIN_ROUNDS || r > MAX_ROUNDS) {
			throw new ValidationException("rounds: must be " + MIN_ROUNDS + "-" + MAX_ROUNDS);
		}
		if (chance < 0 || chance > 100) {
			throw new ValidationException("eventChance: must be 0-100");
		}
		if (seconds < MIN_BET_SECONDS || seconds > MAX_BET_SECONDS) {
			throw new ValidationException("betSeconds: must be " + MIN_BET_SECONDS + "-" + MAX_BET_SECONDS);
		}
		if (cardTime < MIN_CARD_SECONDS || cardTime > MAX_CARD_SECONDS) {
			throw new ValidationException("cardSeconds: must be " + MIN_CARD_SECONDS + "-" + MAX_CARD_SECONDS);
		}
		if (events != null && events.contains(null)) {
			throw new ValidationException("events: unknown event");
		}
		if (cards != null && cards.contains(null)) {
			throw new ValidationException("cards: unknown card");
		}
		// missing list = all events; duplicates are dropped, order follows the enum
		EnumSet<RoundEvent> enabled = events == null ? EnumSet.allOf(RoundEvent.class) : EnumSet.noneOf(RoundEvent.class);
		if (events != null) {
			enabled.addAll(events);
		}
		// missing list = no cards
		EnumSet<Card> enabledCards = EnumSet.noneOf(Card.class);
		if (cards != null) {
			enabledCards.addAll(cards);
		}
		return new GameSettings(r, chance, seconds, Boolean.TRUE.equals(lastRoundDouble), List.copyOf(enabled),
				List.copyOf(enabledCards), cardTime, scenario);
	}
}
