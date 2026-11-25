package com.sto.backend.error;

public class GameInProgressException extends ApiException {

	public GameInProgressException() {
		this("Game already started, joining is possible only in the lobby");
	}

	public GameInProgressException(String message) {
		super(ErrorCode.GAME_IN_PROGRESS, message);
	}
}
