package org.mcp;

import java.awt.*;
import java.util.Arrays;

public class Board {
    public static final int SIZE = 10;      // 10x10 grid
    public static final int WIN_LEN = 5;    // 5-in-a-row to win

    private final char[][] g = new char[SIZE][SIZE];
    private int[][] lastWinLine = null; // 5 cells [ [r,c], ... ]

    public Board() { reset(); }

    public synchronized void reset() {
        for (char[] row : g) Arrays.fill(row, '.');
        lastWinLine = null;
    }

    public synchronized boolean play(int r, int c, char p) {
        if (!inBounds(r, c)) return false;
        if (g[r][c] != '.') return false;
        g[r][c] = p;
        lastWinLine = null; // reset until computed again
        return true;
    }

    public synchronized boolean isFull() {
        for (int r = 0; r < SIZE; r++)
            for (int c = 0; c < SIZE; c++)
                if (g[r][c] == '.') return false;
        return true;
    }

    public synchronized char winner() {
        lastWinLine = null;
        // Check all cells as possible starts
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                char p = g[r][c];
                if (p == '.') continue;
                // Right →
                if (c + WIN_LEN - 1 < SIZE && runAndCapture(r, c, 0, 1, p)) return p;
                // Down ↓
                if (r + WIN_LEN - 1 < SIZE && runAndCapture(r, c, 1, 0, p)) return p;
                // Down-right ↘
                if (r + WIN_LEN - 1 < SIZE && c + WIN_LEN - 1 < SIZE && runAndCapture(r, c, 1, 1, p)) return p;
                // Down-left ↙
                if (r + WIN_LEN - 1 < SIZE && c - WIN_LEN + 1 >= 0 && runAndCapture(r, c, 1, -1, p)) return p;
            }
        }
        return '.';
    }

    /** Returns the last computed winning line (5 cells) or null. */
    public synchronized int[][] getLastWinLine() {
        if (lastWinLine == null) return null;
        int[][] copy = new int[WIN_LEN][2];
        for (int i = 0; i < WIN_LEN; i++) { copy[i][0] = lastWinLine[i][0]; copy[i][1] = lastWinLine[i][1]; }
        return copy;
    }

    private boolean runAndCapture(int r, int c, int dr, int dc, char p) {
        for (int k = 0; k < WIN_LEN; k++) {
            if (g[r + k*dr][c + k*dc] != p) return false;
        }
        lastWinLine = new int[WIN_LEN][2];
        for (int k = 0; k < WIN_LEN; k++) {
            lastWinLine[k][0] = r + k*dr;
            lastWinLine[k][1] = c + k*dc;
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
        lastWinLine = null;
    }

    public synchronized char get(int r, int c) { return g[r][c]; }
}
