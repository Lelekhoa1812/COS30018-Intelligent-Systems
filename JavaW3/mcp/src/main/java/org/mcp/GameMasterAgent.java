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

    private AID playerX;   // 'X'
    private AID playerO;   // 'O'
    private AID human1;
    private AID human2;
    private AID ai;        // only in PVC

    private char current = 'X';
    private boolean started = false;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            String m = String.valueOf(args[0]).trim().toUpperCase();
            mode = m.equals("PVP") ? Mode.PVP : Mode.PVC;
        }
        System.out.println(getLocalName() + " mode = " + mode);

        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg == null) { block(); return; }
                String content = msg.getContent();
                if (content == null) return;

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
        if (rnd.nextBoolean()) { playerX = a; playerO = b; }
        else { playerX = b; playerO = a; }
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
        broadcastState("NEW", null);
        requestMove();
    }

    private void handleMove(ACLMessage msg, String content) {
        String[] parts = content.split("\\s+");
        if (parts.length < 3) return;
        int r, c;
        try {
            r = Integer.parseInt(parts[1]);
            c = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            requestMoveTo(msg.getSender(), "Bad format. Use: row col");
            return;
        }

        char p = senderSymbol(msg.getSender());
        if (p == '?') return;

        if (p != current) {
            requestMoveTo(msg.getSender(), "Not your turn.");
            return;
        }

        if (board.play(r, c, p)) {
            // If AI was thinking, stop spinner now
            if (mode == Mode.PVC && msg.getSender().equals(ai)) broadcastThink(false);

            broadcastState("PLAYING", null);

            char w = board.winner();
            if (w != '.') {
                int[][] winLine = board.getLastWinLine();
                broadcastState("WIN " + w, winLine);
                started = false;
                return;
            }
            if (board.isFull()) {
                broadcastState("DRAW", null);
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

        // If AI's turn in PVC mode → start spinner
        if (mode == Mode.PVC && target.equals(ai)) broadcastThink(true);

        requestMoveTo(target, "Your turn (" + current + ")");
    }

    private void requestMoveTo(AID target, String note) {
        ACLMessage req = new ACLMessage(ACLMessage.REQUEST);
        req.addReceiver(target);
        req.setContent("REQUEST_MOVE " + board.serialize() + " " + current + " " + note.replace(' ', '_'));
        send(req);
    }

    private void broadcastThink(boolean start) {
        String payload = start ? "THINK START" : "THINK STOP";
        if (playerX != null) {
            ACLMessage mX = new ACLMessage(ACLMessage.INFORM);
            mX.addReceiver(playerX); mX.setContent(payload); send(mX);
        }
        if (playerO != null) {
            ACLMessage mO = new ACLMessage(ACLMessage.INFORM);
            mO.addReceiver(playerO); mO.setContent(payload); send(mO);
        }
    }

    private void broadcastState(String status, int[][] winLine) {
        StringBuilder sb = new StringBuilder("STATE ").append(board.serialize()).append(' ').append(status);
        if (winLine != null && winLine.length == Board.WIN_LEN) {
            sb.append(" LINE");
            for (int i = 0; i < Board.WIN_LEN; i++) {
                sb.append(' ').append(winLine[i][0]).append(' ').append(winLine[i][1]);
            }
        }
        String payload = sb.toString();

        if (playerX != null) {
            ACLMessage mX = new ACLMessage(ACLMessage.INFORM);
            mX.addReceiver(playerX); mX.setContent(payload); send(mX);
        }
        if (playerO != null) {
            ACLMessage mO = new ACLMessage(ACLMessage.INFORM);
            mO.addReceiver(playerO); mO.setContent(payload); send(mO);
        }
    }
}
