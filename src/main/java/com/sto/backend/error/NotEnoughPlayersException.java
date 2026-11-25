package com.sto.backend.error;

public class NotEnoughPlayersException extends ApiException {

	public NotEnoughPlayersException() {
		this("At least 2 players are required");
	}

	public NotEnoughPlayersException(String message) {
		super(ErrorCode.NOT_ENOUGH_PLAYERS, message);
	}
}
