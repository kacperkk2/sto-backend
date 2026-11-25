package com.sto.backend.room;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.sto.backend.game.Game;
import com.sto.backend.game.GameSettings;

/**
 * Mutable room state. All access goes through the room's monitor: single methods are
 * {@code synchronized}, and multi-step operations (check-then-act) should wrap them in
 * {@code synchronized (room) { ... }}.
 */
public class Room {

	private final String code;
	private final String hostPlayerId;
	private final List<Player> players = new ArrayList<>();
	private RoomStatus status = RoomStatus.LOBBY;
	private Game game;
	/** Settings shown in the lobby and used by the next game; the host changes them before starting. */
	private GameSettings settings;
	private Instant lastActivityAt;
	/** Bumped on every state change, so clients can drop out-of-order SSE events. */
	private long version;
	/** Keys ("{roundNumber}:{phase}[:blitz]") of the deadlines already scheduled in this game, to avoid doubles. */
	private final Set<String> scheduledDeadlines = new HashSet<>();

	public Room(String code, Player host, GameSettings settings) {
		this.code = Objects.requireNonNull(code);
		this.settings = Objects.requireNonNull(settings);
		this.hostPlayerId = host.id();
		this.players.add(host);
		this.lastActivityAt = host.joinedAt();
	}

	public String code() {
		return code;
	}

	public String hostPlayerId() {
		return hostPlayerId;
	}

	public synchronized RoomStatus status() {
		return status;
	}

	public synchronized List<Player> players() {
		return List.copyOf(players);
	}

	public synchronized Optional<Game> game() {
		return Optional.ofNullable(game);
	}

	public synchronized GameSettings settings() {
		return settings;
	}

	public synchronized void updateSettings(GameSettings settings) {
		this.settings = Objects.requireNonNull(settings);
	}

	public synchronized Instant lastActivityAt() {
		return lastActivityAt;
	}

	public synchronized long version() {
		return version;
	}

	public boolean isHost(String playerId) {
		return hostPlayerId.equals(playerId);
	}

	public synchronized Optional<Player> findPlayer(String playerId) {
		return players.stream().filter(p -> p.id().equals(playerId)).findFirst();
	}

	public synchronized Optional<Player> findPlayerByToken(String token) {
		if (token == null || token.isEmpty()) {
			return Optional.empty();
		}
		byte[] given = token.getBytes(StandardCharsets.UTF_8);
		return players.stream()
				.filter(p -> MessageDigest.isEqual(p.token().getBytes(StandardCharsets.UTF_8), given))
				.findFirst();
	}

	public synchronized boolean isNickTaken(String nick) {
		String normalized = normalizeNick(nick);
		return players.stream().anyMatch(p -> normalizeNick(p.nick()).equals(normalized));
	}

	public synchronized void addPlayer(Player player) {
		players.add(player);
	}

	public synchronized void startGame(Game game) {
		this.game = game;
		this.status = RoomStatus.IN_GAME;
		this.scheduledDeadlines.clear();
	}

	public synchronized void finishGame() {
		this.status = RoomStatus.FINISHED;
	}

	/** Back to the lobby after a finished game: the old game (scores, ranking) is dropped, settings stay. */
	public synchronized void returnToLobby() {
		this.game = null;
		this.status = RoomStatus.LOBBY;
		this.scheduledDeadlines.clear();
	}

	public synchronized void close() {
		this.status = RoomStatus.CLOSED;
	}

	public synchronized void touch(Instant now) {
		this.lastActivityAt = now;
	}

	public synchronized void bumpVersion() {
		version++;
	}

	/** Marks the deadline as scheduled; returns {@code false} when it already was. */
	public synchronized boolean markDeadlineScheduled(String key) {
		return scheduledDeadlines.add(key);
	}

	private static String normalizeNick(String nick) {
		return nick.trim().toLowerCase(Locale.ROOT);
	}
}
