package com.sto.backend.game;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Mutable state of one round. Guarded by the room's monitor; mutated only by {@link GameEngine}.
 * Effects of the event and of the played cards end up in the same sets (open bettors, copy actors, all in,
 * x2, blitz), so the same effect twice on one player counts once.
 */
public class Round {

	private final int number;
	private final boolean tiebreak;
	private final List<String> participantIds;
	private final Question question;
	private final RoundEvent event;

	private RoundPhase phase;
	private Instant phaseEndsAt;

	private String vaBanqueChooserId;
	private String vaBanqueTargetId;

	/** Who bets first: the leader of an Open bet round (public bet) and targets of Peek cards. */
	private final Set<String> openBettorIds = new LinkedHashSet<>();
	/** Open bettors whose bet everyone sees (the Open bet event's leader). */
	private final Set<String> publicOpenBettorIds = new LinkedHashSet<>();
	/** Peek cards: player -> targets whose bet only that player sees. */
	private final Map<String, Set<String>> peeks = new HashMap<>();
	/** Who may take over another bet after the betting: drawn player of Blind copy and Copy cards. */
	private final Set<String> copyActorIds = new LinkedHashSet<>();
	/** Copy card players: unlike the Blind copy event they see everyone's bets before deciding. */
	private final Set<String> seeingCopyActorIds = new HashSet<>();
	/** Copy actor -> whose bet they took over; secret until the reveal. */
	private final Map<String, String> copyTargets = new HashMap<>();
	private final Set<String> copyDecided = new HashSet<>();
	/** Targets of Va banque cards (the event's target is {@link #vaBanqueTargetId}). */
	private final Set<String> allInCardIds = new LinkedHashSet<>();
	/** Players of Double cards. */
	private final Set<String> doubledCardIds = new LinkedHashSet<>();
	/** Targets of Blitz cards. */
	private final Set<String> blitzIds = new LinkedHashSet<>();
	/** Own deadline of the blitz targets betting in the current phase; {@code null} when there is none. */
	private Instant blitzEndsAt;
	private boolean blitzOver;
	/** Tax cards: player -> target whose half of the round's gain the player also gets. */
	private final Map<String, String> taxes = new HashMap<>();

	/** Who decides in {@link RoundPhase#CARDS}: participants holding cards when the round starts. */
	private final Set<String> cardPlayerIds = new LinkedHashSet<>();
	/** Secret choices in {@link RoundPhase#CARDS}; a player who passed is in {@link #cardDone} only. */
	private final Map<String, PlayedCard> cardChoices = new HashMap<>();
	private final Set<String> cardDone = new HashSet<>();
	/** Public once the cards phase is over, in participant order. */
	private final List<PlayedCard> playedCards = new ArrayList<>();

	/** Bets placed by players (or assigned on timeout), in the order they came. */
	private final Map<String, Bet> bets = new LinkedHashMap<>();
	private final Set<String> autoBets = new HashSet<>();
	private Map<String, PlayerResult> results = Map.of();

	Round(int number, boolean tiebreak, List<String> participantIds, Question question, RoundEvent event) {
		this.number = number;
		this.tiebreak = tiebreak;
		this.participantIds = List.copyOf(participantIds);
		this.question = question;
		this.event = event;
	}

	public int number() {
		return number;
	}

	public boolean tiebreak() {
		return tiebreak;
	}

	public List<String> participantIds() {
		return participantIds;
	}

	public boolean isParticipant(String playerId) {
		return participantIds.contains(playerId);
	}

	public Question question() {
		return question;
	}

	public RoundEvent event() {
		return event;
	}

	public RoundPhase phase() {
		return phase;
	}

	public Instant phaseEndsAt() {
		return phaseEndsAt;
	}

	public String vaBanqueChooserId() {
		return vaBanqueChooserId;
	}

	public String vaBanqueTargetId() {
		return vaBanqueTargetId;
	}

	public List<String> openBettorIds() {
		return List.copyOf(openBettorIds);
	}

	public boolean isOpenBettor(String playerId) {
		return openBettorIds.contains(playerId);
	}

	/** Public from the start (event) or from the end of the cards phase (cards). */
	public List<String> copyActorIds() {
		return List.copyOf(copyActorIds);
	}

