package org.mcp;

import javax.swing.*;
import java.awt.*;
import java.util.function.BiConsumer;

public class TicTacToeUI extends JFrame {
    private final JButton[][] cells = new JButton[Board.SIZE][Board.SIZE];
    private final JLabel status = new JLabel("Connecting...");
    private boolean enabledForMove = false;
    private BiConsumer<Integer, Integer> onMove;

    public TicTacToeUI(String title) {
        super(title == null ? "Gomoku 10×10 — Five-in-a-Row" : title);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        JPanel grid = new JPanel(new GridLayout(Board.SIZE, Board.SIZE, 2, 2));
        Font f = new Font(Font.SANS_SERIF, Font.BOLD, 22); // smaller font for 10x10
        for (int r = 0; r < Board.SIZE; r++) {
            for (int c = 0; c < Board.SIZE; c++) {
                final int rr = r, cc = c;
                JButton b = new JButton("");
                b.setFont(f);
                b.addActionListener(e -> {
                    if (enabledForMove && onMove != null) onMove.accept(rr, cc);
                });
                cells[r][c] = b;
                grid.add(b);
            }
        }
        add(grid, BorderLayout.CENTER);

        status.setHorizontalAlignment(SwingConstants.CENTER);
        add(status, BorderLayout.SOUTH);

        setSize(700, 760);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    public void setOnMove(BiConsumer<Integer, Integer> onMove) {
        this.onMove = onMove;
    }

    public void setBoardFromString(String s) {
        if (s == null || s.length() != Board.SIZE * Board.SIZE) return;
        for (int i = 0; i < Board.SIZE * Board.SIZE; i++) {
            char ch = s.charAt(i);
            cells[i / Board.SIZE][i % Board.SIZE].setText(ch == '.' ? "" : String.valueOf(ch));
        }
    }

    public void setStatus(String text) { status.setText(text); }

    public void enableMove(boolean enable) { enabledForMove = enable; }
}
