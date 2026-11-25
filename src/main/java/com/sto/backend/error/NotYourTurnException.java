package com.sto.backend.error;

public class NotYourTurnException extends ApiException {

	public NotYourTurnException() {
		this("This action is not available to you now");
	}

	public NotYourTurnException(String message) {
		super(ErrorCode.NOT_YOUR_TURN, message);
	}
}
