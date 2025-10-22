package org.pocketchess.ui.gameframepack.notation;

import org.pocketchess.core.game.gamenotation.NotationProvider;
import org.pocketchess.core.game.moveanalyze.Move;
import org.pocketchess.core.general.Game;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * Manages the move history table displayed in the UI.
 * Shows all moves in chess notation in a table format
 */
public class MoveHistoryManager {
    private final JTable moveHistoryTable;          // The visual table component
    private final DefaultTableModel moveTableModel;  // The data model for the table
    private final Game game;                         // The game state
    private final NotationProvider notationProvider; // Converts moves to notation
    private final Runnable updateUICallback;         // Called when UI needs to update

    /**
     * Creates a new move history manager.
     */
    public MoveHistoryManager(Game game, NotationProvider notationProvider, Runnable updateUICallback) {
        this.game = game;
        this.notationProvider = notationProvider;
        this.updateUICallback = updateUICallback;

        String[] columnNames = {"#", "White", "Black"};
        moveTableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        moveHistoryTable = new JTable(moveTableModel);
        setupTable();
    }

    /**
     * Configures the table's appearance and behavior.
     */
    private void setupTable() {

        moveHistoryTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        moveHistoryTable.setCellSelectionEnabled(true);

        moveHistoryTable.setFont(new Font("Arial", Font.PLAIN, 14));
        moveHistoryTable.setShowGrid(false);
        moveHistoryTable.setIntercellSpacing(new Dimension(0, 0));

        moveHistoryTable.getColumnModel().getColumn(0).setMaxWidth(40);

        moveHistoryTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                int selectedRow = moveHistoryTable.rowAtPoint(evt.getPoint());
                int selectedCol = moveHistoryTable.columnAtPoint(evt.getPoint());


                if (selectedRow != -1 && selectedCol > 0) {
                    Object moveText = moveHistoryTable.getValueAt(selectedRow, selectedCol);

                    if (moveText != null && !moveText.toString().isEmpty()) {

                        int moveIndex = (selectedRow * 2) + (selectedCol - 1);

                        if (moveIndex < game.getMoveHistory().size() &&
                                moveIndex != game.getCurrentMoveIndex()) {
                            game.goToMove(moveIndex);
                            updateUICallback.run();
                        }
                    }
                }
            }
        });
    }

    /**
     * Creates a scroll pane containing the move history table.
     */
    public JScrollPane createScrollPane() {
        JScrollPane scrollPane = new JScrollPane(moveHistoryTable);
        scrollPane.setPreferredSize(new Dimension(180, 0));
        return scrollPane;
    }

    /**
     * Updates the move history table to reflect the current game state.
     */
    public void updateMoveHistory() {

        moveTableModel.setRowCount(0);

        List<Move> history = game.getMoveHistory();


        for (int i = 0; i < history.size(); i += 2) {

            String moveNum = (i / 2 + 1) + ".";

            String whiteMove = notationProvider.getNotationForMove(history.get(i));

            String blackMove = (i + 1 < history.size())
                    ? notationProvider.getNotationForMove(history.get(i + 1))
                    : "";

            moveTableModel.addRow(new Object[]{moveNum, whiteMove, blackMove});
        }

        int currentIndex = game.getCurrentMoveIndex();
        if (currentIndex >= 0) {

            int targetRow = currentIndex / 2;
            int targetCol = (currentIndex % 2) + 1;

            if (moveHistoryTable.getSelectedRow() != targetRow ||
                    moveHistoryTable.getSelectedColumn() != targetCol) {
                if (targetRow < moveHistoryTable.getRowCount()) {
                    moveHistoryTable.changeSelection(targetRow, targetCol, false, false);
                }
            }
        } else {
            moveHistoryTable.clearSelection();
        }
    }
}