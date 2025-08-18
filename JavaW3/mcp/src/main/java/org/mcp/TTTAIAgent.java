package org.mcp;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TTTAIAgent extends Agent {
    private GeminiAgent gemini;
    private String mySymbol = "O"; // default; will accept ASSIGN message

    @Override
    protected void setup() {
        gemini = GeminiAgent.fromEnv();

        // register with game
        ACLMessage reg = new ACLMessage(ACLMessage.INFORM);
        reg.addReceiver(new AID("game", AID.ISLOCALNAME));
        reg.setContent("REGISTER AI");
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
                    if (parts.length >= 2) mySymbol = parts[1];
                } else if (c.startsWith("REQUEST_MOVE")) {
                    String[] parts = c.split("\\s+", 4);
                    String board = parts[1];   // 100 chars of . X O
                    String turn = parts[2];    // whose turn (X/O)
                    makeMove(board);
                }
            }
        });
    }

    private void makeMove(String board) {
        // STRICT response to ease parsing
        String prompt = """
            You are playing Gomoku (5-in-a-row) on a 10x10 grid as '%s'.
            The board is row-major encoded into 100 characters: '.' empty, 'X', 'O'.
            Coordinates are ZERO-BASED in [0..9].
            Your task: choose a legal, strong move.
            CRITICAL: Only respond with two integers like "row col". No text, no punctuation.
            Current board: %s
            """.formatted(mySymbol, board);

        String answer = "";
        for (int attempt = 0; attempt < 4; attempt++) {
            try {
                answer = gemini.complete(prompt);
                int[] rc = parseRC(answer);
                if (rc != null && inBounds(rc[0], rc[1]) && isEmpty(board, rc[0], rc[1])) {
                    ACLMessage m = new ACLMessage(ACLMessage.INFORM);
                    m.addReceiver(new AID("game", AID.ISLOCALNAME));
                    m.setContent("MOVE " + rc[0] + " " + rc[1]);
                    send(m);
                    return;
                }
                prompt += "\nYour last output was invalid. Remember: ONLY 'row col' like '4 7' within [0..9].";
            } catch (Exception e) {
                prompt += "\nThe last attempt failed due to an error; try again.";
            }
        }

        // Fallback: first empty cell
        for (int i = 0; i < Board.SIZE * Board.SIZE; i++) {
            if (board.charAt(i) == '.') {
                int r = i / Board.SIZE, c = i % Board.SIZE;
                ACLMessage m = new ACLMessage(ACLMessage.INFORM);
                m.addReceiver(new AID("game", AID.ISLOCALNAME));
                m.setContent("MOVE " + r + " " + c);
                send(m);
                return;
            }
        }
    }

    private static boolean inBounds(int r, int c) {
        return r >= 0 && r < Board.SIZE && c >= 0 && c < Board.SIZE;
    }

    private static boolean isEmpty(String board, int r, int c) {
        int idx = r * Board.SIZE + c;
        return board.charAt(idx) == '.';
    }

    private static int[] parseRC(String text) {
        if (text == null) return null;
        String t = text.trim();
        // "r c"
        Matcher m = Pattern.compile("^\\s*([0-9])\\s+([0-9])\\s*$").matcher(t);
        if (m.matches()) {
            return new int[]{ Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)) };
        }
        // "r,c"
        m = Pattern.compile("^\\s*([0-9])\\s*,\\s*([0-9])\\s*$").matcher(t);
        if (m.matches()) {
            return new int[]{ Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)) };
        }
        return null;
    }
}
