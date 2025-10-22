package org.pocketchess.ui.gameframepack.piecesandclock;

import org.pocketchess.core.game.TimeControl;
import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.TreeMap;

/**
 * Dialog for selecting time control before starting a game
 */
public class TimeSetupDialog extends JDialog {
    private TimeControl selectedControl = null;
    private boolean confirmed = false;

    // Predefined time options
    private static final Map<Integer, String> TIME_STEPS = new TreeMap<>();
    private static final Map<Integer, String> INCREMENT_STEPS = new TreeMap<>();
    private final Integer[] timeKeys;
    private final Integer[] incrementKeys;

    // Static initialization - populate time options
    static {
        // Base time options
        TIME_STEPS.put(10, "10 sec");
        TIME_STEPS.put(30, "30 sec");
        TIME_STEPS.put(60, "1 min");
        TIME_STEPS.put(90, "1.5 min");
        TIME_STEPS.put(120, "2 min");
        TIME_STEPS.put(180, "3 min");
        TIME_STEPS.put(300, "5 min");
        TIME_STEPS.put(420, "7 min");
        TIME_STEPS.put(600, "10 min");
        TIME_STEPS.put(900, "15 min");
        TIME_STEPS.put(1200, "20 min");
        TIME_STEPS.put(1800, "30 min");
        TIME_STEPS.put(3600, "1 hour");
        TIME_STEPS.put(5400, "1.5 hour");
        TIME_STEPS.put(7200, "2 hour");
        TIME_STEPS.put(10800, "3 hour");

        // Increment options
        INCREMENT_STEPS.put(0, "+0 sec");
        INCREMENT_STEPS.put(1, "+1 sec");
        INCREMENT_STEPS.put(2, "+2 sec");
        INCREMENT_STEPS.put(3, "+3 sec");
        for (int i = 4; i <= 10; i++) INCREMENT_STEPS.put(i, "+" + i + " sec");
        INCREMENT_STEPS.put(15, "+15 sec");
        INCREMENT_STEPS.put(20, "+20 sec");
        INCREMENT_STEPS.put(30, "+30 sec");
        INCREMENT_STEPS.put(40, "+40 sec");
        INCREMENT_STEPS.put(50, "+50 sec");
        INCREMENT_STEPS.put(60, "+1 min");
        INCREMENT_STEPS.put(90, "+1.5 min");
        INCREMENT_STEPS.put(120, "+2 min");
        INCREMENT_STEPS.put(180, "+3 min");
    }

    private final JSlider timeSlider;
    private final JSlider incrementSlider;
    private final JLabel timeLabel;
    private final JLabel incrementLabel;
    private final JCheckBox unlimitedCheckBox;

    /**
     * Creates a new time setup dialog.
     */
    public TimeSetupDialog(JFrame parent) {
        super(parent, "Time setup", true);  // Modal dialog

        // Convert maps to arrays for slider indexing
        timeKeys = TIME_STEPS.keySet().toArray(new Integer[0]);
        incrementKeys = INCREMENT_STEPS.keySet().toArray(new Integer[0]);

        setLayout(new BorderLayout(10, 10));
        setResizable(false);

        // Create main panel with sliders
        JPanel slidersPanel = new JPanel(new GridBagLayout());
        slidersPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // TIME SLIDER
        timeSlider = new JSlider(0, timeKeys.length - 1, 6);
        timeLabel = new JLabel("", SwingConstants.CENTER);
        timeLabel.setPreferredSize(new Dimension(60, 20));
        gbc.gridx = 0; gbc.gridy = 0; slidersPanel.add(new JLabel("Time:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; slidersPanel.add(timeSlider, gbc);
        gbc.gridx = 2; gbc.weightx = 0; slidersPanel.add(timeLabel, gbc);

        // INCREMENT SLIDER
        incrementSlider = new JSlider(0, incrementKeys.length - 1, 3);
        incrementLabel = new JLabel("", SwingConstants.CENTER);
        incrementLabel.setPreferredSize(new Dimension(60, 20));
        gbc.gridx = 0; gbc.gridy = 1; slidersPanel.add(new JLabel("Increment:"), gbc);
        gbc.gridx = 1; slidersPanel.add(incrementSlider, gbc);
        gbc.gridx = 2; slidersPanel.add(incrementLabel, gbc);

        // UNLIMITED CHECKBOX
        unlimitedCheckBox = new JCheckBox("Unlimited time");
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 3; slidersPanel.add(unlimitedCheckBox, gbc);

        // BUTTONS
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Cancel");
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);

        // Layout
        add(new JLabel("Choose time control", SwingConstants.CENTER), BorderLayout.NORTH);
        add(slidersPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        // Event listeners
        timeSlider.addChangeListener(e -> updateLabels());
        incrementSlider.addChangeListener(e -> updateLabels());
        unlimitedCheckBox.addActionListener(e -> toggleSliders());
        okButton.addActionListener(e -> onConfirm());
        cancelButton.addActionListener(e -> dispose());

        updateLabels();  // Initialize label text
        pack();
        setLocationRelativeTo(parent);
    }

    /**
     * Updates the time labels based on slider positions.
     */
    private void updateLabels() {
        int timeIndex = timeSlider.getValue();
        int incrementIndex = incrementSlider.getValue();
        timeLabel.setText(TIME_STEPS.get(timeKeys[timeIndex]));
        incrementLabel.setText(INCREMENT_STEPS.get(incrementKeys[incrementIndex]));
    }

    /**
     * Enables/disables sliders based on unlimited checkbox.
     */
    private void toggleSliders() {
        boolean enabled = !unlimitedCheckBox.isSelected();
        timeSlider.setEnabled(enabled);
        incrementSlider.setEnabled(enabled);
        timeLabel.setEnabled(enabled);
        incrementLabel.setEnabled(enabled);
    }

    /**
     * Handles OK button click - creates TimeControl and closes dialog.
     */
    private void onConfirm() {
        if (unlimitedCheckBox.isSelected()) {
            selectedControl = TimeControl.UNLIMITED;
        } else {
            int timeValue = timeKeys[timeSlider.getValue()];
            int incrementValue = incrementKeys[incrementSlider.getValue()];
            selectedControl = new TimeControl(timeValue, incrementValue);
        }
        confirmed = true;
        dispose();
    }

    /**
     * Shows the dialog and returns the selected time control.
     */
    public static TimeControl showDialog(JFrame parent) {
        TimeSetupDialog dialog = new TimeSetupDialog(parent);
        dialog.setVisible(true);  // Blocks until dialog closes
        return dialog.confirmed ? dialog.selectedControl : null;
    }
}