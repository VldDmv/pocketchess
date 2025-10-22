package org.pocketchess.core.ai;

import org.pocketchess.core.game.moveanalyze.Move;
import org.pocketchess.core.general.Board;
import org.pocketchess.core.general.Game;
import org.pocketchess.core.pieces.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Fast move generator for chess pieces.
 * This class efficiently generates all legal moves for the current board position.
 * Used by the AI and for validating moves during gameplay.
 */
public class FastMoveGenerator {

    // Knight moves in an L-shape: 2 squares in one direction, 1 in perpendicular
    private static final int[][] KNIGHT_MOVES = {{-2, -1}, {-2, 1}, {-1, -2}, {-1, 2}, {1, -2}, {1, 2}, {2, -1}, {2, 1}};

    // Bishop moves diagonally in 4 directions
    private static final int[][] BISHOP_DIRECTIONS = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};

    // Rook moves horizontally and vertically in 4 directions
    private static final int[][] ROOK_DIRECTIONS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    // Queen combines Bishop and Rook movements (8 directions)
    private static final int[][] QUEEN_DIRECTIONS = {{-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}};

    // King moves one square in any direction (8 possible moves)
    private static final int[][] KING_MOVES = {{-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}};

    /**
     * Generates all legal moves for the current game position.
     */
    public List<Move> generateMoves(Game game) {

        List<Move> pseudoLegalMoves = new ArrayList<>(128);
        generatePseudoLegalMoves(game, pseudoLegalMoves);

       // Filter to only legal moves (those that don't leave king in check)
        List<Move> legalMoves = new ArrayList<>(pseudoLegalMoves.size());
        for (Move move : pseudoLegalMoves) {
            // Temporarily make the move on the board
            game.makeTemporaryMove(move);

            if (!game.isKingInCheck(!game.isWhiteTurn())) {
                legalMoves.add(move);
            }

            game.undoTemporaryMove(move);
        }
        return legalMoves;
    }

    /**
     * Creates a Move object with all necessary information.
     */
    private Move createMove(Game game, Spot start, Spot end, Piece moved, Piece killed) {
        boolean wasFirstMove = false;
        // Track if King or Rook hasn't moved yet
        if (moved instanceof King) wasFirstMove = !((King) moved).hasMoved();
        if (moved instanceof Rook) wasFirstMove = !((Rook) moved).hasMoved();

        return new Move(start, end, moved, killed, false, wasFirstMove,
                game.getBoard().getEnPassantTargetSquare(), 0, 0, 0);
    }

    /**
     * Creates a special Move for pawn promotion.
     */
    private Move createPromotionMove(Game game, Spot start, Spot end, Piece captured, Piece promotedTo) {
        Move move = createMove(game, start, end, start.getPiece(), captured);
        move.promotedTo = promotedTo;
        return move;
    }

    /**
     * Generates all pseudo-legal moves for the current position.
     * Scans the entire board and generates moves for each piece of the current player.
     */
    private void generatePseudoLegalMoves(Game game, List<Move> moves) {
        Board board = game.getBoard();
        boolean isWhite = game.isWhiteTurn();

        // Scan all 64 squares of the chess board
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece piece = board.getBox(r, c).getPiece();

                // Only generate moves for pieces belonging to the current player
                if (piece != null && piece.isWhite() == isWhite) {
                    // Generate moves based on piece type
                    if (piece instanceof Pawn) generatePawnMoves(game, r, c, piece, moves);
                    else if (piece instanceof Knight) generateKnightMoves(game, r, c, piece, moves);
                    else if (piece instanceof Bishop) generateSlidingMoves(game, r, c, piece, BISHOP_DIRECTIONS, moves);
                    else if (piece instanceof Rook) generateSlidingMoves(game, r, c, piece, ROOK_DIRECTIONS, moves);
                    else if (piece instanceof Queen) generateSlidingMoves(game, r, c, piece, QUEEN_DIRECTIONS, moves);
                    else if (piece instanceof King) generateKingMoves(game, r, c, piece, moves);
                }
            }
        }
    }

    /**
     * Generates moves for sliding pieces (Bishop, Rook, Queen).
     */
    private void generateSlidingMoves(Game game, int r, int c, Piece piece, int[][] directions, List<Move> moves) {
        Spot start = game.getBoard().getBox(r, c);

        // Try each direction
        for (int[] dir : directions) {
            int tr = r + dir[0];
            int tc = c + dir[1];

            // Keep moving in this direction until we hit something
            while (tr >= 0 && tr < 8 && tc >= 0 && tc < 8) {
                Spot end = game.getBoard().getBox(tr, tc);
                Piece targetPiece = end.getPiece();

                if (targetPiece == null) {
                    // Empty square - we can move here and continue
                    moves.add(createMove(game, start, end, piece, null));
                } else {
                    // Square is occupied
                    if (targetPiece.isWhite() != piece.isWhite()) {
                        // Enemy piece - we can capture it
                        moves.add(createMove(game, start, end, piece, targetPiece));
                    }
                    // Can't continue past this piece
                    break;
                }

                // Continue sliding in this direction
                tr += dir[0];
                tc += dir[1];
            }
        }
    }

    /**
     * Generates all possible knight moves.
     */
    private void generateKnightMoves(Game game, int r, int c, Piece piece, List<Move> moves) {
        Spot start = game.getBoard().getBox(r, c);

        // Try all 8 possible L-shaped moves
        for (int[] move : KNIGHT_MOVES) {
            int tr = r + move[0];
            int tc = c + move[1];

            // Check if target square is on the board
            if (tr >= 0 && tr < 8 && tc >= 0 && tc < 8) {
                Spot end = game.getBoard().getBox(tr, tc);
                Piece targetPiece = end.getPiece();

                // Can move if square is empty or contains enemy piece
                if (targetPiece == null || targetPiece.isWhite() != piece.isWhite()) {
                    moves.add(createMove(game, start, end, piece, targetPiece));
                }
            }
        }
    }

    /**
     * Generates all possible king moves including castling.
     */
    private void generateKingMoves(Game game, int r, int c, Piece piece, List<Move> moves) {
        Spot start = game.getBoard().getBox(r, c);

        // Generate normal one-square moves in all 8 directions
        for (int[] move : KING_MOVES) {
            int tr = r + move[0];
            int tc = c + move[1];

            if (tr >= 0 && tr < 8 && tc >= 0 && tc < 8) {
                Spot end = game.getBoard().getBox(tr, tc);
                Piece targetPiece = end.getPiece();

                if (targetPiece == null || targetPiece.isWhite() != piece.isWhite()) {
                    moves.add(createMove(game, start, end, piece, targetPiece));
                }
            }
        }

        // Check for castling moves (only if king hasn't moved yet)
        if (piece instanceof King && !((King) piece).hasMoved()) {
            boolean isWhite = piece.isWhite();


            if (game.isKingInCheck(isWhite)) {
                return;
            }

            // KINGSIDE CASTLING (short castling, O-O)
            if (game.getBoard().getBox(r, c + 1).getPiece() == null &&
                    game.getBoard().getBox(r, c + 2).getPiece() == null) {

                Piece rook = game.getBoard().getBox(r, c + 3).getPiece();


                if (rook instanceof Rook && !((Rook) rook).hasMoved()) {

                    if (!game.isSquareUnderAttack(game.getBoard().getBox(r, c + 1), !isWhite) &&
                            !game.isSquareUnderAttack(game.getBoard().getBox(r, c + 2), !isWhite)) {
                        Move castlingMove = new Move(start, game.getBoard().getBox(r, c + 2),
                                piece, null, true, true,
                                game.getBoard().getEnPassantTargetSquare(), 0, 0, 0);
                        moves.add(castlingMove);
                    }
                }
            }

            // QUEENSIDE CASTLING (long castling, O-O-O)
            if (game.getBoard().getBox(r, c - 1).getPiece() == null &&
                    game.getBoard().getBox(r, c - 2).getPiece() == null &&
                    game.getBoard().getBox(r, c - 3).getPiece() == null) {

                Piece rook = game.getBoard().getBox(r, c - 4).getPiece();

                if (rook instanceof Rook && !((Rook) rook).hasMoved()) {
                    // Check that king doesn't pass through or land on attacked squares
                    if (!game.isSquareUnderAttack(game.getBoard().getBox(r, c - 1), !isWhite) &&
                            !game.isSquareUnderAttack(game.getBoard().getBox(r, c - 2), !isWhite)) {
                        Move castlingMove = new Move(start, game.getBoard().getBox(r, c - 2),
                                piece, null, true, true,
                                game.getBoard().getEnPassantTargetSquare(), 0, 0, 0);
                        moves.add(castlingMove);
                    }
                }
            }
        }
    }

    /**
     * Generates all possible pawn moves.
     */
    private void generatePawnMoves(Game game, int r, int c, Piece piece, List<Move> moves) {
        Board board = game.getBoard();
        Spot start = board.getBox(r, c);

        // Pawns move in opposite directions depending on color
        int direction = piece.isWhite() ? -1 : 1;  // White moves up (-1), Black moves down (+1)
        int startRow = piece.isWhite() ? 6 : 1;    // Starting row for each color
        int promotionRow = piece.isWhite() ? 0 : 7; // Row where pawn promotes

        // FORWARD MOVES
        int oneStep = r + direction;
        if (oneStep >= 0 && oneStep < 8 && board.getBox(oneStep, c).getPiece() == null) {
            Spot end = board.getBox(oneStep, c);

            // Check if pawn reaches promotion row
            if (oneStep == promotionRow) {
                addPromotionMoves(game, start, end, null, moves);
            } else {
                moves.add(createMove(game, start, end, piece, null));
            }

            // Two-square advance from starting position
            if (r == startRow) {
                int twoSteps = r + 2 * direction;
                if (board.getBox(twoSteps, c).getPiece() == null) {
                    Spot endTwo = board.getBox(twoSteps, c);
                    moves.add(createMove(game, start, endTwo, piece, null));
                }
            }
        }

        // CAPTURE MOVES (diagonal)
        int[] captureCols = {c - 1, c + 1};  // Left and right diagonal
        for (int tc : captureCols) {
            if (tc >= 0 && tc < 8 && oneStep >= 0 && oneStep < 8) {
                Spot end = board.getBox(oneStep, tc);
                Piece targetPiece = end.getPiece();

                // Normal diagonal capture
                if (targetPiece != null && targetPiece.isWhite() != piece.isWhite()) {
                    if (oneStep == promotionRow) {
                        addPromotionMoves(game, start, end, targetPiece, moves);
                    } else {
                        moves.add(createMove(game, start, end, piece, targetPiece));
                    }
                }
                // en pass
                if (end == board.getEnPassantTargetSquare()) {
                    Spot capturedPawnSpot = board.getBox(r, tc);
                    moves.add(createMove(game, start, end, piece, capturedPawnSpot.getPiece()));
                }
            }
        }
    }

    /**
     * Adds all four possible promotion moves for a pawn.
     */
    private void addPromotionMoves(Game game, Spot start, Spot end, Piece captured, List<Move> moves) {
        boolean isWhite = start.getPiece().isWhite();

        moves.add(createPromotionMove(game, start, end, captured, new Queen(isWhite)));
        moves.add(createPromotionMove(game, start, end, captured, new Rook(isWhite)));
        moves.add(createPromotionMove(game, start, end, captured, new Bishop(isWhite)));
        moves.add(createPromotionMove(game, start, end, captured, new Knight(isWhite)));
    }
}