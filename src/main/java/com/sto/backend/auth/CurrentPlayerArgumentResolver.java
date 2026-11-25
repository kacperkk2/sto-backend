package com.sto.backend.auth;

import java.time.Clock;
import java.util.Map;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerMapping;

import com.sto.backend.config.WebConfig;
import com.sto.backend.error.RoomNotFoundException;
import com.sto.backend.error.UnauthorizedException;
import com.sto.backend.room.Player;
import com.sto.backend.room.Room;
import com.sto.backend.room.RoomRepository;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class CurrentPlayerArgumentResolver implements HandlerMethodArgumentResolver {

	static final String CODE_PATH_VARIABLE = "code";
	static final String TOKEN_QUERY_PARAM = "token";
	static final String EVENTS_PATH_SUFFIX = "/events";

	private final RoomRepository repository;
	private final Clock clock;

	public CurrentPlayerArgumentResolver(RoomRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.hasParameterAnnotation(CurrentPlayer.class)
				&& AuthenticatedPlayer.class.equals(parameter.getParameterType());
	}

	@Override
	public AuthenticatedPlayer resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
		HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
		Room room = repository.find(roomCode(webRequest)).orElseThrow(RoomNotFoundException::new);
		Player player = room.findPlayerByToken(token(request)).orElseThrow(UnauthorizedException::new);
		room.touch(clock.instant());
		return new AuthenticatedPlayer(room, player);
	}

	private static String roomCode(NativeWebRequest webRequest) {
		@SuppressWarnings("unchecked")
		Map<String, String> vars = (Map<String, String>) webRequest.getAttribute(
				HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
		String code = vars == null ? null : vars.get(CODE_PATH_VARIABLE);
		if (code == null) {
			throw new IllegalStateException("@CurrentPlayer requires a {code} path variable");
		}
		return code;
	}

	/** EventSource cannot send headers, so only the SSE endpoint accepts the token as a query param. */
	private static String token(HttpServletRequest request) {
		if (request.getRequestURI().endsWith(EVENTS_PATH_SUFFIX)) {
			return request.getParameter(TOKEN_QUERY_PARAM);
		}
		return request.getHeader(WebConfig.PLAYER_TOKEN_HEADER);
	}
}
