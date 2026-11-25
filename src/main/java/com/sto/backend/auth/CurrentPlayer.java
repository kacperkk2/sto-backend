package com.sto.backend.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the {@link AuthenticatedPlayer} for the room {@code {code}} in the path, authenticated by the
 * {@code X-Player-Token} header (or the {@code token} query param on {@code /events}).
 * Throws 404 when the room does not exist and 401 when the token is missing or belongs elsewhere.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentPlayer {
}
