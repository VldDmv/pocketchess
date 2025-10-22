package org.pocketchess.ui.boardpack;

import org.pocketchess.core.game.GameStatus;
import org.pocketchess.core.general.Game;
import org.pocketchess.core.pieces.Piece;
import org.pocketchess.core.pieces.Spot;
import org.pocketchess.ui.gameframepack.GameFrame;
import org.pocketchess.ui.gameframepack.piecesandclock.ImageLoader;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Handles mouse input for the chess board.
 */
public class BoardMouseHandler extends MouseAdapter {
    private final GameFrame gameFrame;
    private final Game game;
    private final BoardCoordinateHelper coordHelper;
    private final BoardState boardState;
    private final Runnable repaintCallback;
    private final int tileSize;

    public BoardMouseHandler(GameFrame gameFrame, Game game, BoardCoordinateHelper coordHelper,
                             BoardState boardState, Runnable repaintCallback, int tileSize) {
        this.gameFrame = gameFrame;
        this.game = game;
        this.coordHelper = coordHelper;
        this.boardState = boardState;
        this.repaintCallback = repaintCallback;
        this.tileSize = tileSize;
    }

    /**
     * Mouse pressed - start of interaction.
     */
    @Override
    public void mousePressed(MouseEvent e) {
        // Only handle left mouse button
        if (!SwingUtilities.isLeftMouseButton(e) || isGameEnded() || game.isAIsTurn()) {
            return;
        }

        // Convert pixel coordinates to logical board coordinates
        int[] logicalCoords = coordHelper.displayToLogical(e.getX() / tileSize, e.getY() / tileSize);
        if (coordHelper.isValidPosition(logicalCoords[0], logicalCoords[1])) {
            return;
        }

        // Record the piece we clicked on
        boardState.sourceSpot = game.getBoard().getBox(logicalCoords[0], logicalCoords[1]);
        boardState.draggedPiece = boardState.sourceSpot.getPiece();
    }

    /**
     * Mouse dragged - user is dragging a piece.
     */
    @Override
    public void mouseDragged(MouseEvent e) {
        if (!SwingUtilities.isLeftMouseButton(e) || boardState.draggedPiece == null) {
            return;
        }

        // Only drag your own pieces during your turn
        if (boardState.draggedPiece.isWhite() == game.isWhiteTurn()) {
            if (!boardState.isDragging) {
                startDragging();
            }

            boardState.dragX = e.getX() - tileSize / 2;
            boardState.dragY = e.getY() - tileSize / 2;
            repaintCallback.run();
        }
    }

    /**
     * Mouse released - end of interaction.
     */
    @Override
    public void mouseReleased(MouseEvent e) {
        if (!SwingUtilities.isLeftMouseButton(e)) {
            return;
        }

        if (isGameEnded()) {
            cancelDragIfNeeded();
            resetBoardState();
            gameFrame.updateUI();
            return;
        }

        boolean moveSuccessful;

        if (boardState.isDragging) {
            moveSuccessful = handleDragRelease(e);
        } else {
            moveSuccessful = handleClickMove();
        }

        // If move successful, play sound and handle pawn promotion
        if (moveSuccessful) {
            gameFrame.playSoundForLastMove();
            if (game.getStatus() == GameStatus.AWAITING_PROMOTION) {
                PawnPromotionDialog.showAndPromote(game, gameFrame, null);
            }
        }

        resetBoardState();
        gameFrame.updateUI();
    }

    /**
     * Initializes drag mode.
     */
    private void startDragging() {
        boardState.isDragging = true;
        boardState.selectedSpot = null;
        boardState.dragHighlightSpot = boardState.sourceSpot;
        boardState.sourceSpot.setPiece(null); // Remove from board
        boardState.draggedPieceImage = ImageLoader.getImageForPiece(boardState.draggedPiece);
    }

    /**
     * Handles releasing piece after dragging.
     */
    private boolean handleDragRelease(MouseEvent e) {
        boardState.sourceSpot.setPiece(boardState.draggedPiece);

        int[] logicalCoords = coordHelper.displayToLogical(e.getX() / tileSize, e.getY() / tileSize);
        if (coordHelper.isValidPosition(logicalCoords[0], logicalCoords[1])) {
            return false; // Dropped outside board
        }

        Spot targetSpot = game.getBoard().getBox(logicalCoords[0], logicalCoords[1]);
        if (game.isMoveLegal(boardState.sourceSpot, targetSpot)) {
            return game.playerMove(
                    boardState.sourceSpot.getX(),
                    boardState.sourceSpot.getY(),
                    logicalCoords[0],
                    logicalCoords[1]
            );
        }
        return false;
    }

    /**
     * Handles click-to-move interaction.
     */
    private boolean handleClickMove() {
        if (boardState.sourceSpot == null) {
            return false;
        }

        Piece clickedPiece = boardState.sourceSpot.getPiece();

        // First click: select piece
        if (boardState.selectedSpot == null) {
            if (clickedPiece != null && clickedPiece.isWhite() == game.isWhiteTurn()) {
                boardState.selectedSpot = boardState.sourceSpot;
            }
            return false;
        }

        // Click on same piece: deselect
        if (boardState.sourceSpot == boardState.selectedSpot) {
            boardState.selectedSpot = null;
            return false;
        }

        // Click on legal destination: execute move
        if (game.isMoveLegal(boardState.selectedSpot, boardState.sourceSpot)) {
            boolean success = game.playerMove(
                    boardState.selectedSpot.getX(),
                    boardState.selectedSpot.getY(),
                    boardState.sourceSpot.getX(),
                    boardState.sourceSpot.getY()
            );
            boardState.selectedSpot = null;
            return success;
        }

        // Click on another piece → switch selection
        if (clickedPiece != null && clickedPiece.isWhite() == game.isWhiteTurn()) {
            boardState.selectedSpot = boardState.sourceSpot;
        } else {
            boardState.selectedSpot = null;
        }

        return false;
    }

    /**
     * Cancels drag if game ended mid-drag.
     */
    private void cancelDragIfNeeded() {
        if (boardState.isDragging) {
            boardState.sourceSpot.setPiece(boardState.draggedPiece);
        }
    }


    private void resetBoardState() {
        boardState.isDragging = false;
        boardState.draggedPiece = null;
        boardState.draggedPieceImage = null;
        boardState.sourceSpot = null;
        boardState.dragHighlightSpot = null;
    }


    private boolean isGameEnded() {
        GameStatus status = game.getStatus();
        return status == GameStatus.WHITE_WIN ||
                status == GameStatus.BLACK_WIN ||
                status == GameStatus.STALEMATE ||
                status == GameStatus.WHITE_WIN_ON_TIME ||
                status == GameStatus.BLACK_WIN_ON_TIME ||
                status == GameStatus.DRAW_50_MOVES ||
                status == GameStatus.DRAW_AGREED ||
                status == GameStatus.WHITE_WINS_BY_RESIGNATION ||
                status == GameStatus.BLACK_WINS_BY_RESIGNATION ||
                status == GameStatus.DRAW_THREEFOLD_REPETITION;
    }
}