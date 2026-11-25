package com.sto.backend.room;

import java.time.Instant;
import java.util.Objects;

/** {@code bot}: a dev-only fake player the server plays for (see {@code GameEngine#playBots}). */
public record Player(String id, String nick, String token, Instant joinedAt, boolean bot) {

	public Player(String id, String nick, String token, Instant joinedAt) {
		this(id, nick, token, joinedAt, false);
	}

	public Player {
		Objects.requireNonNull(id);
		Objects.requireNonNull(nick);
		Objects.requireNonNull(token);
		Objects.requireNonNull(joinedAt);
	}

	@Override
	public String toString() {
		return "Player[id=" + id + ", nick=" + nick + ", joinedAt=" + joinedAt + ", bot=" + bot + "]";
	}
}
