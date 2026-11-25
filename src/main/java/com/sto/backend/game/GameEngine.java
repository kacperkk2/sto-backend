package com.sto.backend.game;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

import com.sto.backend.error.AlreadyBetException;
import com.sto.backend.error.InvalidStateException;
import com.sto.backend.error.NotYourTurnException;
import com.sto.backend.error.ValidationException;

/**
 * Game rules (see {@code GAME_DESIGN.md} in the frontend repo): drawing events and actors, cards,
 * phase transitions, scoring and tie-breaks. No Spring, no clock: callers pass {@code now} and hold the
 * room lock.
 */
public class GameEngine {

	public static final int DOUBLE_MULTIPLIER = 2;
	/** Betting time of a Blitz round and of Blitz card targets; a shorter host setting is kept. */
	public static final int BLITZ_BET_SECONDS = 15;

	private final QuestionBank questions;
	private final RandomGenerator random;
	private final Duration vaBanquePick;
	private final Duration copyPick;

	public GameEngine(QuestionBank questions, RandomGenerator random, Duration vaBanquePick, Duration copyPick) {
		this.questions = questions;
		this.random = random;
		this.vaBanquePick = vaBanquePick;
		this.copyPick = copyPick;
	}

	/**
	 * Starts the next round: a regular one while there are rounds left, then tie-break rounds while the
	 * leaders are tied. Returns empty and finishes the game when there is a single winner.
	 */
	public Optional<Round> nextRound(Game game, Instant now) {
		GameSettings settings = game.settings();
		boolean tiebreak = game.regularRoundsStarted() >= settings.rounds();
		List<String> participants;
		RoundEvent event;
		if (tiebreak) {
			List<String> leaders = leaders(game, game.playerIds());
			if (leaders.size() == 1) {
				game.finish(leaders);
				return Optional.empty();
			}
			participants = leaders;
			event = null;
		} else {
			participants = game.playerIds();
			boolean lastRegular = game.regularRoundsStarted() + 1 == settings.rounds();
			event = drawEvent(settings, lastRegular);
		}

		Question question = questions.draw(settings.scenario(), game.usedQuestionIds(), random);
		Round round = new Round(game.roundsStarted() + 1, tiebreak, participants, question, event);
		game.startRound(round);

		// event actors are public from the start, cards add to them after the cards phase
		if (event == RoundEvent.VA_BANQUE) {
			round.setVaBanqueChooserId(drawVaBanqueChooser(game, participants));
		} else if (event == RoundEvent.OPEN_BET) {
			round.addPublicOpenBettor(pick(leaders(game, participants)));
		} else if (event == RoundEvent.BLIND_COPY) {
			round.addCopyActor(pick(participants));
		}

		if (!tiebreak) {
			participants.stream().filter(game::hasCards).forEach(round::addCardPlayer);
		}
		if (round.cardPlayerIds().isEmpty()) {
			advanceAfterCards(game, round, now);
		} else {
			round.enterPhase(RoundPhase.CARDS, now.plusSeconds(settings.cardSeconds()));
		}
		return Optional.of(round);
	}

	/** Cards phase: plays one own card, aimed at another participant or (self cards) at the player. */
	public void playCard(Game game, String playerId, Card card, String targetId, Instant now) {
		Round round = requireCardPlayer(game, playerId);
		if (card == null || !game.holds(playerId, card)) {
			throw new ValidationException("card: not in your hand");
		}
		String target = playerId;
		if (card.targetsOther()) {
			if (targetId == null || targetId.equals(playerId) || !round.isParticipant(targetId)) {
				throw new ValidationException("targetId: must be another player of this round");
			}
			target = targetId;
		}
		round.decideCard(playerId, new PlayedCard(playerId, card, target));
		resolveCardsIfAllDecided(game, round, now);
	}

	/** Cards phase: plays no card this round. */
	public void passCard(Game game, String playerId, Instant now) {
		Round round = requireCardPlayer(game, playerId);
		round.decideCard(playerId, null);
		resolveCardsIfAllDecided(game, round, now);
	}

	private static Round requireCardPlayer(Game game, String playerId) {
		Round round = requireRound(game, RoundPhase.CARDS);
		if (!round.isCardPlayer(playerId)) {
			throw new NotYourTurnException("You have no cards to play");
		}
		if (round.hasDecidedCard(playerId)) {
			throw new InvalidStateException("You already decided about a card this round");
		}
		return round;
	}

	private void resolveCardsIfAllDecided(Game game, Round round, Instant now) {
		if (round.allCardsDecided()) {
			resolveCards(game, round, now);
		}
	}

