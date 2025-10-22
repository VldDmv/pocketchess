package org.pocketchess.ui.gameframepack.notation;

import org.pocketchess.core.game.GameMode;
import org.pocketchess.core.game.GameStatus;
import org.pocketchess.core.game.moveanalyze.Move;
import org.pocketchess.core.game.TimeControl;
import org.pocketchess.core.pieces.*;
import org.pocketchess.core.general.Board;
import org.pocketchess.core.general.Game;

import java.util.ArrayList;
import java.util.List;

/**
 * Responsible for formatting chess moves in Standard Algebraic Notation (SAN).
 */
public class ChessNotationFormatter {
    private final Game game;

    public ChessNotationFormatter(Game game) {
        this.game = game;
    }

    /**
     * Converts a move into Standard Algebraic Notation (SAN).
     */
    public String getNotationForMove(Move move) {
        if (move == null || move.pieceMoved == null || move.start == null || move.end == null) {
            return "";
        }

        // CASTLING
        if (move.wasCastlingMove) {
            String symbol = getCheckOrMateSymbol(move);
            return (move.end.getY() > 5 ? "O-O" : "O-O-O") + symbol;
        }

        // Build the notation piece by piece
        String pieceSymbol = getPieceSymbol(move.pieceMoved);  // K, Q, R, B, N, or "" for pawn
        String captureSymbol = move.pieceKilled != null ? "x" : "";  // "x" if capturing
        String endCoords = getCoords(move.end);  // e.g. "e4"
        String promotion = move.promotedTo != null ? "=" + getPieceSymbol(move.promotedTo) : "";  // e.g. "=Q"

        // PAWN MOVES
        if (move.pieceMoved instanceof Pawn) {
            String startFile = (move.pieceKilled != null) ? String.valueOf(getCoords(move.start).charAt(0)) : "";
            return startFile + captureSymbol + endCoords + promotion + getCheckOrMateSymbol(move);
        }

        // PIECE MOVES
        String ambiguityHint = resolveAmbiguity(move);
        return pieceSymbol + ambiguityHint + captureSymbol + endCoords + promotion + getCheckOrMateSymbol(move);
    }

    /**
     * Returns the check or checkmate symbol to append to the move.
     */
    private String getCheckOrMateSymbol(Move move) {
        if (move.statusAfterMove == GameStatus.WHITE_WIN || move.statusAfterMove == GameStatus.BLACK_WIN) {
            return "#";  // Checkmate
        }
        if (move.statusAfterMove == GameStatus.CHECK) {
            return "+";  // Check
        }
        return "";
    }

    /**
     * Resolves ambiguity when multiple pieces of the same type can move to the same square.
     */
    private String resolveAmbiguity(Move move) {
        // Find this move's position in the game history
        int moveIndex = this.game.getMoveHistory().indexOf(move);
        if (moveIndex == -1) {
            return "";
        }

        // Recreate the board state at the moment this move was made
        Game tempGame = new Game();
        tempGame.resetGame(new TimeControl(1, 0), GameMode.PVP, Piece.Color.WHITE);

        // Replay all moves up to (but not including) the current move
        List<Move> history = this.game.getMoveHistory();
        for (int i = 0; i < moveIndex; i++) {
            Move historyMove = history.get(i);
            tempGame.playerMove(historyMove.start.getX(), historyMove.start.getY(),
                    historyMove.end.getX(), historyMove.end.getY(),
                    historyMove.promotedTo);
        }

        // Get the board state and relevant squares
        Board tempBoard = tempGame.getBoard();
        Spot tempStartSpot = tempBoard.getBox(move.start.getX(), move.start.getY());
        Spot tempEndSpot = tempBoard.getBox(move.end.getX(), move.end.getY());

        // Find all pieces of the same type and color (excluding the current piece)
        List<Spot> candidateSpots = new ArrayList<>();
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = tempBoard.getBox(r, c).getPiece();
                if (p != null &&
                        p.getClass() == move.pieceMoved.getClass() &&  // Same type
                        p.isWhite() == move.pieceMoved.isWhite() &&     // Same color
                        !(r == tempStartSpot.getX() && c == tempStartSpot.getY())) {  // Different square
                    candidateSpots.add(tempBoard.getBox(r, c));
                }
            }
        }

        // Check which of these candidates can also legally move to the target square
        List<Spot> ambiguousAttackers = new ArrayList<>();
        for (Spot candidateSpot : candidateSpots) {
            if (tempGame.isMoveLegal(candidateSpot, tempEndSpot)) {
                ambiguousAttackers.add(candidateSpot);
            }
        }

        // If no other pieces can move there, no ambiguity
        if (ambiguousAttackers.isEmpty()) {
            return "";
        }

        // Determine what information is needed: file, rank, or both
        boolean fileIsUnique = true;
        boolean rankIsUnique = true;

        for (Spot attackerSpot : ambiguousAttackers) {
            if (attackerSpot.getY() == tempStartSpot.getY()) {
                fileIsUnique = false;  // Another piece on the same file (column)
            }
            if (attackerSpot.getX() == tempStartSpot.getX()) {
                rankIsUnique = false;  // Another piece on the same rank (row)
            }
        }

        if (fileIsUnique) {
            return String.valueOf(getCoords(tempStartSpot).charAt(0)); // Just file (a-h)
        } else if (rankIsUnique) {
            return String.valueOf(getCoords(tempStartSpot).charAt(1)); // Just rank (1-8)
        } else {
            return getCoords(tempStartSpot); // Full coordinates (e.g., "a1")
        }
    }

    /**
     * Returns the piece symbol for notation.
     */
    private String getPieceSymbol(Piece piece) {
        if (piece instanceof King) return "K";
        if (piece instanceof Queen) return "Q";
        if (piece instanceof Rook) return "R";
        if (piece instanceof Bishop) return "B";
        if (piece instanceof Knight) return "N";
        return ""; //pawn
    }

    /**
     * Converts board coordinates to chess notation.
     */
    private String getCoords(Spot spot) {
        return "" + (char) ('a' + spot.getY()) + (8 - spot.getX());
    }
}