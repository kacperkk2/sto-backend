package com.sto.backend.room;

import java.time.Clock;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sto.backend.auth.AuthenticatedPlayer;
import com.sto.backend.auth.CurrentPlayer;
import com.sto.backend.dto.CreateRoomResponse;
import com.sto.backend.dto.GameStateDto;
import com.sto.backend.dto.JoinRoomResponse;
import com.sto.backend.dto.MyStateDto;
import com.sto.backend.dto.NickRequest;
import com.sto.backend.dto.RoomSummaryDto;

@RestController
@RequestMapping("/rooms")
public class RoomController {

	private final RoomService roomService;
	private final Clock clock;

	public RoomController(RoomService roomService, Clock clock) {
		this.roomService = roomService;
		this.clock = clock;
	}

	/** Public list of rooms (no token needed), ordered by number. */
	@GetMapping
	public List<RoomSummaryDto> list() {
		return roomService.listRooms();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CreateRoomResponse create(@RequestBody(required = false) NickRequest request) {
		Room room = roomService.createRoom(request == null ? null : request.nick());
		Player host = room.players().getFirst();
		return new CreateRoomResponse(room.code(), host.id(), host.token());
	}

	@PostMapping("/{code}/players")
	@ResponseStatus(HttpStatus.CREATED)
	public JoinRoomResponse join(@PathVariable String code, @RequestBody(required = false) NickRequest request) {
		Player player = roomService.joinRoom(code, request == null ? null : request.nick());
		return new JoinRoomResponse(player.id(), player.token());
	}

	@GetMapping("/{code}")
	public GameStateDto state(@CurrentPlayer AuthenticatedPlayer me) {
		return GameStateDto.of(me.room(), clock.instant());
	}

	@GetMapping("/{code}/me")
	public MyStateDto me(@CurrentPlayer AuthenticatedPlayer me) {
		return MyStateDto.of(me.room(), me.player());
	}

	@DeleteMapping("/{code}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void close(@CurrentPlayer AuthenticatedPlayer me) {
		roomService.closeRoom(me);
	}
}
