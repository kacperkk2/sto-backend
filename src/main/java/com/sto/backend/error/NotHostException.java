package com.sto.backend.error;

public class NotHostException extends ApiException {

	public NotHostException() {
		this("Only the host can do this");
	}

	public NotHostException(String message) {
		super(ErrorCode.NOT_HOST, message);
	}
}
