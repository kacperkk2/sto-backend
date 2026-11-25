# API contract — Sto

Shared source of truth for the backend (this repo) and the frontend (`../sto`, Angular). Every change to the contract must land here. Game rules: `../sto/GAME_DESIGN.md`.

## Conventions

- **Base path:** `/api`, JSON, UTF-8.
- **Authorization:** `X-Player-Token: <playerToken>` header. SSE is the exception: the token goes in the query string as `?token=`.
- **Room code:** the room number, `"1"`–`"5"` (a string). At most 5 rooms exist at a time; creating one takes the lowest free number, and a closed or expired room frees its number for reuse.
- **Server authority:** the server runs the phases and timers, and the client only displays them.
  - The countdown is `phaseEndsAt`, corrected by the difference between `serverTime` and the local clock.
  - A state with a `version` lower than the one already held is ignored, because SSE can deliver states out of order.
- **Secrets:** public state never contains secrets.
  - The correct answer and the bets show up only in `REVEALED`.

## Models

```ts
type RoomStatus = 'LOBBY' | 'IN_GAME' | 'FINISHED' | 'CLOSED';
type RoundEvent = 'VA_BANQUE' | 'DOUBLE' | 'OPEN_BET' | 'BLIND_COPY' | 'ALL_IN' | 'BLITZ';
type RoundPhase = 'CARDS' | 'VA_BANQUE_PICK' | 'OPEN_BET' | 'BETTING' | 'COPY_PICK' | 'REVEALED';
type Card = 'VA_BANQUE' | 'DOUBLE' | 'PEEK' | 'COPY' | 'BLITZ' | 'TAX'; // DOUBLE, COPY: self; others: another player

interface Bet { onA: number; onB: number; }          // sum 100, step 5
interface PlayedCard { playerId: string; card: Card; targetId: string; } // self cards: targetId = playerId
interface GameSettings { rounds: number; eventChance: number; betSeconds: number; lastRoundDouble: boolean; events: RoundEvent[]; cards: Card[]; cardSeconds: number; scenario: string; }
interface ScenarioDto { id: string; name: string; count: number; random: boolean; } // count = questions in the scenario; random: false = file order

interface GameStateDto {                              // public, the same for everyone
  code: string;
  status: RoomStatus;
  version: number;                                    // grows with every change
  serverTime: string;                                 // ISO-8601
  hostId: string;
  settings: GameSettings;                             // in a game: its settings; otherwise those of the next game
  players: { id: string; nick: string; isHost: boolean; score: number;
             cards: Card[] }[];                       // cards not played yet; change only after a CARDS phase
  ranking: string[];                                  // player ids by score (ties keep join order)
  round: RoundDto | null;
  winnerIds: string[];                                // when FINISHED
}

interface RoundDto {
  number: number;                                     // tie-break rounds keep counting
  totalRounds: number;                                // regular rounds from the settings
  tiebreak: boolean;
  participantIds: string[];                           // in a tie-break only the tied players
  question: { id: string; category: string; text: string; answers: [string, string] };
  event: RoundEvent | null;
  phase: RoundPhase;
  phaseEndsAt: string | null;                         // null in REVEALED
  vaBanqueChooserId: string | null;
  vaBanqueTargetId: string | null;
  openBettorIds: string[];                            // bet first: OPEN_BET event leader (first) + PEEK card targets
  copyActorIds: string[];                             // may copy after betting: BLIND_COPY drawn player (first, public from the start) + COPY card players
  openBets: Record<string, Bet>;                      // public bet of the OPEN_BET event leader, after the OPEN_BET phase ends (PEEK targets: MyStateDto.seenBets only)
  cardPlayerIds: string[];                            // who decides in CARDS (participants holding cards)
  cardDoneIds: string[];                              // who already played a card or passed (choices stay secret)
  playedCards: PlayedCard[];                          // after the CARDS phase ends
  allInIds: string[];                                 // must bet 100/0 (event and cards)
  doubledIds: string[];                               // points of the round x2 (event and cards)
  blitzIds: string[];                                 // Blitz card targets
  blitzEndsAt: string | null;                         // own deadline of the blitz targets betting in this phase (if earlier than phaseEndsAt)
  betPlayerIds: string[];                             // who already bet
  result: {                                           // only in REVEALED
    correct: 0 | 1;
    players: Record<string, { bet: Bet; auto: boolean; copiedFromId: string | null; gain: number; taxGain: number }>;
  } | null;
}

interface MyStateDto {                                // private, for the token owner
  playerId: string;
  isHost: boolean;
  participating: boolean;
  hand: Card[];                                       // own cards not played yet
  canPlayCard: boolean;                               // in CARDS: may play a card or pass
  cardDecided: boolean;                               // in CARDS: already played a card or passed
  myCard: PlayedCard | null;                          // own card of this round (also during CARDS)
  myBet: Bet | null;
  canBet: boolean;
  canChangeBet: boolean;                              // in BETTING: own bet can be taken back (DELETE bet)
  mustGoAllIn: boolean;                               // va banque target (event or card), everyone in ALL_IN
  isOpenBettor: boolean;
  myDeadline: string | null;                          // Blitz card target: own earlier deadline, while the bet is missing
  canPickTarget: boolean;                             // va banque chooser in VA_BANQUE_PICK
  isCopyActor: boolean;
  canCopy: boolean;                                   // copy actor in COPY_PICK who has not decided yet (copy or keep)
  seenBets: Record<string, Bet>;                      // bets only I see: my PEEK targets (after OPEN_BET, until REVEALED); with a COPY card in COPY_PICK: everyone else's
  copyTargetId: string | null;
}

interface RoomSummaryDto {                           // public room list entry
  code: string;
  status: RoomStatus;
  hostNick: string;
  playerCount: number;
  playerNicks: string[];                              // join order, host first
  joinable: boolean;                                  // LOBBY or FINISHED
}

interface ErrorDto { error: ErrorCode; message?: string; }
type ErrorCode = 'ROOM_NOT_FOUND' | 'ROOM_CLOSED' | 'NICK_TAKEN' | 'UNAUTHORIZED' | 'NOT_HOST'
               | 'INVALID_STATE' | 'VALIDATION' | 'NOT_ENOUGH_PLAYERS'
               | 'GAME_IN_PROGRESS' | 'NOT_YOUR_TURN' | 'ALREADY_BET' | 'ROOM_LIMIT';
```

