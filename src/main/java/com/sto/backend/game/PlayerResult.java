package com.sto.backend.game;

/**
 * Outcome of a round for one player.
 *
 * @param bet          final bet (for a copier: the copied player's bet)
 * @param auto         the bet was assigned because the player did not bet in time
 * @param copiedFromId id of the player whose bet was copied (Blind copy), otherwise {@code null}
 * @param gain         points gained in the round (multiplier and {@code taxGain} included)
 * @param taxGain      part of {@code gain} from a Tax card (half of the target's bet gain), otherwise 0
 */
public record PlayerResult(Bet bet, boolean auto, String copiedFromId, int gain, int taxGain) {
}
