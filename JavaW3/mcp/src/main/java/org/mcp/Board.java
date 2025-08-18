package org.mcp;

import java.util.Arrays;

public class Board {
    public static final int SIZE = 10;      // 10x10 grid
    public static final int WIN_LEN = 5;    // 5-in-a-row to win

    private final char[][] g = new char[SIZE][SIZE];

    public Board() { reset(); }

    public synchronized void reset() {
        for (char[] row : g) Arrays.fill(row, '.');
    }

    public synchronized boolean play(int r, int c, char p) {
        if (!inBounds(r, c)) return false;
        if (g[r][c] != '.') return false;
        g[r][c] = p;
        return true;
    }

    public synchronized boolean isFull() {
        for (int r = 0; r < SIZE; r++)
            for (int c = 0; c < SIZE; c++)
                if (g[r][c] == '.') return false;
        return true;
    }

    public synchronized char winner() {
        // Check all cells as possible starts
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                char p = g[r][c];
                if (p == '.') continue;
                // Right →
                if (c + WIN_LEN - 1 < SIZE && run(r, c, 0, 1, p)) return p;
                // Down ↓
                if (r + WIN_LEN - 1 < SIZE && run(r, c, 1, 0, p)) return p;
                // Down-right ↘
                if (r + WIN_LEN - 1 < SIZE && c + WIN_LEN - 1 < SIZE && run(r, c, 1, 1, p)) return p;
                // Down-left ↙
                if (r + WIN_LEN - 1 < SIZE && c - WIN_LEN + 1 >= 0 && run(r, c, 1, -1, p)) return p;
            }
        }
        return '.';
    }

    private boolean run(int r, int c, int dr, int dc, char p) {
        for (int k = 0; k < WIN_LEN; k++) {
            if (g[r + k*dr][c + k*dc] != p) return false;
        }
        return true;
    }

    private boolean inBounds(int r, int c) {
        return r >= 0 && r < SIZE && c >= 0 && c < SIZE;
    }

    public synchronized String serialize() {
        StringBuilder sb = new StringBuilder(SIZE * SIZE);
        for (int r = 0; r < SIZE; r++)
            for (int c = 0; c < SIZE; c++)
                sb.append(g[r][c]);
        return sb.toString();
    }

    public synchronized void deserialize(String s) {
        if (s == null || s.length() != SIZE * SIZE) return;
        for (int i = 0; i < SIZE * SIZE; i++) {
            g[i / SIZE][i % SIZE] = s.charAt(i);
        }
    }

    public synchronized char get(int r, int c) { return g[r][c]; }
}
