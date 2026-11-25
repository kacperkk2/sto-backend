package com.sto.backend.game;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import com.sto.backend.error.ValidationException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Question scenarios: every {@code questions/*.json} file on the classpath is one scenario, its id is the file
 * name without {@code .json}. A file is either a plain array of questions (the name is the id, random order) or
 * an object {@code {"name": "...", "random": true, "questions": [...]}} ({@code name} and {@code random} optional,
 * {@code random} defaults to true). Scenarios are ordered by id; the first one is the default.
 */
@Component
public class QuestionBank {

	static final String LOCATION = "classpath*:questions/*.json";

	private final Map<String, Scenario> scenarios;

	public QuestionBank() {
		this(load());
	}

	QuestionBank(List<Scenario> scenarios) {
		if (scenarios.isEmpty()) {
			throw new IllegalStateException("No question scenarios in " + LOCATION);
		}
		Map<String, Scenario> byId = new LinkedHashMap<>();
		scenarios.stream().sorted(Comparator.comparing(Scenario::id)).forEach(s -> {
			if (byId.put(s.id(), s) != null) {
				throw new IllegalStateException("Duplicate scenario id: " + s.id());
			}
		});
		this.scenarios = byId;
	}

	public List<Scenario> scenarios() {
		return List.copyOf(scenarios.values());
	}

	public String defaultScenarioId() {
		return scenarios.keySet().iterator().next();
	}

	/** The given scenario id, or the default one for {@code null}; an unknown id is rejected. */
	public String resolveScenarioId(String id) {
		if (id == null) {
			return defaultScenarioId();
		}
		if (!scenarios.containsKey(id)) {
			throw new ValidationException("scenario: unknown scenario " + id);
		}
		return id;
	}

	/**
	 * Draws a question of the scenario not in {@code used} and adds its id there: a random one, or in a
	 * non-random scenario the first one in file order. When every question of the scenario was used, {@code used}
	 * is cleared and questions start repeating.
	 */
	public Question draw(String scenarioId, Set<String> used, RandomGenerator random) {
		Scenario scenario = scenarios.get(scenarioId);
		if (scenario == null) {
			throw new IllegalArgumentException("Unknown scenario: " + scenarioId);
		}
		List<Question> fresh = scenario.questions().stream().filter(q -> !used.contains(q.id())).toList();
		if (fresh.isEmpty()) {
			used.clear();
			fresh = scenario.questions();
		}
		Question question = scenario.random() ? fresh.get(random.nextInt(fresh.size())) : fresh.getFirst();
		used.add(question.id());
		return question;
	}

	/**
	 * A set of questions; question ids are unique within it.
	 *
	 * @param random {@code false}: questions go in file order instead of a random one
	 */
	public record Scenario(String id, String name, boolean random, List<Question> questions) {

		public Scenario {
			questions = List.copyOf(questions);
			if (questions.isEmpty()) {
				throw new IllegalStateException("Scenario " + id + " has no questions");
			}
			Set<String> ids = new HashSet<>();
			for (Question q : questions) {
				if (!ids.add(q.id())) {
					throw new IllegalStateException("Scenario " + id + ": duplicate question id " + q.id());
				}
			}
		}
	}

	private static List<Scenario> load() {
		JsonMapper mapper = JsonMapper.builder().build();
		List<Scenario> scenarios = new ArrayList<>();
		try {
			for (Resource resource : new PathMatchingResourcePatternResolver().getResources(LOCATION)) {
				String file = resource.getFilename();
				if (file == null) {
					continue;
				}
				String id = file.substring(0, file.length() - ".json".length());
				try (InputStream in = resource.getInputStream()) {
					scenarios.add(parse(mapper, id, mapper.readTree(in)));
				} catch (RuntimeException e) {
					throw new IllegalStateException("Could not load scenario " + file, e);
				}
			}
		} catch (IOException e) {
			throw new IllegalStateException("Could not load " + LOCATION, e);
		}
		return scenarios;
	}

	private static Scenario parse(JsonMapper mapper, String id, JsonNode root) {
		JsonNode questions = root.isArray() ? root : root.path("questions");
		if (!questions.isArray()) {
			throw new IllegalStateException("Scenario " + id + ": expected an array or an object with \"questions\"");
		}
		JsonNode nameNode = root.path("name");
		String name = nameNode.isString() && !nameNode.asString().isBlank() ? nameNode.asString().trim() : id;
		JsonNode randomNode = root.path("random");
		if (!randomNode.isMissingNode() && !randomNode.isBoolean()) {
			throw new IllegalStateException("Scenario " + id + ": \"random\" must be true or false");
		}
		boolean random = !randomNode.isBoolean() || randomNode.asBoolean();
		List<Question> list = new ArrayList<>();
		for (JsonNode node : questions) {
			list.add(mapper.treeToValue(node, Question.class));
		}
		return new Scenario(id, name, random, list);
	}
}
