package com.sto.backend.error;

public class NickTakenException extends ApiException {

	public NickTakenException() {
		this("Nick is already taken in this room");
	}

	public NickTakenException(String message) {
		super(ErrorCode.NICK_TAKEN, message);
	}
}
