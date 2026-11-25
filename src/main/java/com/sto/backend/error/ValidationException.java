package com.sto.backend.error;

public class ValidationException extends ApiException {

	public ValidationException() {
		this("Invalid request");
	}

	public ValidationException(String message) {
		super(ErrorCode.VALIDATION, message);
	}
}