	public boolean isCopyActor(String playerId) {
		return copyActorIds.contains(playerId);
	}

	/** Whose bet the copy actor took over, once chosen. */
	public String copyTargetOf(String playerId) {
		return copyTargets.get(playerId);
	}

	public boolean hasDecidedCopy(String playerId) {
		return copyDecided.contains(playerId);
	}

	/** Public open bets (Open bet event), once the open bet phase is over (by then they never change). */
	public Map<String, Bet> openBets() {
		if (!openBetsDone()) {
			return Map.of();
		}
		return betsOf(publicOpenBettorIds);
	}

	/**
	 * Bets only this player sees: bets of their Peek targets (after the open bet phase) and, for a Copy
	 * card player deciding in {@link RoundPhase#COPY_PICK}, everyone else's bets.
	 */
	public Map<String, Bet> seenBetsOf(String viewerId) {
		if (phase == RoundPhase.COPY_PICK && seeingCopyActorIds.contains(viewerId)) {
			return betsOf(participantIds.stream().filter(id -> !id.equals(viewerId)).toList());
		}
		if (!openBetsDone() || phase == RoundPhase.REVEALED) {
			return Map.of();
		}
		return betsOf(peeks.getOrDefault(viewerId, Set.of()));
	}

	/** Who peeks at this player's bet (Peek cards aimed at them). */
	public List<String> peekersOf(String targetId) {
		return participantIds.stream()
				.filter(id -> peeks.getOrDefault(id, Set.of()).contains(targetId))
				.toList();
	}

	private boolean openBetsDone() {
		return phase == RoundPhase.BETTING || phase == RoundPhase.COPY_PICK || phase == RoundPhase.REVEALED;
	}

	private Map<String, Bet> betsOf(Collection<String> ids) {
		Map<String, Bet> result = new LinkedHashMap<>();
		for (String id : ids) {
			Bet bet = bets.get(id);
			if (bet != null) {
				result.put(id, bet);
			}
		}
		return result;
	}

	public Optional<Bet> betOf(String playerId) {
		return Optional.ofNullable(bets.get(playerId));
	}

	/** Whether the player has nothing more to do in the betting phase (bet placed). */
	public boolean isDone(String playerId) {
		return bets.containsKey(playerId);
	}

	/**
	 * Whether the player may take back their own bet to change it: they placed it themselves, it is not
	 * a binding open bet and the player's blitz time is not over.
	 */
	public boolean canChangeBet(String playerId) {
		return bets.containsKey(playerId)
				&& !autoBets.contains(playerId)
				&& !openBettorIds.contains(playerId)
				&& !(blitzOver && blitzIds.contains(playerId));
	}

	/** Participants who are done, in participant order. */
	public List<String> donePlayerIds() {
		return participantIds.stream().filter(this::isDone).toList();
	}

	public boolean allDone() {
		return participantIds.stream().allMatch(this::isDone);
	}

	boolean allOpenBetsDone() {
		return openBettorIds.stream().allMatch(this::isDone);
	}

	/** Per-player outcome; empty until {@link RoundPhase#REVEALED}. */
	public Map<String, PlayerResult> results() {
		return Collections.unmodifiableMap(results);
	}

	public boolean isDouble() {
		return event == RoundEvent.DOUBLE;
	}

	public int multiplier(String playerId) {
		return isDouble() || doubledCardIds.contains(playerId) ? GameEngine.DOUBLE_MULTIPLIER : 1;
	}

	/** Only 100/0 bets: everyone in {@link RoundEvent#ALL_IN}, the va banque target, Va banque card targets. */
	public boolean mustGoAllIn(String playerId) {
		return event == RoundEvent.ALL_IN || playerId.equals(vaBanqueTargetId) || allInCardIds.contains(playerId);
	}

	/** Participants who must bet 100/0, in participant order. */
	public List<String> allInIds() {
		return participantIds.stream().filter(this::mustGoAllIn).toList();
	}

	/** Participants whose points of the round count x2, in participant order. */
	public List<String> doubledIds() {
		return participantIds.stream().filter(id -> multiplier(id) > 1).toList();
	}

	public List<String> blitzIds() {
		return List.copyOf(blitzIds);
	}

