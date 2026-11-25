package com.sto.backend.events;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.sto.backend.dto.GameStateDto;
import com.sto.backend.room.Room;
import com.sto.backend.room.RoomEvent;
import com.sto.backend.room.RoomStatus;

/** Per-room registry of SSE emitters; translates {@link RoomEvent}s into SSE events. */
@Component
public class RoomEvents {

	public static final String STATE = "state";
	public static final String ROOM_CLOSED = "room-closed";

	private static final Logger log = LoggerFactory.getLogger(RoomEvents.class);

	private final ConcurrentMap<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
	private final Duration timeout;
	private final Clock clock;

	public RoomEvents(@Value("${app.sse.timeout:30m}") Duration timeout, Clock clock) {
		this.timeout = timeout;
		this.clock = clock;
	}

	/** Registers a new subscriber and immediately sends it the current {@code state}. */
	public SseEmitter subscribe(Room room) {
		String code = room.code();
		SseEmitter emitter = new SseEmitter(timeout.toMillis());
		emitter.onCompletion(() -> remove(code, emitter));
		emitter.onTimeout(() -> {
			remove(code, emitter);
			emitter.complete();
		});
		emitter.onError(e -> remove(code, emitter));
		emitters.computeIfAbsent(code, c -> new CopyOnWriteArrayList<>()).add(emitter);

		if (room.status() == RoomStatus.CLOSED) {
			// Room closed between authentication and registration.
			send(code, emitter, SseEmitter.event().name(ROOM_CLOSED).data(Map.of()));
			remove(code, emitter);
			emitter.complete();
		} else {
			send(code, emitter, SseEmitter.event().name(STATE).data(GameStateDto.of(room, clock.instant())));
		}
		return emitter;
	}

	public void broadcast(String code, String name, Object data) {
		for (SseEmitter emitter : emitters.getOrDefault(code, List.of())) {
			send(code, emitter, SseEmitter.event().name(name).data(data));
		}
	}

	/** Sends {@code room-closed} to every subscriber of the room and ends their streams. */
	public void closeRoom(String code) {
		List<SseEmitter> subscribers = emitters.remove(code);
		if (subscribers == null) {
			return;
		}
		for (SseEmitter emitter : subscribers) {
			try {
				emitter.send(SseEmitter.event().name(ROOM_CLOSED).data(Map.of()));
				emitter.complete();
			} catch (IOException | IllegalStateException e) {
				log.debug("Could not notify subscriber of room {} about closing", code, e);
			}
		}
	}

	@EventListener
	public void on(RoomEvent event) {
		switch (event) {
			case RoomEvent.StateChanged e -> broadcast(e.code(), STATE, e.state());
			case RoomEvent.RoomClosed e -> closeRoom(e.code());
		}
	}

	@Scheduled(fixedRateString = "${app.sse.heartbeat:25s}")
	public void heartbeat() {
		emitters.forEach((code, subscribers) -> {
			for (SseEmitter emitter : subscribers) {
				send(code, emitter, SseEmitter.event().comment("ping"));
			}
		});
	}

	private void send(String code, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
		try {
			emitter.send(event);
		} catch (IOException | IllegalStateException e) {
			// Client went away; drop it. completeWithError lets the container release the request.
			remove(code, emitter);
			emitter.completeWithError(e);
		}
	}

	private void remove(String code, SseEmitter emitter) {
		emitters.computeIfPresent(code, (c, list) -> {
			list.remove(emitter);
			return list.isEmpty() ? null : list;
		});
	}
}
