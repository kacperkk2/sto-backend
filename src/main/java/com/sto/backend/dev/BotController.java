package com.sto.backend.dev;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sto.backend.auth.AuthenticatedPlayer;
import com.sto.backend.auth.CurrentPlayer;
import com.sto.backend.dto.GameStateDto;
import com.sto.backend.room.RoomService;

/** Dev only (app.dev.bots-enabled): fake players for testing many players on screen. */
@RestController
@RequestMapping("/rooms/{code}")
@ConditionalOnBooleanProperty("app.dev.bots-enabled")
public class BotController {

	public record AddBotsRequest(Integer count) {
	}

	private final RoomService roomService;

	public BotController(RoomService roomService) {
		this.roomService = roomService;
	}

	@PostMapping("/bots")
	public GameStateDto addBots(@CurrentPlayer AuthenticatedPlayer me,
			@RequestBody(required = false) AddBotsRequest request) {
		int count = request == null || request.count() == null ? 1 : request.count();
		return roomService.addBots(me, count);
	}
}
