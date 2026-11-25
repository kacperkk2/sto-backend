package com.sto.backend.dto;

import java.util.List;

import com.sto.backend.room.Player;
import com.sto.backend.room.Room;
import com.sto.backend.room.RoomStatus;

/**
 * A room on the public room list (no secrets, no player ids).
 *
 * @param playerNicks nicks in join order (host first)
 * @param joinable whether new players can join now (lobby or finished game)
 */
public record RoomSummaryDto(String code, RoomStatus status, String hostNick, int playerCount,
		List<String> playerNicks, boolean joinable) {

	public static RoomSummaryDto of(Room room) {
		synchronized (room) {
			var players = room.players();
			String hostNick = room.findPlayer(room.hostPlayerId()).map(Player::nick).orElse("?");
			RoomStatus status = room.status();
			boolean joinable = status == RoomStatus.LOBBY || status == RoomStatus.FINISHED;
			return new RoomSummaryDto(room.code(), status, hostNick, players.size(),
					players.stream().map(Player::nick).toList(), joinable);
		}
	}
}
