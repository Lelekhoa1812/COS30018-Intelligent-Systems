package org.mcp;

import javax.swing.*;
import java.awt.*;
import java.util.function.BiConsumer;

public class TicTacToeUI extends JFrame {
    private final JButton[][] cells = new JButton[3][3];
    private final JLabel status = new JLabel("Connecting...");
    private boolean enabledForMove = false;
    private BiConsumer<Integer, Integer> onMove;

    public TicTacToeUI() {
        super("Tic-Tac-Toe — Human vs Gemini");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        JPanel grid = new JPanel(new GridLayout(3,3,4,4));
        Font f = new Font(Font.SANS_SERIF, Font.BOLD, 42);
        for (int r=0;r<3;r++){
            for (int c=0;c<3;c++){
                int rr=r, cc=c;
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

        setSize(360, 420);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    public void setOnMove(BiConsumer<Integer, Integer> onMove) {
        this.onMove = onMove;
    }

    public void setBoardFromString(String s) {
        for (int i=0;i<9;i++){
            char ch = s.charAt(i);
            cells[i/3][i%3].setText(ch == '.' ? "" : String.valueOf(ch));
        }
    }

    public void setStatus(String text) { status.setText(text); }

    public void enableMove(boolean enable) { enabledForMove = enable; }
}
