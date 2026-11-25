package com.sto.backend.game;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** State of one game in a room. Guarded by the room's monitor; mutated only by {@link GameEngine}. */
public class Game {

	private final GameSettings settings;
	private final Map<String, Integer> scores = new LinkedHashMap<>();
	private final Set<String> usedQuestionIds = new HashSet<>();
	/** Players who already were the va banque chooser in the current cycle. */
	private final Set<String> vaBanqueChoosers = new HashSet<>();
	private int roundsStarted;
	private int regularRoundsStarted;
	private Round currentRound;
	private List<String> winnerIds = List.of();

	/** Cards not played yet, per player. */
	private final Map<String, EnumSet<Card>> hands = new LinkedHashMap<>();

	public Game(GameSettings settings, List<String> playerIds) {
		this.settings = settings;
		for (String id : playerIds) {
			scores.put(id, 0);
			EnumSet<Card> hand = EnumSet.noneOf(Card.class);
			hand.addAll(settings.cards());
			hands.put(id, hand);
		}
	}

	/** Cards the player still holds, in enum order. */
	public List<Card> hand(String playerId) {
		EnumSet<Card> hand = hands.get(playerId);
		return hand == null ? List.of() : List.copyOf(hand);
	}

	public boolean hasCards(String playerId) {
		EnumSet<Card> hand = hands.get(playerId);
		return hand != null && !hand.isEmpty();
	}

	public boolean holds(String playerId, Card card) {
		EnumSet<Card> hand = hands.get(playerId);
		return hand != null && hand.contains(card);
	}

	void useCard(String playerId, Card card) {
		EnumSet<Card> hand = hands.get(playerId);
		if (hand != null) {
			hand.remove(card);
		}
	}

	public GameSettings settings() {
		return settings;
	}

	public List<String> playerIds() {
		return List.copyOf(scores.keySet());
	}

	public int score(String playerId) {
		return scores.getOrDefault(playerId, 0);
	}

	public Round currentRound() {
		return currentRound;
	}

	/** Set when the game is finished. */
	public List<String> winnerIds() {
		return winnerIds;
	}

	public boolean isFinished() {
		return !winnerIds.isEmpty();
	}

	int roundsStarted() {
		return roundsStarted;
	}

	int regularRoundsStarted() {
		return regularRoundsStarted;
	}

	/** Questions played in earlier games (the host's history); drawn only once every other question was used. */
	public void excludeQuestions(Collection<String> questionIds) {
		for (String id : questionIds) {
			if (id != null) {
				usedQuestionIds.add(id);
			}
		}
	}

	Set<String> usedQuestionIds() {
		return usedQuestionIds;
	}

	Set<String> vaBanqueChoosers() {
		return vaBanqueChoosers;
	}

	void addScore(String playerId, int gain) {
		scores.merge(playerId, gain, Integer::sum);
	}

	void startRound(Round round) {
		roundsStarted++;
		if (!round.tiebreak()) {
			regularRoundsStarted++;
		}
		currentRound = round;
	}

	void finish(List<String> winners) {
		this.winnerIds = List.copyOf(winners);
	}
}
