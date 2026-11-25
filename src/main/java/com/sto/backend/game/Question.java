package com.sto.backend.game;

import java.util.List;

/** A question with exactly two answers; {@code correct} is the index (0 = A, 1 = B). */
public record Question(String id, String category, String text, List<String> answers, int correct) {

	public Question {
		answers = List.copyOf(answers);
		if (answers.size() != 2) {
			throw new IllegalArgumentException("Question " + id + " must have exactly 2 answers");
		}
		if (correct != 0 && correct != 1) {
			throw new IllegalArgumentException("Question " + id + ": correct must be 0 or 1");
		}
	}
}
