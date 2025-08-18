package org.mcp;

import javax.swing.*;
import java.awt.*;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.BiConsumer;

public class TicTacToeUI extends JFrame {
    private final JButton[][] cells = new JButton[Board.SIZE][Board.SIZE];
    private final JLabel status = new JLabel("Connecting...");
    private final JProgressBar thinking = new JProgressBar(); // spinner
    private boolean enabledForMove = false;
    private BiConsumer<Integer, Integer> onMove;

    // win animation
    private Timer flashTimer;
    private int flashCount = 0;
    private int[][] lastWinLine;

    public TicTacToeUI(String title) {
        super(title == null ? "Gomoku 10×10 — Five-in-a-Row" : title);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        JPanel grid = new JPanel(new GridLayout(Board.SIZE, Board.SIZE, 2, 2));
        Font f = new Font(Font.SANS_SERIF, Font.BOLD, 22);
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

        JPanel south = new JPanel(new BorderLayout());
        status.setHorizontalAlignment(SwingConstants.CENTER);
        south.add(status, BorderLayout.CENTER);

        thinking.setIndeterminate(true);
        thinking.setVisible(false);
        south.add(thinking, BorderLayout.SOUTH);

        add(south, BorderLayout.SOUTH);

        setSize(720, 800);
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
            JButton btn = cells[i / Board.SIZE][i % Board.SIZE];
            btn.setText(ch == '.' ? "" : String.valueOf(ch));
            // Reset cell background if a previous animation changed it
            btn.setBackground(null);
            btn.setOpaque(false);
        }
        // clear any previous animation
        stopWinAnimation();
        lastWinLine = null;
    }

    public void setStatus(String text) { status.setText(text); }

    public void enableMove(boolean enable) { enabledForMove = enable; }

    /** Show/hide bot thinking spinner */
    public void setThinking(boolean on) {
        thinking.setVisible(on);
        repaint();
    }

    /** Provide win line for celebration (five cells). */
    public void showWinLine(int[][] line) {
        if (line == null || line.length != Board.WIN_LEN) return;
        lastWinLine = line;
        startWinAnimation();
    }

    private void startWinAnimation() {
        stopWinAnimation();
        flashCount = 0;
        flashTimer = new Timer("win-flash", true);
        flashTimer.scheduleAtFixedRate(new TimerTask() {
            private boolean on = false;
            @Override public void run() {
                on = !on;
                flashCount++;
                for (int[] rc : lastWinLine) {
                    int r = rc[0], c = rc[1];
                    JButton b = cells[r][c];
                    b.setOpaque(true);
                    b.setBackground(on ? new Color(255, 230, 0) : new Color(255, 120, 0));
                }
                if (flashCount >= 10) { // ~5 on/off cycles
                    stopWinAnimation();
                }
            }
        }, 0, 200);
    }

    private void stopWinAnimation() {
        if (flashTimer != null) {
            flashTimer.cancel();
            flashTimer = null;
        }
    }
}
