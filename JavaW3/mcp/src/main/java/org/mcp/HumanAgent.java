package org.mcp;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

import javax.swing.*;

public class HumanAgent extends Agent {
    private TicTacToeUI ui;
    private String mySymbol = "X";

    @Override
    protected void setup() {
        SwingUtilities.invokeLater(() -> {
            ui = new TicTacToeUI();
            ui.setOnMove((r, c) -> {
                ACLMessage m = new ACLMessage(ACLMessage.INFORM);
                m.addReceiver(getAID("game")); // GameMaster local name = "game"
                m.setContent("MOVE " + r + " " + c);
                send(m);
            });
        });

        // register with game
        ACLMessage reg = new ACLMessage(ACLMessage.INFORM);
        reg.addReceiver(getAID("game"));
        reg.setContent("REGISTER HUMAN");
        send(reg);

        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg == null) { block(); return; }
                String c = msg.getContent();

                if (c != null && c.startsWith("REQUEST_MOVE")) {
                    String[] parts = c.split("\\s+");
                    String board = parts[1];
                    String turn = parts[2];
                    String note = (parts.length > 3) ? parts[3] : "";
                    if (ui != null) SwingUtilities.invokeLater(() -> {
                        ui.setBoardFromString(board);
                        ui.setStatus("Your turn (X). " + note);
                        ui.enableMove("X".equals(turn));
                    });
                } else if (c != null && c.startsWith("STATE")) {
                    String[] parts = c.split("\\s+");
                    String board = parts[1];
                    String status = parts[2];
                    if (ui != null) SwingUtilities.invokeLater(() -> {
                        ui.setBoardFromString(board);
                        switch (status) {
                            case "NEW" -> ui.setStatus("New game started");
                            case "DRAW" -> { ui.setStatus("Draw!"); ui.enableMove(false); }
                            case "PLAYING" -> ui.setStatus("Playing...");
                            default -> {
                                if (status.startsWith("WIN")) {
                                    String w = status.split("\\s+")[1];
                                    ui.setStatus("Winner: " + w);
                                    ui.enableMove(false);
                                }
                            }
                        }
                    });
                }
            }
        });
    }
}
