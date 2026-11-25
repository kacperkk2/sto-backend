package com.sto.backend.error;

import com.sto.backend.room.RoomRepository;

public class RoomLimitException extends ApiException {

	public RoomLimitException() {
		super(ErrorCode.ROOM_LIMIT, "All " + RoomRepository.MAX_ROOMS + " rooms are taken");
	}
}
