package com.sto.backend.room;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically removes rooms with no activity for {@code app.room.ttl}. Subscribers get {@code room-closed}
 * through the regular {@link RoomEvent.RoomClosed} path. SSE heartbeats do not count as activity.
 */
@Component
public class RoomExpiry {

	private static final Logger log = LoggerFactory.getLogger(RoomExpiry.class);

	private final RoomRepository repository;
	private final RoomService roomService;
	private final Clock clock;
	private final Duration ttl;

	public RoomExpiry(RoomRepository repository, RoomService roomService, Clock clock,
			@Value("${app.room.ttl:1h}") Duration ttl) {
		this.repository = repository;
		this.roomService = roomService;
		this.clock = clock;
		this.ttl = ttl;
	}

	@Scheduled(fixedDelayString = "${app.room.cleanup-interval:5m}")
	public void removeInactiveRooms() {
		Instant cutoff = clock.instant().minus(ttl);
		int removed = 0;
		for (Room room : repository.all()) {
			if (roomService.closeIfInactiveSince(room, cutoff)) {
				removed++;
			}
		}
		if (removed > 0) {
			log.info("Removed {} inactive room(s), {} left", removed, repository.all().size());
		}
	}
}
