package com.sto.backend.config;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.sto.backend.auth.CurrentPlayerArgumentResolver;

@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class WebConfig implements WebMvcConfigurer {

	public static final String API_PREFIX = "/api";
	public static final String PLAYER_TOKEN_HEADER = "X-Player-Token";

	private final CorsProperties corsProperties;
	private final CurrentPlayerArgumentResolver currentPlayerResolver;

	public WebConfig(CorsProperties corsProperties, CurrentPlayerArgumentResolver currentPlayerResolver) {
		this.corsProperties = corsProperties;
		this.currentPlayerResolver = currentPlayerResolver;
	}

	@Override
	public void configurePathMatch(PathMatchConfigurer configurer) {
		configurer.addPathPrefix(API_PREFIX, type -> type.isAnnotationPresent(RestController.class));
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping(API_PREFIX + "/**")
				.allowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new))
				.allowedMethods("GET", "POST", "PUT", "DELETE")
				.allowedHeaders(PLAYER_TOKEN_HEADER, "Content-Type")
				.maxAge(3600);
	}

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(currentPlayerResolver);
	}
}
