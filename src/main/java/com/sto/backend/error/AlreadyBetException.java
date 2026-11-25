package com.sto.backend.error;

public class AlreadyBetException extends ApiException {

	public AlreadyBetException() {
		this("Bet already placed in this round");
	}

	public AlreadyBetException(String message) {
		super(ErrorCode.ALREADY_BET, message);
	}
}
