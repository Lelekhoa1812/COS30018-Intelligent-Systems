package org.mcp;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import java.io.FileWriter;

public class GameAgent extends Agent {
    private char[] board = {'1','2','3','4','5','6','7','8','9'};
    private boolean isPlayerTurn = true;

    protected void setup() {
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
                    // Request AI move
                    ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
                    request.addReceiver(getAID("gemini@platform"));
                    request.setContent(getBoardState());
                    send(request);
                    
                    ACLMessage reply = blockingReceive();
                    processAIMove(reply.getContent());
                    isPlayerTurn = true;
                    updateBoard();
                }
            }
        });
    }

    private void processPlayerMove(String input) {
        int pos = Integer.parseInt(input) - 1;
        if (board[pos] != 'X' && board[pos] != 'O') {
            board[pos] = 'X';
        }
    }

    private void processAIMove(String move) {
        int pos = Integer.parseInt(move.trim()) - 1;
        board[pos] = 'O';
    }

    private String getBoardState() {
        return String.format("""
            %c | %c | %c
            ---------
            %c | %c | %c
            ---------
            %c | %c | %c""", board);
    }

    private void updateBoard() {
        try (FileWriter writer = new FileWriter("resources/board.txt")) {
            writer.write(getBoardState());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}