package org.mcp;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TTTAIAgent extends Agent {
    private GeminiAgent gemini;

    @Override
    protected void setup() {
        gemini = GeminiAgent.fromEnv();

        // register with game
        ACLMessage reg = new ACLMessage(ACLMessage.INFORM);
        reg.addReceiver(getAID("game"));
        reg.setContent("REGISTER AI");
        send(reg);

        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg == null) { block(); return; }
                String c = msg.getContent();
                if (c != null && c.startsWith("REQUEST_MOVE")) {
                    String[] parts = c.split("\\s+");
                    String board = parts[1];   // 9 chars of . X O
                    String turn = parts[2];    // "O" for AI
                    makeMove(board);
                } else if (c != null && c.startsWith("STATE")) {
                    // ignore; could log if desired
                }
            }
        });
    }

    private void makeMove(String board) {
        // Prompt enforces STRICT output for easy parsing
        String prompt = """
            You are playing Tic-Tac-Toe as 'O'. The board is a 3x3 grid.
            Board encoding is 9 characters row-major: '.' empty, 'X', 'O'.
            Example: "X.O..O..X"
            ONLY respond with the move as two ZERO-BASED integers: "row col".
            Do NOT add explanations. Valid rows/cols are 0,1,2.
            Choose a legal, good move. Current board: %s
            """.formatted(board);

        String answer = "";
        for (int attempt = 0; attempt < 4; attempt++) {
            try {
                answer = gemini.complete(prompt);
                int[] rc = parseRC(answer);
                if (rc != null) {
                    ACLMessage m = new ACLMessage(ACLMessage.INFORM);
                    m.addReceiver(getAID("game"));
                    m.setContent("MOVE " + rc[0] + " " + rc[1]);
                    send(m);
                    return;
                }
                prompt += "\nYour last output was invalid. Remember: ONLY 'row col' like '1 2'.";
            } catch (Exception e) {
                prompt += "\nThe last attempt failed due to an error; try again.";
            }
        }

        // Fallback: first empty cell
        for (int i=0;i<9;i++){
            if (board.charAt(i)=='.'){
                int r=i/3, c=i%3;
                ACLMessage m = new ACLMessage(ACLMessage.INFORM);
                m.addReceiver(getAID("game"));
                m.setContent("MOVE " + r + " " + c);
                send(m);
                return;
            }
        }
    }

    private static int[] parseRC(String text) {
        if (text == null) return null;
        Matcher m = Pattern.compile("(?s).*?([0-2])\\D+([0-2]).*").matcher(text.trim());
        if (m.matches()) {
            return new int[]{ Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)) };
        }
        // also accept "r,c"
        m = Pattern.compile("\\b([0-2])\\s*,\\s*([0-2])\\b").matcher(text.trim());
        if (m.find()) {
            return new int[]{ Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)) };
        }
        return null;
    }
}

