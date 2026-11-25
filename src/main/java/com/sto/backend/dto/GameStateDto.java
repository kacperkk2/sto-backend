package com.sto.backend.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.sto.backend.game.Bet;
import com.sto.backend.game.Card;
import com.sto.backend.game.Game;
import com.sto.backend.game.GameEngine;
import com.sto.backend.game.GameSettings;
import com.sto.backend.game.PlayedCard;
import com.sto.backend.game.PlayerResult;
import com.sto.backend.game.Question;
import com.sto.backend.game.Round;
import com.sto.backend.game.RoundEvent;
import com.sto.backend.game.RoundPhase;
import com.sto.backend.room.Player;
import com.sto.backend.room.Room;
import com.sto.backend.room.RoomStatus;

/**
 * Public room state, the same for every player. Never contains secrets: the correct answer and the bets
 * appear only after the round is revealed.
 *
 * @param version    grows with every change; clients ignore states older than the one they have
 * @param serverTime lets clients correct their clock for {@code phaseEndsAt} countdowns
 * @param players    in join order
 * @param ranking    player ids by score (empty in the lobby)
 * @param winnerIds  set when the game is {@code FINISHED}
 */
public record GameStateDto(
		String code,
		RoomStatus status,
		long version,
		Instant serverTime,
		String hostId,
		GameSettings settings,
		List<PlayerDto> players,
		List<String> ranking,
		RoundDto round,
		List<String> winnerIds) {

	/** @param cards cards not played yet; they change only once a cards phase is over */
	public record PlayerDto(String id, String nick, boolean isHost, int score, List<Card> cards) {
	}

	public record QuestionDto(String id, String category, String text, List<String> answers) {

		static QuestionDto of(Question q) {
			return new QuestionDto(q.id(), q.category(), q.text(), q.answers());
		}
	}

	/**
	 * @param openBettorIds bet first (Open bet event leader, Peek card targets)
	 * @param copyActorIds  may copy a bet after the betting (Blind copy event, Copy cards)
	 * @param openBets      public bets of the Open bet event's leader, visible after the open bet phase (Peek
	 *                      targets' bets only reach the peeking player, in {@code MyStateDto.seenBets})
	 * @param cardPlayerIds who decides in the cards phase (participants holding cards)
	 * @param cardDoneIds   who already played a card or passed (the choice stays secret until the phase ends)
	 * @param playedCards   public after the cards phase
	 * @param allInIds      must bet 100/0 (event and cards)
	 * @param doubledIds    points of the round x2 (event and cards)
	 * @param blitzIds      Blitz card targets, they bet until {@code blitzEndsAt}
	 * @param blitzEndsAt   own deadline of the blitz targets in the current phase, if earlier than its end
	 * @param betPlayerIds  participants who already bet
	 * @param result        only in {@code REVEALED}
	 */
	public record RoundDto(
			int number,
			int totalRounds,
			boolean tiebreak,
			List<String> participantIds,
			QuestionDto question,
			RoundEvent event,
			RoundPhase phase,
			Instant phaseEndsAt,
			String vaBanqueChooserId,
			String vaBanqueTargetId,
			List<String> openBettorIds,
			List<String> copyActorIds,
			Map<String, Bet> openBets,
			List<String> cardPlayerIds,
			List<String> cardDoneIds,
			List<PlayedCard> playedCards,
			List<String> allInIds,
			List<String> doubledIds,
			List<String> blitzIds,
			Instant blitzEndsAt,
			List<String> betPlayerIds,
			ResultDto result) {
	}

	/**
	 * @param correct index of the correct answer (0 = A, 1 = B)
	 * @param players outcome per participant, keyed by player id
	 */
	public record ResultDto(int correct, Map<String, PlayerResult> players) {
	}

	/** Must be called with the room lock held (or it takes it). */
	public static GameStateDto of(Room room, Instant now) {
		synchronized (room) {
			Game game = room.game().orElse(null);
			List<PlayerDto> players = room.players().stream()
					.map(p -> toPlayer(room, game, p))
					.toList();
			return new GameStateDto(
					room.code(),
					room.status(),
					room.version(),
					now,
					room.hostPlayerId(),
					// in a game: the game's settings; otherwise the ones of the next game
					game != null && room.status() == RoomStatus.IN_GAME ? game.settings() : room.settings(),
					players,
					game == null ? List.of() : GameEngine.ranking(game),
					game == null || game.currentRound() == null ? null : toRound(game, game.currentRound()),
					game == null ? List.of() : game.winnerIds());
		}
	}

	private static PlayerDto toPlayer(Room room, Game game, Player p) {
		int score = game == null ? 0 : game.score(p.id());
		List<Card> cards = game == null ? List.of() : game.hand(p.id());
		return new PlayerDto(p.id(), p.nick(), room.isHost(p.id()), score, cards);
	}

	private static RoundDto toRound(Game game, Round round) {
		boolean revealed = round.phase() == RoundPhase.REVEALED;
		ResultDto result = revealed ? new ResultDto(round.question().correct(), round.results()) : null;
		return new RoundDto(
				round.number(),
				game.settings().rounds(),
				round.tiebreak(),
				round.participantIds(),
				QuestionDto.of(round.question()),
				round.event(),
				round.phase(),
				round.phaseEndsAt(),
				round.vaBanqueChooserId(),
				round.vaBanqueTargetId(),
				round.openBettorIds(),
				round.copyActorIds(),
				round.openBets(),
				round.cardPlayerIds(),
				round.cardDoneIds(),
				round.playedCards(),
				round.allInIds(),
				round.doubledIds(),
				round.blitzIds(),
				round.blitzEndsAt(),
				round.donePlayerIds(),
				result);
	}
}