	public boolean isBlitz(String playerId) {
		return blitzIds.contains(playerId);
	}

	public Instant blitzEndsAt() {
		return blitzEndsAt;
	}

	/** Own deadline of the player in the current phase (blitz target), otherwise {@code null}. */
	public Instant deadlineOf(String playerId) {
		return blitzEndsAt != null && blitzIds.contains(playerId) && !isDone(playerId) ? blitzEndsAt : null;
	}

	/** Whose gain the player taxes (Tax card), otherwise {@code null}. */
	public String taxTargetOf(String playerId) {
		return taxes.get(playerId);
	}

	public List<String> cardPlayerIds() {
		return List.copyOf(cardPlayerIds);
	}

	public boolean isCardPlayer(String playerId) {
		return cardPlayerIds.contains(playerId);
	}

	/** Who already played a card or passed in the cards phase, in participant order. */
	public List<String> cardDoneIds() {
		return participantIds.stream().filter(cardDone::contains).toList();
	}

	public boolean hasDecidedCard(String playerId) {
		return cardDone.contains(playerId);
	}

	/** The player's own choice in the cards phase ({@code null}: not decided or passed). */
	public PlayedCard cardChoiceOf(String playerId) {
		return cardChoices.get(playerId);
	}

	public List<PlayedCard> playedCards() {
		return List.copyOf(playedCards);
	}

	void enterPhase(RoundPhase phase, Instant endsAt) {
		this.phase = phase;
		this.phaseEndsAt = endsAt;
	}

	void setVaBanqueChooserId(String id) {
		this.vaBanqueChooserId = id;
	}

	void setVaBanqueTargetId(String id) {
		this.vaBanqueTargetId = id;
	}

	/** The Open bet event's leader: bets first, everyone sees the bet. */
	void addPublicOpenBettor(String id) {
		openBettorIds.add(id);
		publicOpenBettorIds.add(id);
	}

	void addCopyActor(String id) {
		copyActorIds.add(id);
	}

	void decideCopy(String actorId, String targetId) {
		copyDecided.add(actorId);
		if (targetId != null) {
			copyTargets.put(actorId, targetId);
		}
	}

	boolean allCopiesDecided() {
		return copyDecided.containsAll(copyActorIds);
	}

	void addCardPlayer(String id) {
		cardPlayerIds.add(id);
	}

	void decideCard(String playerId, PlayedCard card) {
		cardDone.add(playerId);
		if (card != null) {
			cardChoices.put(playerId, card);
		}
	}

	boolean allCardsDecided() {
		return cardDone.containsAll(cardPlayerIds);
	}

	/** Makes the choices public and applies their effects; the same effect on one player counts once. */
	void applyCards() {
		for (String id : participantIds) {
			PlayedCard played = cardChoices.get(id);
			if (played == null) {
				continue;
			}
			playedCards.add(played);
			switch (played.card()) {
				case VA_BANQUE -> allInCardIds.add(played.targetId());
				case DOUBLE -> doubledCardIds.add(played.playerId());
				case PEEK -> {
					openBettorIds.add(played.targetId());
					peeks.computeIfAbsent(played.playerId(), k -> new LinkedHashSet<>()).add(played.targetId());
				}
				case COPY -> {
					copyActorIds.add(played.playerId());
					seeingCopyActorIds.add(played.playerId());
				}
				case BLITZ -> blitzIds.add(played.targetId());
				case TAX -> taxes.put(played.playerId(), played.targetId());
			}
		}
	}

	void setBlitzEndsAt(Instant endsAt) {
		this.blitzEndsAt = endsAt;
		this.blitzOver = false;
	}

	void endBlitz() {
		this.blitzEndsAt = null;
		this.blitzOver = true;
	}

	void putBet(String playerId, Bet bet, boolean auto) {
		bets.put(playerId, bet);
		if (auto) {
			autoBets.add(playerId);
		}
	}

	void removeBet(String playerId) {
		bets.remove(playerId);
		autoBets.remove(playerId);
	}

	boolean isAutoBet(String playerId) {
		return autoBets.contains(playerId);
	}

	void setResults(Map<String, PlayerResult> results) {
		this.results = new LinkedHashMap<>(results);
	}
}
