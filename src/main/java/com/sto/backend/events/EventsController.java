package com.sto.backend.events;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.sto.backend.auth.AuthenticatedPlayer;
import com.sto.backend.auth.CurrentPlayer;

import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/rooms")
public class EventsController {

	private final RoomEvents roomEvents;

	public EventsController(RoomEvents roomEvents) {
		this.roomEvents = roomEvents;
	}

	/** Token comes from {@code ?token=} because EventSource cannot send headers. */
	@GetMapping(path = "/{code}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter events(@CurrentPlayer AuthenticatedPlayer me, HttpServletResponse response) {
		response.setHeader("Cache-Control", "no-cache");
		response.setHeader("X-Accel-Buffering", "no");
		return roomEvents.subscribe(me.room());
	}
}
