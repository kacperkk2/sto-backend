package com.sto.backend.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.sto.backend.game.Bet;
import com.sto.backend.game.Card;
import com.sto.backend.game.Game;
import com.sto.backend.game.PlayedCard;
import com.sto.backend.game.Round;
import com.sto.backend.game.RoundPhase;
import com.sto.backend.room.Player;
import com.sto.backend.room.Room;

/**
 * Private state of the token owner in the current round.
 *
 * @param participating  takes part in the current round (false in the lobby and for players outside a tie-break)
 * @param hand           own cards not played yet
 * @param canPlayCard    in {@code CARDS}: may play a card or pass
 * @param cardDecided    in {@code CARDS}: already played a card or passed
 * @param myCard         own card played in this round (also during {@code CARDS}), {@code null} when none
 * @param myBet          own bet, once placed
 * @param canBet         may place a bet right now
 * @param canChangeBet   may take back the own bet ({@code DELETE bet}) to place it again
 * @param mustGoAllIn    only 100/0 bets are accepted (va banque target, Va banque card target, All in round)
 * @param isOpenBettor   bets first, publicly (Open bet event leader or card target)
 * @param myDeadline     own earlier deadline (Blitz card target), while the bet is missing
 * @param canPickTarget  is the va banque chooser and the target is not picked yet
 * @param isCopyActor    may copy a bet after the betting (Blind copy event or card)
 * @param canCopy        in {@code COPY_PICK}: may copy someone's bet or keep the own one
 * @param copyTargetId   whose bet was copied, once chosen
 * @param seenBets       bets only this player sees: Peek card targets (after the open bet phase) and, for a
 *                       Copy card player in {@code COPY_PICK}, everyone else's bets
 */
public record MyStateDto(
		String playerId,
		boolean isHost,
		boolean participating,
		List<Card> hand,
		boolean canPlayCard,
		boolean cardDecided,
		PlayedCard myCard,
		Bet myBet,
		boolean canBet,
		boolean canChangeBet,
		boolean mustGoAllIn,
		boolean isOpenBettor,
		Instant myDeadline,
		boolean canPickTarget,
		boolean isCopyActor,
		boolean canCopy,
		String copyTargetId,
		Map<String, Bet> seenBets) {

	public static MyStateDto of(Room room, Player me) {
		synchronized (room) {
			String id = me.id();
			Game game = room.game().orElse(null);
			List<Card> hand = game == null ? List.of() : game.hand(id);
			Round round = game == null || game.isFinished() ? null : game.currentRound();
			if (round == null || !round.isParticipant(id)) {
				return new MyStateDto(id, room.isHost(id), false, hand, false, false, null, null, false, false, false,
						false, null, false, false, false, null, Map.of());
			}
			RoundPhase phase = round.phase();
			boolean done = round.isDone(id);
			boolean openBettor = round.isOpenBettor(id);
			boolean copyActor = round.isCopyActor(id);
			boolean cards = phase == RoundPhase.CARDS && round.isCardPlayer(id);
			PlayedCard myCard = phase == RoundPhase.CARDS
					? round.cardChoiceOf(id)
					: round.playedCards().stream().filter(p -> p.playerId().equals(id)).findFirst().orElse(null);
			boolean canBet = (phase == RoundPhase.BETTING && !done)
					|| (phase == RoundPhase.OPEN_BET && openBettor && !done);
			return new MyStateDto(
					id,
					room.isHost(id),
					true,
					hand,
					cards && !round.hasDecidedCard(id),
					cards && round.hasDecidedCard(id),
					myCard,
					round.betOf(id).orElse(null),
					canBet,
					phase == RoundPhase.BETTING && round.canChangeBet(id),
					round.mustGoAllIn(id),
					openBettor,
					canBet ? round.deadlineOf(id) : null,
					phase == RoundPhase.VA_BANQUE_PICK && id.equals(round.vaBanqueChooserId()),
					copyActor,
					copyActor && phase == RoundPhase.COPY_PICK && !round.hasDecidedCopy(id),
					copyActor ? round.copyTargetOf(id) : null,
					round.seenBetsOf(id));
		}
	}
}
