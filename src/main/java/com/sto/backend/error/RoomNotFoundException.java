package com.sto.backend.error;

public class RoomNotFoundException extends ApiException {

	public RoomNotFoundException() {
		this("Room not found");
	}

	public RoomNotFoundException(String message) {
		super(ErrorCode.ROOM_NOT_FOUND, message);
	}
}