	/** Uses up the played cards (also those without effect), applies them and moves on. */
	private void resolveCards(Game game, Round round, Instant now) {
		round.applyCards();
		round.playedCards().forEach(p -> game.useCard(p.playerId(), p.card()));
		advanceAfterCards(game, round, now);
	}

	private void advanceAfterCards(Game game, Round round, Instant now) {
		if (round.event() == RoundEvent.VA_BANQUE) {
			round.enterPhase(RoundPhase.VA_BANQUE_PICK, now.plus(vaBanquePick));
		} else {
			startBettingPhases(game, round, now);
		}
	}

	public void pickVaBanqueTarget(Game game, String playerId, String targetId, Instant now) {
		Round round = requireRound(game, RoundPhase.VA_BANQUE_PICK);
		if (!playerId.equals(round.vaBanqueChooserId())) {
			throw new NotYourTurnException("Only the va banque chooser can pick the target");
		}
		if (targetId == null || targetId.equals(playerId) || !round.isParticipant(targetId)) {
			throw new ValidationException("targetId: must be another player of this round");
		}
		round.setVaBanqueTargetId(targetId);
		startBettingPhases(game, round, now);
	}

	public void placeBet(Game game, String playerId, Bet bet, Instant now) {
		Round round = requireRound(game, null);
		if (!round.isParticipant(playerId)) {
			throw new NotYourTurnException("You do not take part in this round");
		}
		switch (round.phase()) {
			case OPEN_BET -> {
				if (!round.isOpenBettor(playerId)) {
					throw new NotYourTurnException("Wait for the open bets");
				}
				requireValidBet(round, playerId, bet);
				round.putBet(playerId, bet, false);
				if (round.allOpenBetsDone()) {
					startBetting(game, round, now);
				}
			}
			case BETTING -> {
				requireValidBet(round, playerId, bet);
				round.putBet(playerId, bet, false);
				endBettingIfAllDone(game, round, now);
			}
			default -> throw new InvalidStateException("Betting is not open, phase is " + round.phase());
		}
	}

	private static void requireValidBet(Round round, String playerId, Bet bet) {
		if (round.isDone(playerId)) {
			throw new AlreadyBetException();
		}
		if (round.mustGoAllIn(playerId) && !bet.isAllIn()) {
			throw new ValidationException("You must bet 100/0");
		}
	}

	/**
	 * Takes back the own bet to change it: the player counts as not done again (an unchanged bet at the
	 * timeout becomes the auto bet). Not for binding open bets nor after the own blitz time.
	 */
	public void withdrawBet(Game game, String playerId) {
		Round round = requireRound(game, RoundPhase.BETTING);
		if (!round.isParticipant(playerId)) {
			throw new NotYourTurnException("You do not take part in this round");
		}
		if (!round.canChangeBet(playerId)) {
			throw new InvalidStateException("There is no bet you can change");
		}
		round.removeBet(playerId);
	}

	/** After the betting: a copy actor takes over another player's bet. */
	public void copyBet(Game game, String playerId, String targetId) {
		Round round = requireCopyActor(game, playerId);
		if (targetId == null || targetId.equals(playerId) || !round.isParticipant(targetId)) {
			throw new ValidationException("targetId: must be another player of this round");
		}
		round.decideCopy(playerId, targetId);
		revealIfAllCopiesDecided(game, round);
	}

	/** After the betting: a copy actor stays with their own bet. */
	public void keepBet(Game game, String playerId) {
		Round round = requireCopyActor(game, playerId);
		round.decideCopy(playerId, null);
		revealIfAllCopiesDecided(game, round);
	}

	private static Round requireCopyActor(Game game, String playerId) {
		Round round = requireRound(game, RoundPhase.COPY_PICK);
		if (!round.isCopyActor(playerId)) {
			throw new NotYourTurnException("Only a copy actor can copy a bet");
		}
		if (round.hasDecidedCopy(playerId)) {
			throw new InvalidStateException("You already decided about the copy");
		}
		return round;
	}

	private void revealIfAllCopiesDecided(Game game, Round round) {
		if (round.allCopiesDecided()) {
			reveal(game, round);
		}
	}

