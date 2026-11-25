package com.sto.backend.error;

import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.sto.backend.config.WebConfig;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ErrorDto> handleApi(ApiException ex) {
		return error(ex.code(), ex.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ErrorDto> handleBodyValidation(MethodArgumentNotValidException ex) {
		String message = ex.getBindingResult().getFieldErrors().stream()
				.map(e -> e.getField() + ": " + e.getDefaultMessage())
				.collect(Collectors.joining("; "));
		return error(ErrorCode.VALIDATION, message.isEmpty() ? "Invalid request" : message);
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	ResponseEntity<ErrorDto> handleMethodValidation(HandlerMethodValidationException ex) {
		String message = ex.getAllErrors().stream()
				.map(e -> e.getDefaultMessage())
				.collect(Collectors.joining("; "));
		return error(ErrorCode.VALIDATION, message.isEmpty() ? "Invalid request" : message);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ErrorDto> handleUnreadable(HttpMessageNotReadableException ex) {
		return error(ErrorCode.VALIDATION, "Malformed request body");
	}

	@ExceptionHandler(MissingRequestHeaderException.class)
	ResponseEntity<ErrorDto> handleMissingHeader(MissingRequestHeaderException ex) {
		if (WebConfig.PLAYER_TOKEN_HEADER.equalsIgnoreCase(ex.getHeaderName())) {
			return error(ErrorCode.UNAUTHORIZED, "Missing player token");
		}
		return error(ErrorCode.VALIDATION, "Missing header " + ex.getHeaderName());
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	ResponseEntity<ErrorDto> handleMissingParam(MissingServletRequestParameterException ex) {
		if ("token".equals(ex.getParameterName())) {
			return error(ErrorCode.UNAUTHORIZED, "Missing player token");
		}
		return error(ErrorCode.VALIDATION, "Missing parameter " + ex.getParameterName());
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<ErrorDto> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
		return error(ErrorCode.VALIDATION, ex.getName() + ": invalid value");
	}

	private static ResponseEntity<ErrorDto> error(ErrorCode code, String message) {
		return ResponseEntity.status(code.status()).body(new ErrorDto(code, message));
	}
}
