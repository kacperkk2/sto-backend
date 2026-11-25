package com.sto.backend.game;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import com.sto.backend.auth.AuthenticatedPlayer;
import com.sto.backend.dto.BetRequest;
import com.sto.backend.dto.CardRequest;
import com.sto.backend.dto.GameSettingsRequest;
import com.sto.backend.dto.GameStateDto;
import com.sto.backend.error.InvalidStateException;
import com.sto.backend.error.NotEnoughPlayersException;
import com.sto.backend.error.RoomNotFoundException;
import com.sto.backend.error.ValidationException;
import com.sto.backend.room.Player;
import com.sto.backend.room.Room;
import com.sto.backend.room.RoomEvent;
import com.sto.backend.room.RoomStatus;

/**
 * Game actions of players and phase deadlines. Every change happens under the room lock, bumps the room
 * version, schedules the deadline of the (new) phase and publishes {@link RoomEvent.StateChanged}.
 */
@Service
public class GameService {

	public static final int MIN_PLAYERS = 2;
	static final int MAX_EXCLUDED_QUESTIONS = 10_000;

	private static final Logger log = LoggerFactory.getLogger(GameService.class);

	private final QuestionBank questions;
	private final GameEngine engine;
	private final TaskScheduler scheduler;
	private final ApplicationEventPublisher events;
	private final Clock clock;

	public GameService(QuestionBank questions, TaskScheduler scheduler, ApplicationEventPublisher events, Clock clock,
			@Value("${app.game.va-banque-pick:10s}") Duration vaBanquePick,
			@Value("${app.game.copy-pick:15s}") Duration copyPick) {
		this.questions = questions;
		this.engine = new GameEngine(questions, new SecureRandom(), vaBanquePick, copyPick);
		this.scheduler = scheduler;
		this.events = events;
		this.clock = clock;
	}

	/** Host changes the settings of the next game; everyone sees them in the lobby. */
	public GameStateDto updateSettings(AuthenticatedPlayer me, GameSettingsRequest request) {
		me.requireHost();
		GameSettings settings = toSettings(request);
		return change(me.room(), now -> {
			requireNotInGame(me.room(), "Settings can be changed");
			me.room().updateSettings(settings);
		});
	}

	/**
	 * Starts a new game (from the lobby or after a finished game); scores start from 0. Without a body the
	 * room's current settings are used.
	 */
	public GameStateDto startGame(AuthenticatedPlayer me, GameSettingsRequest request) {
		me.requireHost();
		GameSettings requested = request == null || !request.hasSettings() ? null : toSettings(request);
		List<String> excluded = request == null || request.excludedQuestionIds() == null ? List.of()
				: request.excludedQuestionIds();
		if (excluded.size() > MAX_EXCLUDED_QUESTIONS) {
			throw new ValidationException("excludedQuestionIds: at most " + MAX_EXCLUDED_QUESTIONS);
		}
		return change(me.room(), now -> {
			Room room = me.room();
			requireNotInGame(room, "Game can be started");
			List<String> playerIds = room.players().stream().map(Player::id).toList();
			if (playerIds.size() < MIN_PLAYERS) {
				throw new NotEnoughPlayersException();
			}
			if (requested != null) {
				room.updateSettings(requested);
			}
			Game game = new Game(room.settings(), playerIds);
			game.excludeQuestions(excluded);
			room.startGame(game);
			engine.nextRound(game, now);
		});
	}

	/** Host moves on from a revealed round: next round, a tie-break round or the end of the game. */
	public GameStateDto nextRound(AuthenticatedPlayer me) {
		me.requireHost();
		return change(me.room(), now -> {
			Game game = requireGame(me.room());
			if (game.currentRound().phase() != RoundPhase.REVEALED) {
				throw new InvalidStateException("Next round only after the current one is revealed");
			}
			if (engine.nextRound(game, now).isEmpty()) {
				me.room().finishGame();
			}
		});
	}

	/** Host takes everyone from a finished game back to the lobby, to change the settings and start again. */
	public GameStateDto returnToLobby(AuthenticatedPlayer me) {
		me.requireHost();
		return change(me.room(), now -> {
			Room room = me.room();
			if (room.status() != RoomStatus.FINISHED) {
				throw new InvalidStateException("Back to lobby only from FINISHED, room is " + room.status());
			}
			room.returnToLobby();
		});
	}

	public GameStateDto playCard(AuthenticatedPlayer me, CardRequest request) {
		Card card = request == null ? null : request.card();
		String targetId = request == null ? null : request.targetId();
		return change(me.room(), now -> engine.playCard(requireGame(me.room()), me.player().id(), card, targetId,
				now));
	}

