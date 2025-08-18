package org.mcp;

import java.util.Arrays;

public class Board {
    private final char[][] b = new char[3][3];

    public Board() { reset(); }

    public synchronized void reset() {
        for (char[] row : b) Arrays.fill(row, '.');
    }

    public synchronized boolean play(int r, int c, char p) {
        if (r < 0 || r > 2 || c < 0 || c > 2) return false;
        if (b[r][c] != '.') return false;
        b[r][c] = p;
        return true;
    }

    public synchronized boolean isFull() {
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 3; c++)
                if (b[r][c] == '.') return false;
        return true;
    }

    public synchronized char winner() {
        // rows and cols
        for (int i = 0; i < 3; i++) {
            if (b[i][0] != '.' && b[i][0] == b[i][1] && b[i][1] == b[i][2]) return b[i][0];
            if (b[0][i] != '.' && b[0][i] == b[1][i] && b[1][i] == b[2][i]) return b[0][i];
        }
        // diagonals
        if (b[1][1] != '.' && ((b[0][0] == b[1][1] && b[1][1] == b[2][2]) ||
                (b[0][2] == b[1][1] && b[1][1] == b[2][0]))) {
            return b[1][1];
        }
        return '.';
    }

    public synchronized String serialize() {
        StringBuilder sb = new StringBuilder(9);
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 3; c++)
                sb.append(b[r][c]);
        return sb.toString();
    }

    public synchronized void deserialize(String s) {
        if (s == null || s.length() != 9) return;
        for (int i = 0; i < 9; i++) b[i/3][i%3] = s.charAt(i);
    }

    public synchronized char get(int r, int c) { return b[r][c]; }
}

