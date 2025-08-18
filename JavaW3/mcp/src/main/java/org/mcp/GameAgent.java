package org.mcp;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

import java.io.FileWriter;
import java.util.Arrays;

public class GameAgent extends Agent {
    private static final int SIZE = 10;
    private final char[][] board = new char[SIZE][SIZE];
    private boolean isPlayerTurn = true;

    @Override
    protected void setup() {
        resetBoard();

        addBehaviour(new CyclicBehaviour(this) {
            public void action() {
                if (isPlayerTurn) {
                    // Wait for human input
                    ACLMessage msg = receive();
                    if (msg != null) {
                        processPlayerMove(msg.getContent());
                        isPlayerTurn = false;
                        updateBoard();
                    }
                } else {
                    // Ask Gemini AI for move
                    ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
                    request.addReceiver(getAID("gemini@platform"));
                    request.setContent(getBoardState());
                    send(request);

                    ACLMessage reply = blockingReceive();
                    if (reply != null) {
                        processAIMove(reply.getContent());
                    }
                    isPlayerTurn = true;
                    updateBoard();
                }
            }
        });
    }

    private void resetBoard() {
        for (char[] row : board) {
            Arrays.fill(row, '.');
        }
    }

    private void processPlayerMove(String input) {
        // Expect input as "row col"
        String[] parts = input.trim().split("\\s+");
        if (parts.length == 2) {
            int r = Integer.parseInt(parts[0]);
            int c = Integer.parseInt(parts[1]);
            if (inBounds(r, c) && board[r][c] == '.') {
                board[r][c] = 'X';
            }
        }
    }

    private void processAIMove(String move) {
        // Expect AI move as "row col"
        String[] parts = move.trim().split("\\s+");
        if (parts.length == 2) {
            int r = Integer.parseInt(parts[0]);
            int c = Integer.parseInt(parts[1]);
            if (inBounds(r, c) && board[r][c] == '.') {
                board[r][c] = 'O';
            }
        }
    }

    private boolean inBounds(int r, int c) {
        return r >= 0 && r < SIZE && c >= 0 && c < SIZE;
    }

    private String getBoardState() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                sb.append(board[r][c]);
                if (c < SIZE - 1) sb.append(" | ");
            }
            sb.append("\n");
            if (r < SIZE - 1) {
                sb.append("-".repeat(SIZE * 4 - 3)).append("\n");
            }
        }
        return sb.toString();
    }

    private void updateBoard() {
        try (FileWriter writer = new FileWriter("resources/board.txt")) {
            writer.write(getBoardState());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