	public GameStateDto passCard(AuthenticatedPlayer me) {
		return change(me.room(), now -> engine.passCard(requireGame(me.room()), me.player().id(), now));
	}

	public GameStateDto pickVaBanqueTarget(AuthenticatedPlayer me, String targetId) {
		return change(me.room(), now -> engine.pickVaBanqueTarget(requireGame(me.room()), me.player().id(),
				targetId, now));
	}

	public GameStateDto placeBet(AuthenticatedPlayer me, BetRequest request) {
		Bet bet = request == null ? Bet.of(null, null) : Bet.of(request.onA(), request.onB());
		return change(me.room(), now -> engine.placeBet(requireGame(me.room()), me.player().id(), bet, now));
	}

	public GameStateDto withdrawBet(AuthenticatedPlayer me) {
		return change(me.room(), now -> engine.withdrawBet(requireGame(me.room()), me.player().id()));
	}

	public GameStateDto copyBet(AuthenticatedPlayer me, String targetId) {
		return change(me.room(), now -> engine.copyBet(requireGame(me.room()), me.player().id(), targetId));
	}

	public GameStateDto keepBet(AuthenticatedPlayer me) {
		return change(me.room(), now -> engine.keepBet(requireGame(me.room()), me.player().id()));
	}

	private GameSettings toSettings(GameSettingsRequest request) {
		return request == null
				? GameSettings.defaults(questions.defaultScenarioId())
				: GameSettings.of(request.rounds(), request.eventChance(), request.betSeconds(),
						request.lastRoundDouble(), request.events(), request.cards(), request.cardSeconds(),
						questions.resolveScenarioId(request.scenario()));
	}

	private static void requireNotInGame(Room room, String action) {
		if (room.status() != RoomStatus.LOBBY && room.status() != RoomStatus.FINISHED) {
			throw new InvalidStateException(action + " only in LOBBY or FINISHED, room is " + room.status());
		}
	}

	private GameStateDto change(Room room, Consumer<Instant> action) {
		GameStateDto state;
		synchronized (room) {
			if (room.status() == RoomStatus.CLOSED) {
				throw new RoomNotFoundException();
			}
			Instant now = clock.instant();
			action.accept(now);
			state = commit(room, now);
		}
		events.publishEvent(new RoomEvent.StateChanged(room.code(), state));
		return state;
	}

	/** Must hold the room lock. */
	private GameStateDto commit(Room room, Instant now) {
		if (room.status() == RoomStatus.IN_GAME) {
			Set<String> botIds = room.players().stream()
					.filter(Player::bot)
					.map(Player::id)
					.collect(Collectors.toSet());
			engine.playBots(room.game().orElseThrow(), botIds, now);
		}
		room.bumpVersion();
		scheduleDeadline(room);
		return GameStateDto.of(room, now);
	}

	private void scheduleDeadline(Room room) {
		if (room.status() != RoomStatus.IN_GAME) {
			return;
		}
		Round round = room.game().map(Game::currentRound).orElse(null);
		if (round == null || round.phaseEndsAt() == null) {
			return;
		}
		int number = round.number();
		RoundPhase phase = round.phase();
		if (room.markDeadlineScheduled(number + ":" + phase)) {
			scheduler.schedule(() -> onDeadline(room, number, phase, false), round.phaseEndsAt());
		}
		// own, earlier deadline of Blitz card targets in this phase
		if (round.blitzEndsAt() != null && room.markDeadlineScheduled(number + ":" + phase + ":blitz")) {
			scheduler.schedule(() -> onDeadline(room, number, phase, true), round.blitzEndsAt());
		}
	}

	private void onDeadline(Room room, int roundNumber, RoundPhase phase, boolean blitz) {
		try {
			GameStateDto state;
			synchronized (room) {
				if (room.status() != RoomStatus.IN_GAME) {
					return;
				}
				Game game = room.game().orElseThrow();
				Round round = game.currentRound();
				if (round.number() != roundNumber || round.phase() != phase) {
					return; // the phase ended earlier (everyone acted)
				}
				if (blitz && round.blitzEndsAt() == null) {
					return; // the blitz targets already bet
				}
				Instant now = clock.instant();
				if (blitz) {
					engine.blitzTimeout(game, now);
				} else {
					engine.timeout(game, now);
				}
				state = commit(room, now);
			}
			events.publishEvent(new RoomEvent.StateChanged(room.code(), state));
		} catch (RuntimeException e) {
			log.error("Deadline of room {} round {} phase {} failed", room.code(), roundNumber, phase, e);
		}
	}

	private static Game requireGame(Room room) {
		if (room.status() != RoomStatus.IN_GAME) {
			throw new InvalidStateException("No game in progress, room is " + room.status());
		}
		return room.game().orElseThrow();
	}
}
