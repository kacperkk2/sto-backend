package com.sto.backend.room;

import com.sto.backend.dto.GameStateDto;

/** Published after a room changes; {@link com.sto.backend.events.RoomEvents} turns it into SSE. */
public sealed interface RoomEvent {

	String code();

	/** Public state after the change (built under the room lock). */
	record StateChanged(String code, GameStateDto state) implements RoomEvent {
	}

	record RoomClosed(String code) implements RoomEvent {
	}
}
