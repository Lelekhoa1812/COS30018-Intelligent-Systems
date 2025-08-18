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
                    String[] parts = c.split("\\s+");
                    if (parts.length >= 2) {
                        mySymbol = parts[1];
                        if (ui != null) SwingUtilities.invokeLater(() ->
                                ui.setStatus("Assigned as " + mySymbol + ". Waiting to start..."));
                    }
                } else if (c.startsWith("REQUEST_MOVE")) {
                    String[] parts = c.split("\\s+", 4);
                    String board = parts[1];
                    String turn = parts[2];
                    String note = (parts.length > 3) ? parts[3].replace('_', ' ') : "";

                    if (ui != null) SwingUtilities.invokeLater(() -> {
                        ui.setBoardFromString(board);
                        ui.setStatus(displayName + " (" + mySymbol + ") — " + note);
                        ui.enableMove(mySymbol.equals(turn));
                        ui.setThinking(false); // never show spinner for a human's own turn
                    });
                } else if (c.startsWith("STATE")) {
                    // Possible formats:
                    // STATE <board> PLAYING
                    // STATE <board> NEW
                    // STATE <board> DRAW
                    // STATE <board> WIN X [LINE r1 c1 r2 c2 r3 c3 r4 c4 r5 c5]
                    String[] parts = c.split("\\s+");
                    String board = parts[1];
                    String status = parts[2];

                    if (ui != null) SwingUtilities.invokeLater(() -> {
                        ui.setBoardFromString(board);
                        switch (status) {
                            case "NEW" -> ui.setStatus("New game started. You are " + mySymbol);
                            case "DRAW" -> { ui.setStatus("Draw!"); ui.enableMove(false); ui.setThinking(false); }
                            case "PLAYING" -> ui.setStatus("Playing...");
                            default -> {
                                if (status.startsWith("WIN")) {
                                    String w = status.split("\\s+")[1];
                                    ui.setStatus("Winner: " + w);
                                    ui.enableMove(false);
                                    ui.setThinking(false);
                                    // Look for LINE payload
                                    int idx = c.indexOf(" LINE");
                                    if (idx >= 0) {
                                        String tail = c.substring(idx + 5).trim();
                                        String[] nums = tail.split("\\s+");
                                        if (nums.length == 10) {
                                            int[][] line = new int[Board.WIN_LEN][2];
                                            for (int i = 0; i < Board.WIN_LEN; i++) {
                                                line[i][0] = Integer.parseInt(nums[i*2]);
                                                line[i][1] = Integer.parseInt(nums[i*2+1]);
                                            }
                                            ui.showWinLine(line);
                                        }
                                    }
                                }
                            }
                        }
                    });
                } else if (c.equals("THINK START")) {
                    if (ui != null) SwingUtilities.invokeLater(() -> ui.setThinking(true));
                } else if (c.equals("THINK STOP")) {
                    if (ui != null) SwingUtilities.invokeLater(() -> ui.setThinking(false));
                }
            }
        });
    }
}
