package com.sto.backend.game;

/**
 * A card played in the {@link RoundPhase#CARDS} phase, public once the phase is over.
 *
 * @param targetId the player the card is aimed at (the player themselves for self cards)
 */
public record PlayedCard(String playerId, Card card, String targetId) {
}
