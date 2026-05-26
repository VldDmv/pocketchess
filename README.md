# PocketChess

A chess application written in Java, available both as an offline **desktop**
game (Swing) and as an **online** multiplayer server (Spring Boot). Both clients
share a single headless chess engine, so the rules, AI and variants behave
identically everywhere.

Supports three variants — **Classic**, **Chess960** (Fischer Random) and
**Floor is Lava** — in both Player‑vs‑Engine and Player‑vs‑Player play.

## Project layout

A multi‑module Maven build:

| Module    | Artifact              | What it is                                                       |
|-----------|-----------------------|------------------------------------------------------------------|
| `core`    | `pocketchess-core`    | The chess engine and game logic — headless, no UI or networking. |
| `desktop` | `pocketchess-desktop` | Swing desktop UI. Depends on `core`.                             |
| `online`  | `pocketchess-online`  | Spring Boot web server for online play. Depends on `core`.       |

All chess logic lives in `core`; the two clients are thin wrappers around it.

## Features

### Shared (both clients)
- **Variants:** Classic, Chess960, and Floor is Lava.
- **Player vs Engine** with three difficulties:
    - Easy — a casual opponent that makes occasional mistakes.
    - Medium — a solid club‑level player.
    - Hard — a challenging opponent using deeper search.
- **Complete chess rules**, including castling (Chess960 king‑takes‑rook),
  en passant, promotion, and draw detection (stalemate, threefold repetition,
  50‑move, insufficient material).
- **PGN** import and export (variant‑aware: the Chess960 back‑rank and the Lava
  RNG seed are preserved so games replay exactly).

### Desktop
- Custom time controls with optional increments.
- Move‑history table with full navigation back to any position.
- Captured pieces, clocks, and sound effects.

### Online
- **Accounts:** form login plus optional Google OAuth2 sign‑in.
- **Lobby:** create games vs the bot or post an open challenge for another player.
- **Player vs Player** in real time over WebSocket.
- **Per‑category Elo ratings** and player profiles — standard chess is split by
  time control, with Chess960 and Lava pooled separately.
- **Time controls** with increments, plus an optional **berserk** (halve your
  clock for a larger rating gain on a win).
- In‑board promotion picker, move‑history replay, in‑game chat, and
  draw / resign / rematch / takeback offers.
- Disconnect handling with a reconnect grace window.

## Tech stack

- **Language / build:** Java 17, multi‑module Maven.
- **Desktop UI:** Java Swing.
- **Online:** Spring Boot 3.3.5 — Web MVC, Security (form + OAuth2), WebSocket /
  STOMP over SockJS, Spring Data JPA with a file‑based H2 database, Thymeleaf
  templates. The browser side uses chessboard.js, jQuery and `@stomp/stompjs`.
- **AI engine** (in `core`):
    - Iterative‑deepening Negamax search with alpha‑beta pruning.
    - Move‑ordering heuristics (MVV‑LVA, killer moves, history heuristic).
    - Quiescence search to mitigate the horizon effect.
    - Transposition table with Zobrist hashing.
    - Evaluation considering material, piece‑square tables, mobility, king safety,
      pawn structure and other strategic factors.
- **Engine tests:** perft suites plus make/undo and generator/executor
  consistency invariants guard the move generator.

## Building and running

Requires JDK 17 and Maven.

```bash
# Build everything and install the core module into the local repository
mvn clean install            # with tests
mvn clean install -DskipTests

# Run the online server (http://localhost:8080)
mvn -pl online spring-boot:run

# Run the engine tests only
mvn -pl core test
```

The desktop client's entry point is
`org.pocketchess.ui.gameframepack.frame.GameFrame` (run it from your IDE, or
launch the built `desktop` artifact).

The online server stores accounts, ratings and game records in a file‑based H2
database under `./data/` and serves on port `8080`.

## Attributions

### Chess Pieces
The chess piece graphics are from the **Cardinal** set by **sadsnake1**, licensed under CC BY-NC-SA 4.0 , sourced from the PyChess project.
- **Source:** https://github.com/pychess/pychess/tree/master/pieces/cardinal

### Sound Effects
The sound effects are from the **PyChess** project.
- **Source:** https://github.com/pychess/pychess/tree/master/sounds
- **License:** GNU GPL v3
