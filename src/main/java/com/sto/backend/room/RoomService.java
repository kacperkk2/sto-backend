package com.sto.backend.room;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.sto.backend.auth.AuthenticatedPlayer;
import com.sto.backend.dto.GameStateDto;
import com.sto.backend.dto.RoomSummaryDto;
import com.sto.backend.error.GameInProgressException;
import com.sto.backend.error.NickTakenException;
import com.sto.backend.error.RoomClosedException;
import com.sto.backend.error.RoomNotFoundException;
import com.sto.backend.error.ValidationException;
import com.sto.backend.game.GameSettings;
import com.sto.backend.game.QuestionBank;

@Service
public class RoomService {

	static final int MAX_NICK_LENGTH = 20;
	static final int MAX_BOTS_PER_REQUEST = 50;

	private final RoomRepository repository;
	private final TokenGenerator tokenGenerator;
	private final ApplicationEventPublisher events;
	private final Clock clock;
	private final QuestionBank questions;

	public RoomService(RoomRepository repository, TokenGenerator tokenGenerator,
			ApplicationEventPublisher events, Clock clock, QuestionBank questions) {
		this.repository = repository;
		this.tokenGenerator = tokenGenerator;
		this.events = events;
		this.clock = clock;
		this.questions = questions;
	}

	public Room createRoom(String rawNick) {
		Player host = newPlayer(validateNick(rawNick));
		GameSettings settings = GameSettings.defaults(questions.defaultScenarioId());
		return repository.create(code -> new Room(code, host, settings));
	}

	public List<RoomSummaryDto> listRooms() {
		return repository.all().stream()
				.map(RoomSummaryDto::of)
				.filter(r -> r.status() != RoomStatus.CLOSED)
				.toList();
	}

	/** Joining is possible in the lobby and after a finished game (before the next one starts). */
	public Player joinRoom(String code, String rawNick) {
		String nick = validateNick(rawNick);
		Room room = repository.find(code).orElseThrow(RoomNotFoundException::new);
		Player player;
		GameStateDto state;
		synchronized (room) {
			switch (room.status()) {
				case CLOSED -> throw new RoomClosedException();
				case IN_GAME -> throw new GameInProgressException();
				case LOBBY, FINISHED -> {
					// ok
				}
			}
			if (room.isNickTaken(nick)) {
				throw new NickTakenException();
			}
			player = newPlayer(nick);
			room.addPlayer(player);
			room.touch(player.joinedAt());
			room.bumpVersion();
			state = GameStateDto.of(room, clock.instant());
		}
		events.publishEvent(new RoomEvent.StateChanged(room.code(), state));
		return player;
	}

	/**
	 * Dev only: adds {@code count} bots ("Bot 1", "Bot 2", …, skipping taken nicks) that always bet 50/50.
	 * Allowed in the same states as joining.
	 */
	public GameStateDto addBots(AuthenticatedPlayer me, int count) {
		me.requireHost();
		if (count < 1 || count > MAX_BOTS_PER_REQUEST) {
			throw new ValidationException("count: must be 1-" + MAX_BOTS_PER_REQUEST);
		}
		Room room = me.room();
		GameStateDto state;
		synchronized (room) {
			switch (room.status()) {
				case CLOSED -> throw new RoomClosedException();
				case IN_GAME -> throw new GameInProgressException();
				case LOBBY, FINISHED -> {
					// ok
				}
			}
			Instant now = clock.instant();
			int number = 1;
			for (int added = 0; added < count; added++) {
				while (room.isNickTaken("Bot " + number)) {
					number++;
				}
				room.addPlayer(new Player(tokenGenerator.newPlayerId(), "Bot " + number, tokenGenerator.newToken(),
						now, true));
			}
			room.touch(now);
			room.bumpVersion();
			state = GameStateDto.of(room, now);
		}
		events.publishEvent(new RoomEvent.StateChanged(room.code(), state));
		return state;
	}

	public void closeRoom(AuthenticatedPlayer me) {
		me.requireHost();
		closeRoom(me.room());
	}

	/** Marks the room CLOSED, removes it and publishes {@link RoomEvent.RoomClosed}. */
	public void closeRoom(Room room) {
		closeRoomIf(room, null);
	}

	/**
	 * Closes the room only if its last activity is before {@code cutoff}. The check and the close happen
	 * under the room lock, so a request that touches the room concurrently keeps it alive.
	 *
	 * @return whether the room was closed
	 */
	public boolean closeIfInactiveSince(Room room, Instant cutoff) {
		return closeRoomIf(room, cutoff);
	}

	private boolean closeRoomIf(Room room, Instant inactiveBefore) {
		synchronized (room) {
			if (room.status() == RoomStatus.CLOSED) {
				return false;
			}
			if (inactiveBefore != null && !room.lastActivityAt().isBefore(inactiveBefore)) {
				return false;
			}
			room.close();
			repository.remove(room.code());
		}
		events.publishEvent(new RoomEvent.RoomClosed(room.code()));
		return true;
	}

	private Player newPlayer(String nick) {
		Instant now = clock.instant();
		return new Player(tokenGenerator.newPlayerId(), nick, tokenGenerator.newToken(), now);
	}

	private static String validateNick(String rawNick) {
		String nick = rawNick == null ? "" : rawNick.strip();
		if (nick.isEmpty() || nick.length() > MAX_NICK_LENGTH) {
			throw new ValidationException("nick: must be 1-" + MAX_NICK_LENGTH + " characters after trimming");
		}
		return nick;
	}
}
