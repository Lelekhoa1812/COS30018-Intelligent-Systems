package org.mcp;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

import java.util.Random;

public class GameMasterAgent extends Agent {
    private final Board board = new Board();
    private AID human;
    private AID ai;
    private char current = 'X'; // Human=X, AI=O by default
    private final Random rnd = new Random();

    @Override
    protected void setup() {
        System.out.println(getLocalName() + " ready.");
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg == null) { block(); return; }
                String content = msg.getContent();

                if ("REGISTER HUMAN".equals(content)) {
                    human = msg.getSender();
                    System.out.println("Human registered: " + human.getLocalName());
                    tryStart();
                } else if ("REGISTER AI".equals(content)) {
                    ai = msg.getSender();
                    System.out.println("AI registered: " + ai.getLocalName());
                    tryStart();
                } else if (content != null && content.startsWith("MOVE")) {
                    // MOVE r c
                    String[] parts = content.split("\\s+");
                    int r = Integer.parseInt(parts[1]);
                    int c = Integer.parseInt(parts[2]);
                    char p = msg.getSender().equals(human) ? 'X' : 'O';

                    if (board.play(r, c, p)) {
                        broadcastState("PLAYING");
                        // Check end
                        char w = board.winner();
                        if (w != '.') {
                            broadcastState("WIN " + w);
                        } else if (board.isFull()) {
                            broadcastState("DRAW");
                        } else {
                            current = (current == 'X') ? 'O' : 'X';
                            requestMove();
                        }
                    } else {
                        // Invalid -> ask same player again
                        requestMoveTo(msg.getSender(), "Invalid move, try again.");
                    }
                }
            }
        });
    }

    private void tryStart() {
        if (human != null && ai != null) {
            board.reset();
            current = rnd.nextBoolean() ? 'X' : 'O'; // random first player
            broadcastState("NEW");
            requestMove();
        }
    }

    private void requestMove() {
        AID target = (current == 'X') ? human : ai;
        requestMoveTo(target, "Your turn (" + current + ")");
    }

    private void requestMoveTo(AID target, String note) {
        ACLMessage req = new ACLMessage(ACLMessage.REQUEST);
        req.addReceiver(target);
        req.setContent("REQUEST_MOVE " + board.serialize() + " " + current + " " + note);
        send(req);
    }

    private void broadcastState(String status) {
        String payload = "STATE " + board.serialize() + " " + status;
        if (human != null) {
            ACLMessage toHuman = new ACLMessage(ACLMessage.INFORM);
            toHuman.addReceiver(human);
            toHuman.setContent(payload);
            send(toHuman);
        }
        if (ai != null) {
            ACLMessage toAI = new ACLMessage(ACLMessage.INFORM);
            toAI.addReceiver(ai);
            toAI.setContent(payload);
            send(toAI);
        }
    }
}
