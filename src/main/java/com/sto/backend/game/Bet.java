package com.sto.backend.game;

import com.sto.backend.error.ValidationException;

/** Split of 100 points between answer A ({@code onA}) and B ({@code onB}). */
public record Bet(int onA, int onB) {

	public static final int TOTAL = 100;
	public static final int STEP = 5;

	public static final Bet HALF = new Bet(50, 50);
	public static final Bet ALL_A = new Bet(100, 0);
	public static final Bet ALL_B = new Bet(0, 100);

	/** Validates a bet coming from a client. */
	public static Bet of(Integer onA, Integer onB) {
		if (onA == null || onB == null) {
			throw new ValidationException("onA, onB: must not be null");
		}
		if (onA < 0 || onB < 0 || onA + onB != TOTAL) {
			throw new ValidationException("onA + onB: must be non-negative and sum up to " + TOTAL);
		}
		if (onA % STEP != 0) {
			throw new ValidationException("onA, onB: must be multiples of " + STEP);
		}
		return new Bet(onA, onB);
	}

	/** Points put on answer {@code 0} (A) or {@code 1} (B). */
	public int on(int answer) {
		return answer == 0 ? onA : onB;
	}

	public boolean isAllIn() {
		return onA == TOTAL || onB == TOTAL;
	}
}
