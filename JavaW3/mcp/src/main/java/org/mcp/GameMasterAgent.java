package org.mcp;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

import java.util.Random;

public class GameMasterAgent extends Agent {
    private enum Mode { PVP, PVC }

    private final Board board = new Board();
    private final Random rnd = new Random();

    private Mode mode = Mode.PVC;

    private AID playerX;   // whoever is 'X' (Human or Human1)
    private AID playerO;   // whoever is 'O' (AI or Human2)
    private AID human1;    // first registered human
    private AID human2;    // second registered human (PvP)
    private AID ai;        // AI agent (PvC)

    private char current = 'X';
    private boolean started = false;

    @Override
    protected void setup() {
        // Read mode from args
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            String m = String.valueOf(args[0]).trim().toUpperCase();
            if (m.equals("PVP")) mode = Mode.PVP;
            else mode = Mode.PVC;
        }
        System.out.println(getLocalName() + " mode = " + mode);

        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg == null) { block(); return; }
                String content = msg.getContent();
                if (content == null) { return; }

                if ("REGISTER HUMAN".equals(content)) {
                    if (human1 == null) human1 = msg.getSender();
                    else if (human2 == null) human2 = msg.getSender();
                    tryStartIfReady();
                } else if ("REGISTER AI".equals(content)) {
                    ai = msg.getSender();
                    tryStartIfReady();
                } else if (content.startsWith("MOVE")) {
                    handleMove(msg, content);
                }
            }
        });
    }

    private void tryStartIfReady() {
        if (started) return;

        switch (mode) {
            case PVP -> {
                if (human1 != null && human2 != null) {
                    assignPlayers(human1, human2);
                    startGame();
                }
            }
            case PVC -> {
                if (human1 != null && ai != null) {
                    assignPlayers(human1, ai);
                    startGame();
                }
            }
        }
    }

    private void assignPlayers(AID a, AID b) {
        // Randomly assign X and O
        if (rnd.nextBoolean()) {
            playerX = a; playerO = b;
        } else {
            playerX = b; playerO = a;
        }
        // Notify each their symbol
        sendAssign(playerX, 'X');
        sendAssign(playerO, 'O');
    }

    private void sendAssign(AID who, char symbol) {
        ACLMessage m = new ACLMessage(ACLMessage.INFORM);
        m.addReceiver(who);
        m.setContent("ASSIGN " + symbol);
        send(m);
    }

    private void startGame() {
        started = true;
        board.reset();
        current = rnd.nextBoolean() ? 'X' : 'O';
        broadcastState("NEW");
        requestMove();
    }

    private void handleMove(ACLMessage msg, String content) {
        // MOVE r c
        String[] parts = content.split("\\s+");
        if (parts.length < 3) return;
        int r, c;
        try {
            r = Integer.parseInt(parts[1]);
            c = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            // malformed
            requestMoveTo(msg.getSender(), "Bad format. Use: row col");
            return;
        }

        // Determine which symbol the sender is
        char p = senderSymbol(msg.getSender());
        if (p == '?') return; // unregistered

        // Enforce turn
        if (p != current) {
            requestMoveTo(msg.getSender(), "Not your turn.");
            return;
        }

        if (board.play(r, c, p)) {
            broadcastState("PLAYING");

            char w = board.winner();
            if (w != '.') {
                broadcastState("WIN " + w);
                started = false; // end
                return;
            }
            if (board.isFull()) {
                broadcastState("DRAW");
                started = false;
                return;
            }
            current = (current == 'X') ? 'O' : 'X';
            requestMove();
        } else {
            requestMoveTo(msg.getSender(), "Invalid move.");
        }
    }

    private char senderSymbol(AID who) {
        if (who.equals(playerX)) return 'X';
        if (who.equals(playerO)) return 'O';
        return '?';
    }

    private void requestMove() {
        AID target = (current == 'X') ? playerX : playerO;
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

        if (playerX != null) {
            ACLMessage mX = new ACLMessage(ACLMessage.INFORM);
            mX.addReceiver(playerX);
            mX.setContent(payload);
            send(mX);
        }
        if (playerO != null) {
            ACLMessage mO = new ACLMessage(ACLMessage.INFORM);
            mO.addReceiver(playerO);
            mO.setContent(payload);
            send(mO);
        }
    }
}
