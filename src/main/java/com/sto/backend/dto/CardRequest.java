package com.sto.backend.dto;

import com.sto.backend.game.Card;

/** @param targetId only for cards aimed at another player */
public record CardRequest(Card card, String targetId) {
}
