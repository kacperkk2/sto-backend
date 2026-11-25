package com.sto.backend.room;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import org.springframework.stereotype.Repository;

import com.sto.backend.error.RoomLimitException;

/** In-memory rooms. Rooms are numbered 1–{@value #MAX_ROOMS}; a freed number is reused. */
@Repository
public class RoomRepository {

	public static final int MAX_ROOMS = 5;

	private final ConcurrentMap<String, Room> rooms = new ConcurrentHashMap<>();

	/**
	 * Creates a room under the lowest free number. {@code factory} receives the code and builds the room.
	 *
	 * @throws RoomLimitException when all {@value #MAX_ROOMS} numbers are taken
	 */
	public Room create(Function<String, Room> factory) {
		for (int number = 1; number <= MAX_ROOMS; number++) {
			String code = String.valueOf(number);
			if (rooms.containsKey(code)) {
				continue;
			}
			Room room = factory.apply(code);
			if (rooms.putIfAbsent(code, room) == null) {
				return room;
			}
		}
		throw new RoomLimitException();
	}

	public Optional<Room> find(String code) {
		return code == null ? Optional.empty() : Optional.ofNullable(rooms.get(normalizeCode(code)));
	}

	public Optional<Room> remove(String code) {
		return code == null ? Optional.empty() : Optional.ofNullable(rooms.remove(normalizeCode(code)));
	}

	/** All rooms, ordered by number. */
	public Collection<Room> all() {
		return rooms.values().stream()
				.sorted(Comparator.comparingInt(r -> Integer.parseInt(r.code())))
				.toList();
	}

	public static String normalizeCode(String code) {
		return code.trim();
	}
}
