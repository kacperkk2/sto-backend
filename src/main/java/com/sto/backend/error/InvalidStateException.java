package com.sto.backend.error;

public class InvalidStateException extends ApiException {

	public InvalidStateException() {
		this("Operation not allowed in the current room state");
	}

	public InvalidStateException(String message) {
		super(ErrorCode.INVALID_STATE, message);
	}
}
