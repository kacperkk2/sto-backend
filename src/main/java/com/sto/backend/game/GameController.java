package com.sto.backend.game;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sto.backend.auth.AuthenticatedPlayer;
import com.sto.backend.auth.CurrentPlayer;
import com.sto.backend.dto.BetRequest;
import com.sto.backend.dto.CardRequest;
import com.sto.backend.dto.GameSettingsRequest;
import com.sto.backend.dto.GameStateDto;
import com.sto.backend.dto.TargetRequest;

@RestController
@RequestMapping("/rooms/{code}")
public class GameController {

	private final GameService gameService;

	public GameController(GameService gameService) {
		this.gameService = gameService;
	}

	@PostMapping("/game")
	public GameStateDto start(@CurrentPlayer AuthenticatedPlayer me,
			@RequestBody(required = false) GameSettingsRequest request) {
		return gameService.startGame(me, request);
	}

	@PutMapping("/settings")
	public GameStateDto settings(@CurrentPlayer AuthenticatedPlayer me,
			@RequestBody(required = false) GameSettingsRequest request) {
		return gameService.updateSettings(me, request);
	}

	@PostMapping("/lobby")
	public GameStateDto lobby(@CurrentPlayer AuthenticatedPlayer me) {
		return gameService.returnToLobby(me);
	}

	@PostMapping("/rounds/next")
	public GameStateDto next(@CurrentPlayer AuthenticatedPlayer me) {
		return gameService.nextRound(me);
	}

	@PostMapping("/rounds/current/card")
	public GameStateDto card(@CurrentPlayer AuthenticatedPlayer me, @RequestBody(required = false) CardRequest request) {
		return gameService.playCard(me, request);
	}

	@PostMapping("/rounds/current/card/pass")
	public GameStateDto passCard(@CurrentPlayer AuthenticatedPlayer me) {
		return gameService.passCard(me);
	}

	@PostMapping("/rounds/current/va-banque-target")
	public GameStateDto vaBanqueTarget(@CurrentPlayer AuthenticatedPlayer me,
			@RequestBody(required = false) TargetRequest request) {
		return gameService.pickVaBanqueTarget(me, request == null ? null : request.targetId());
	}

	@PostMapping("/rounds/current/bet")
	public GameStateDto bet(@CurrentPlayer AuthenticatedPlayer me, @RequestBody(required = false) BetRequest request) {
		return gameService.placeBet(me, request);
	}

	@DeleteMapping("/rounds/current/bet")
	public GameStateDto withdrawBet(@CurrentPlayer AuthenticatedPlayer me) {
		return gameService.withdrawBet(me);
	}

	@PostMapping("/rounds/current/copy")
	public GameStateDto copy(@CurrentPlayer AuthenticatedPlayer me,
			@RequestBody(required = false) TargetRequest request) {
		return gameService.copyBet(me, request == null ? null : request.targetId());
	}

	@PostMapping("/rounds/current/keep")
	public GameStateDto keep(@CurrentPlayer AuthenticatedPlayer me) {
		return gameService.keepBet(me);
	}
}
