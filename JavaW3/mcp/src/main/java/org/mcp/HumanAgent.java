package org.mcp;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

import javax.swing.*;

public class HumanAgent extends Agent {
    private TicTacToeUI ui;
    private String mySymbol = "?";
    private String displayName = "Human";

    @Override
    protected void setup() {
        // Optional label from args (e.g., "Player 1" / "Player 2")
        Object[] args = getArguments();
        if (args != null && args.length > 0 && args[0] != null) {
            displayName = String.valueOf(args[0]);
        }
        final String windowTitle = "Gomoku 10×10 — " + displayName;

        SwingUtilities.invokeLater(() -> {
            ui = new TicTacToeUI(windowTitle);
            ui.setOnMove((r, c) -> {
                ACLMessage m = new ACLMessage(ACLMessage.INFORM);
                m.addReceiver(new AID("game", AID.ISLOCALNAME));
                m.setContent("MOVE " + r + " " + c);
                send(m);
            });
        });

        // register with game
        ACLMessage reg = new ACLMessage(ACLMessage.INFORM);
        reg.addReceiver(new AID("game", AID.ISLOCALNAME));
        reg.setContent("REGISTER HUMAN");
        send(reg);

        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg == null) { block(); return; }
                String c = msg.getContent();
                if (c == null) return;

                if (c.startsWith("ASSIGN")) {
                    // ASSIGN X|O
                    String[] parts = c.split("\\s+");
                    if (parts.length >= 2) {
                        mySymbol = parts[1];
                        if (ui != null) SwingUtilities.invokeLater(() ->
                                ui.setStatus("Assigned as " + mySymbol + ". Waiting to start..."));
                    }
                } else if (c.startsWith("REQUEST_MOVE")) {
                    // REQUEST_MOVE board turn note
                    String[] parts = c.split("\\s+", 4);
                    String board = parts[1];
                    String turn = parts[2];
                    String note = (parts.length > 3) ? parts[3].replace('_', ' ') : "";

                    if (ui != null) SwingUtilities.invokeLater(() -> {
                        ui.setBoardFromString(board);
                        ui.setStatus(displayName + " (" + mySymbol + ") — " + note);
                        ui.enableMove(mySymbol.equals(turn));
                    });
                } else if (c.startsWith("STATE")) {
                    String[] parts = c.split("\\s+");
                    String board = parts[1];
                    String status = parts[2];
                    if (ui != null) SwingUtilities.invokeLater(() -> {
                        ui.setBoardFromString(board);
                        switch (status) {
                            case "NEW" -> ui.setStatus("New game started. You are " + mySymbol);
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
