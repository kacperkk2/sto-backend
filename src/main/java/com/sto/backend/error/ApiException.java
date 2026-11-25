package com.sto.backend.error;

/** Base class for domain errors mapped to {@link ErrorDto} by {@link ApiExceptionHandler}. */
public abstract class ApiException extends RuntimeException {

	private final ErrorCode code;

	protected ApiException(ErrorCode code, String message) {
		super(message);
		this.code = code;
	}

	public ErrorCode code() {
		return code;
	}
}
