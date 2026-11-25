package com.sto.backend.dto;

import java.util.List;

import com.sto.backend.game.Card;
import com.sto.backend.game.RoundEvent;

/**
 * Missing fields get defaults, see {@link com.sto.backend.game.GameSettings}; a missing scenario is the default one.
 *
 * @param excludedQuestionIds only for starting a game: questions of the game's scenario the host already played in
 *                            earlier games, not drawn unless every question was used; not part of the settings
 */
public record GameSettingsRequest(Integer rounds, Integer eventChance, Integer betSeconds, Boolean lastRoundDouble,
		List<RoundEvent> events, List<Card> cards, Integer cardSeconds, String scenario,
		List<String> excludedQuestionIds) {

	/** Whether any settings field is present (a body with only {@code excludedQuestionIds} keeps the settings). */
	public boolean hasSettings() {
		return rounds != null || eventChance != null || betSeconds != null || lastRoundDouble != null || events != null
				|| cards != null || cardSeconds != null || scenario != null;
	}
}