	/**
	 * Dev bots act right away: they pass in the cards phase, the va banque chooser picks a random target,
	 * everyone else bets 50/50 (who must go all in a random 100/0) and copy actors keep their own bet.
	 */
	public void playBots(Game game, Set<String> botIds, Instant now) {
		if (botIds.isEmpty()) {
			return;
		}
		boolean acted = true;
		while (acted && game.currentRound() != null && !game.isFinished()) {
			Round round = game.currentRound();
			RoundPhase phase = round.phase();
			acted = switch (phase) {
				case CARDS -> {
					boolean any = false;
					for (String id : round.cardPlayerIds()) {
						if (round.phase() != phase) {
							break;
						}
						if (botIds.contains(id) && !round.hasDecidedCard(id)) {
							passCard(game, id, now);
							any = true;
						}
					}
					yield any;
				}
				case VA_BANQUE_PICK -> {
					String chooser = round.vaBanqueChooserId();
					if (!botIds.contains(chooser)) {
						yield false;
					}
					List<String> candidates = round.participantIds().stream()
							.filter(id -> !id.equals(chooser))
							.toList();
					pickVaBanqueTarget(game, chooser, pick(candidates), now);
					yield true;
				}
				case OPEN_BET, BETTING -> {
					List<String> bettors = phase == RoundPhase.OPEN_BET ? round.openBettorIds() : round.participantIds();
					boolean any = false;
					for (String id : bettors) {
						if (round.phase() != phase) {
							break;
						}
						if (botIds.contains(id) && !round.isDone(id)) {
							placeBet(game, id, autoBet(round, id), now);
							any = true;
						}
					}
					yield any;
				}
				case COPY_PICK -> {
					boolean any = false;
					for (String id : round.copyActorIds()) {
						if (round.phase() != phase) {
							break;
						}
						if (botIds.contains(id) && !round.hasDecidedCopy(id)) {
							keepBet(game, id);
							any = true;
						}
					}
					yield any;
				}
				case REVEALED -> false;
			};
		}
	}

	/** Ends the current phase because its time is up. */
	public void timeout(Game game, Instant now) {
		Round round = game.currentRound();
		switch (round.phase()) {
			case CARDS -> resolveCards(game, round, now); // no choice in time = pass
			case VA_BANQUE_PICK -> {
				List<String> candidates = round.participantIds().stream()
						.filter(id -> !id.equals(round.vaBanqueChooserId()))
						.toList();
				round.setVaBanqueTargetId(pick(candidates));
				startBettingPhases(game, round, now);
			}
			case OPEN_BET -> {
				for (String id : round.openBettorIds()) {
					if (!round.isDone(id)) {
						round.putBet(id, autoBet(round, id), true);
					}
				}
				startBetting(game, round, now);
			}
			case BETTING -> endBetting(game, round, now);
			case COPY_PICK -> reveal(game, round); // who did not decide keeps their own bet
			case REVEALED -> {
				// nothing to time out
			}
		}
	}

	/** The own time of the blitz targets is up: they get the auto bet, the phase goes on for the rest. */
	public void blitzTimeout(Game game, Instant now) {
		Round round = game.currentRound();
		RoundPhase phase = round.phase();
		if (round.blitzEndsAt() == null || (phase != RoundPhase.OPEN_BET && phase != RoundPhase.BETTING)) {
			return;
		}
		for (String id : round.blitzIds()) {
			boolean betsNow = phase == RoundPhase.BETTING || round.isOpenBettor(id);
			if (betsNow && !round.isDone(id)) {
				round.putBet(id, autoBet(round, id), true);
			}
		}
		round.endBlitz();
		if (phase == RoundPhase.OPEN_BET) {
			if (round.allOpenBetsDone()) {
				startBetting(game, round, now);
			}
		} else {
			endBettingIfAllDone(game, round, now);
		}
	}

	/** After the cards and the va banque pick: open bets first (if there are open bettors), then betting. */
	private void startBettingPhases(Game game, Round round, Instant now) {
		if (round.openBettorIds().isEmpty()) {
			startBetting(game, round, now);
			return;
		}
		Instant endsAt = now.plusSeconds(betSeconds(game, round));
		round.enterPhase(RoundPhase.OPEN_BET, endsAt);
		boolean blitzBetting = round.blitzIds().stream().anyMatch(round::isOpenBettor);
		setBlitz(game, round, now, endsAt, blitzBetting);
	}

	private void startBetting(Game game, Round round, Instant now) {
		Instant endsAt = now.plusSeconds(betSeconds(game, round));
		round.enterPhase(RoundPhase.BETTING, endsAt);
		boolean blitzBetting = round.blitzIds().stream().anyMatch(id -> !round.isDone(id));
		setBlitz(game, round, now, endsAt, blitzBetting);
		endBettingIfAllDone(game, round, now);
	}

	/** Own deadline of the blitz targets, only when it is earlier than the end of the phase. */
	private static void setBlitz(Game game, Round round, Instant now, Instant phaseEndsAt, boolean blitzBetting) {
		Instant blitzEndsAt = now.plusSeconds(Math.min(game.settings().betSeconds(), BLITZ_BET_SECONDS));
		round.setBlitzEndsAt(blitzBetting && blitzEndsAt.isBefore(phaseEndsAt) ? blitzEndsAt : null);
	}