## Endpoints

| Method | Path | Who | Body → Response | Errors |
|---|---|---|---|---|
| GET | `/rooms` | anyone | → `RoomSummaryDto[]` (by number) | — |
| GET | `/questions/scenarios` | anyone | → `ScenarioDto[]` (by id; the first one is the default) | — |
| POST | `/rooms` | anyone | `{nick}` → 201 `{code, playerId, playerToken}` | 400 VALIDATION, 409 ROOM_LIMIT (all 5 taken) |
| POST | `/rooms/{code}/players` | anyone | `{nick}` → 201 `{playerId, playerToken}` | 404, 409 NICK_TAKEN, 409 GAME_IN_PROGRESS, 410 ROOM_CLOSED, 400 |
| GET | `/rooms/{code}` | player | → `GameStateDto` | 401, 404 |
| GET | `/rooms/{code}/me` | player | → `MyStateDto` | 401, 404 |
| PUT | `/rooms/{code}/settings` | host | `Partial<GameSettings>` → `GameStateDto` (missing fields = defaults; everyone gets them via SSE) | 403, 409 INVALID_STATE (game in progress), 400 |
| POST | `/rooms/{code}/game` | host | `Partial<GameSettings> & {excludedQuestionIds?: string[]}` or no body (= room settings) → `GameStateDto`; a body with any settings field also replaces the room settings. `excludedQuestionIds` (≤ 10000, unknown ids ignored): questions of the game's scenario the host played in earlier games — not drawn until every other question of the scenario was used, then questions repeat | 403, 409 INVALID_STATE (game in progress), 409 NOT_ENOUGH_PLAYERS (<2), 400 |
| POST | `/rooms/{code}/lobby` | host | → `GameStateDto` (back to `LOBBY` after a finished game; scores and ranking dropped, room settings kept) | 403, 409 INVALID_STATE (not FINISHED) |
| POST | `/rooms/{code}/rounds/current/card` | card player | `{card, targetId?}` → `GameStateDto` (`targetId` only for cards aimed at another player) | 409 NOT_YOUR_TURN (no cards) / INVALID_STATE (not CARDS, already decided), 400 (card not in hand, bad target) |
| POST | `/rooms/{code}/rounds/current/card/pass` | card player | → `GameStateDto` (no card this round) | 409 NOT_YOUR_TURN / INVALID_STATE |
| POST | `/rooms/{code}/rounds/current/va-banque-target` | chooser | `{targetId}` → `GameStateDto` | 409 NOT_YOUR_TURN / INVALID_STATE, 400 |
| POST | `/rooms/{code}/rounds/current/bet` | participant | `Bet` → `GameStateDto` | 409 NOT_YOUR_TURN / ALREADY_BET / INVALID_STATE, 400 (sum, step, va banque ≠ 100/0) |
| DELETE | `/rooms/{code}/rounds/current/bet` | participant | → `GameStateDto` (takes back the own bet to change it) | 409 NOT_YOUR_TURN / INVALID_STATE (not BETTING, no own bet, open bet) |
| POST | `/rooms/{code}/rounds/current/copy` | copy actor | `{targetId}` → `GameStateDto` (takes over that bet; reveals once all copy actors decided) | 409 NOT_YOUR_TURN / INVALID_STATE (not COPY_PICK, already decided), 400 |
| POST | `/rooms/{code}/rounds/current/keep` | copy actor | → `GameStateDto` (keeps the own bet; reveals once all copy actors decided) | 409 NOT_YOUR_TURN / INVALID_STATE (not COPY_PICK, already decided) |
| POST | `/rooms/{code}/rounds/next` | host | → `GameStateDto` | 403, 409 INVALID_STATE (round not revealed) |
| POST | `/rooms/{code}/bots` | host | `{count}` (1–50) → `GameStateDto`. **Dev only** (`app.dev.bots-enabled`, otherwise 404): adds bots „Bot N” that act instantly — pass in CARDS, 50/50 (who must go all in: random 100/0), random va banque target, keep in Blind copy | 403, 409 GAME_IN_PROGRESS, 400 |
| DELETE | `/rooms/{code}` | host | → 204 | 403 |
| GET | `/rooms/{code}/events?token=` | player | `text/event-stream` | 401, 404 |

