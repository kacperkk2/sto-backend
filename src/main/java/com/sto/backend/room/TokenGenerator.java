package com.sto.backend.room;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class TokenGenerator {

	private static final int TOKEN_BYTES = 32;

	private final SecureRandom random = new SecureRandom();

	public String newToken() {
		byte[] bytes = new byte[TOKEN_BYTES];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public String newPlayerId() {
		return UUID.randomUUID().toString();
	}
}