	private void endBettingIfAllDone(Game game, Round round, Instant now) {
		if (round.allDone()) {
			endBetting(game, round, now);
		}
	}

	/**
	 * Assigns the auto bets of players who did not bet, then lets the copy actors decide or reveals the
	 * round.
	 */
	private void endBetting(Game game, Round round, Instant now) {
		for (String id : round.participantIds()) {
			if (!round.isDone(id)) {
				round.putBet(id, autoBet(round, id), true);
			}
		}
		round.setBlitzEndsAt(null);
		if (!round.copyActorIds().isEmpty()) {
			round.enterPhase(RoundPhase.COPY_PICK, now.plus(copyPick));
		} else {
			reveal(game, round);
		}
	}

	/**
	 * Copies take the copied player's own bet (no chains); the multiplier is the copier's. A Tax card adds
	 * half (rounded down) of the target's bet gain (their multiplier included, their own tax not: no chains);
	 * the card player's multiplier does not apply to it and the target loses nothing.
	 */
	private void reveal(Game game, Round round) {
		int correct = round.question().correct();
		Map<String, Integer> betGains = new HashMap<>();
		for (String id : round.participantIds()) {
			String copiedFrom = round.copyTargetOf(id);
			Bet bet = round.betOf(copiedFrom != null ? copiedFrom : id).orElseThrow();
			betGains.put(id, bet.on(correct) * round.multiplier(id));
		}
		Map<String, PlayerResult> results = new LinkedHashMap<>();
		for (String id : round.participantIds()) {
			String copiedFrom = round.copyTargetOf(id);
			String source = copiedFrom != null ? copiedFrom : id;
			String taxTarget = round.taxTargetOf(id);
			int taxGain = taxTarget == null ? 0 : betGains.get(taxTarget) / 2;
			int gain = betGains.get(id) + taxGain;
			game.addScore(id, gain);
			results.put(id, new PlayerResult(round.betOf(source).orElseThrow(), round.isAutoBet(source), copiedFrom,
					gain, taxGain));
		}
		round.setResults(results);
		round.enterPhase(RoundPhase.REVEALED, null);
	}

	private static int betSeconds(Game game, Round round) {
		int seconds = game.settings().betSeconds();
		return round.event() == RoundEvent.BLITZ ? Math.min(seconds, BLITZ_BET_SECONDS) : seconds;
	}

	/** 50/50, or a random 100/0 for who must go all in. */
	private Bet autoBet(Round round, String playerId) {
		if (!round.mustGoAllIn(playerId)) {
			return Bet.HALF;
		}
		return random.nextBoolean() ? Bet.ALL_A : Bet.ALL_B;
	}

	private RoundEvent drawEvent(GameSettings settings, boolean lastRegular) {
		if (lastRegular && settings.lastRoundDouble()) {
			return RoundEvent.DOUBLE;
		}
		List<RoundEvent> events = settings.events();
		if (events.isEmpty() || random.nextInt(100) >= settings.eventChance()) {
			return null;
		}
		return events.get(random.nextInt(events.size()));
	}

	/** Draws without repetition; the pool starts over once everyone was the chooser. */
	private String drawVaBanqueChooser(Game game, List<String> participants) {
		List<String> pool = participants.stream().filter(id -> !game.vaBanqueChoosers().contains(id)).toList();
		if (pool.isEmpty()) {
			game.vaBanqueChoosers().clear();
			pool = participants;
		}
		String chooser = pick(pool);
		game.vaBanqueChoosers().add(chooser);
		return chooser;
	}

	/** Players with the top score; more than one means a tie (settled only by a tie-break round). */
	private static List<String> leaders(Game game, List<String> playerIds) {
		int best = playerIds.stream().mapToInt(game::score).max().orElse(0);
		return playerIds.stream().filter(id -> game.score(id) == best).toList();
	}

	/** Ranking by score, descending; ties keep the players' order. */
	public static List<String> ranking(Game game) {
		List<String> ids = new ArrayList<>(game.playerIds());
		ids.sort(Comparator.comparingInt(game::score).reversed());
		return ids;
	}

	private String pick(List<String> ids) {
		return ids.get(random.nextInt(ids.size()));
	}

	private static Round requireRound(Game game, RoundPhase phase) {
		Round round = game.currentRound();
		if (round == null || game.isFinished()) {
			throw new InvalidStateException("No round in progress");
		}
		if (phase != null && round.phase() != phase) {
			throw new InvalidStateException("Not allowed in phase " + round.phase());
		}
		return round;
	}
}
