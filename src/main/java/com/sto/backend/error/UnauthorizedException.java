package com.sto.backend.error;

public class UnauthorizedException extends ApiException {

	public UnauthorizedException() {
		this("Missing or invalid player token");
	}

	public UnauthorizedException(String message) {
		super(ErrorCode.UNAUTHORIZED, message);
	}
}