**Game settings:**

| Field | Default | Range |
|---|---|---|
| `rounds` | 10 | 1–30 |
| `eventChance` | 50 | 0–100 |
| `betSeconds` | 30 | 10–120 |
| `lastRoundDouble` | `false` | — |
| `events` | all events | any subset of `RoundEvent` (duplicates dropped); `[]` = plain rounds only. `lastRoundDouble` works regardless |
| `cards` | `[]` (no cards) | any subset of `Card` (duplicates dropped); every player gets one of each at the start of the game |
| `cardSeconds` | 15 | 5–60; time of the CARDS phase |
| `scenario` | the default scenario | id of a scenario from `/questions/scenarios`; unknown id = 400 |

**Question scenarios:** every `src/main/resources/questions/*.json` file is one scenario; its id is the file name without `.json`. A file is either a plain array of questions (name = id, random order) or `{"name": "…", "random": true, "questions": [...]}` (`name` and `random` optional; `random` defaults to `true`). With `"random": false` questions go in file order: each round takes the first question not used yet (in this game or excluded by `excludedQuestionIds`), so the host's next game continues where the last one ended; after the last question it starts over. A question is `{id, category, text, answers: [A, B], correct: 0 | 1}`; ids are unique within a scenario. Adding a scenario = adding a file (and restarting the backend).

**Joining:** possible in `LOBBY` and `FINISHED`. Starting `/game` again from `FINISHED` begins a new game with scores reset to 0; `/lobby` instead goes back to the lobby first (the frontend's „Zagraj ponownie”).

## Round phases

```
[CARDS cardSeconds] → [VA_BANQUE_PICK 10 s] → [OPEN_BET betSeconds] → BETTING betSeconds (BLITZ: min(betSeconds, 15)) → [COPY_PICK 15 s] → REVEALED --host: rounds/next--> next round | FINISHED
```

- **CARDS** (regular rounds only, when a participant still holds cards): everyone with cards plays one (`card`) or passes (`card/pass`), without seeing the others' choices; ends early once all decided, on timeout who did not decide passes. Then the played cards are used up (also those without effect), become public in `playedCards` and add their effects; the same effect twice on one player counts once.
- **VA_BANQUE_PICK:** if the chooser misses the time, the target is random.
- **OPEN_BET** (when `openBettorIds` is not empty): the open bettors bet at the same time, bindingly; if one misses the time, they get 50/50 (random 100/0 if they must go all in). When the phase ends, the event leader's bet becomes public (`openBets`) and each PEEK target's bet reaches only the peeking player (`seenBets`).
- **BETTING:** ends early once everyone has acted. Until then a player may take back their own bet (`DELETE bet`) and counts as not done again, except binding open bets and Blitz card targets after their own deadline.
- **Blitz card:** targets bet until `blitzEndsAt` = phase start + min(betSeconds, 15) (in the phase where they bet: OPEN_BET or BETTING), then they get the auto bet; the phase goes on for the others.
- **Missed bet:** at the end of BETTING a missing bet becomes 50/50. Who must go all in (`allInIds`) instead gets a random 100/0.
- **COPY_PICK** (when `copyActorIds` is not empty): each copy actor (who bet like everyone) either copies another participant's bet (`copy`) or keeps their own (`keep`); on timeout who did not decide keeps their own. The BLIND_COPY event's actor decides blind, COPY card players see everyone's bets (`seenBets`). A copy takes the target's own bet (no chains). Everyone else waits.
- **Points:**
  - `gain = stake on the correct answer × (player in doubledIds ? 2 : 1)`; the multiplier is the player's own, also for a copied bet.
  - TAX card: the card's player additionally gets `floor(target's bet gain / 2)` (`taxGain`, included in `gain`). The target's bet gain includes their multiplier but not their own `taxGain` (no chains); the card player's multiplier does not apply to it. The target loses nothing.
- **End of the game:** after the last regular round, `rounds/next` either:
  - starts a tie-break round, if the top score is tied, or
  - ends the game.

## SSE events

| `event:` | `data:` | When |
|---|---|---|
| `state` | `GameStateDto` | right after connecting and after every change (join, actions, phase timeouts) |
| `room-closed` | `{}` | after `DELETE` or expiry; the stream then ends |

Heartbeat: an SSE comment (`:ping`) every ~25 s. After `state`, the frontend fetches `GET /me` when it needs private data (own hand and card, whether it can bet, copy or pick a target).
