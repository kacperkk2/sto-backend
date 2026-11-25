package com.sto.backend.auth;

import com.sto.backend.error.NotHostException;
import com.sto.backend.room.Player;
import com.sto.backend.room.Room;

public record AuthenticatedPlayer(Room room, Player player) {

	public boolean isHost() {
		return room.isHost(player.id());
	}

	/** @throws NotHostException (403) when the player is not the room's host */
	public AuthenticatedPlayer requireHost() {
		if (!isHost()) {
			throw new NotHostException();
		}
		return this;
	}
}
