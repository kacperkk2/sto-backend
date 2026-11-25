package com.sto.backend.game;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public info about the question scenarios. */
@RestController
@RequestMapping("/questions")
public class QuestionController {

	public record ScenarioDto(String id, String name, int count, boolean random) {
	}

	private final QuestionBank questions;

	public QuestionController(QuestionBank questions) {
		this.questions = questions;
	}

	/** Scenarios by id; the first one is the default. */
	@GetMapping("/scenarios")
	public List<ScenarioDto> scenarios() {
		return questions.scenarios().stream()
				.map(s -> new ScenarioDto(s.id(), s.name(), s.questions().size(), s.random()))
				.toList();
	}
}
