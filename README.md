# PocketChess

A classic chess game built in Java using the Swing framework.

## Features

- **Supports both Player vs Engine (PVE) and Player vs Player (PVP) modes**
- **Different AI Difficulties:**
    - Easy: A casual opponent that makes occasional mistakes.
    - Medium: A solid club-level player.
    - Hard: A challenging opponent that utilizes advanced search algorithms.
- **Custom Time Controls:** Set up games with various time controls with optional increments.
- **Full Game Navigation:** Includes a move history table that allows you to review the game and navigate back to any
  position.
- **PGN Support:** Save your games to the PGN format and copy them to the clipboard or load PGN files to analyze positions.
- **Complete Chess Rules:** Implements all standard chess rules.

## Tech Stack

- **Language:** Java 17
- **GUI:** Java Swing
- **AI Engine:**
    - Iterative Deepening Negamax search algorithm.
    - Alpha-Beta Pruning for search optimization.
    - Advanced move ordering heuristics (MVV-LVA, Killer Moves, History Heuristic).
    - Quiescence search to mitigate the horizon effect.
    - Transposition Table with Zobrist hashing for caching positions.
    - A comprehensive evaluation function that considers material, piece-square tables, mobility, king safety, pawn
      structure, and other strategic concepts.


### Chess Pieces
The chess piece graphics are from the **Cardinal** set by **sadsnake1**, licensed under CC BY-NC-SA 4.0 , sourced from the PyChess project.
- **Source:** https://github.com/pychess/pychess/tree/master/pieces/cardinal

### Sound Effects
The sound effects are from the **PyChess** project.
- **Source:** https://github.com/pychess/pychess/tree/master/sounds
- **License:** GNU GPL v3