package org.mcp;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hybrid Gomoku Agent (10x10, five-in-a-row).
 * 1) Deterministic search for immediate win/block + key patterns (open-4, closed-4, open-3, fork).
 * 2) Score all legal cells, prune to top-K candidates.
 * 3) Ask Gemini to choose among the shortlist with strict "row col" output.
 * 4) Robust fallback to best-scored move if LLM output invalid/times out.
 */
public class TTTAIAgent extends Agent {
    private GeminiAgent gemini;
    private String mySymbol = "O"; // will be overwritten by ASSIGN

    // Scoring weights (tuneable)
    private static final long WIN_NOW              = 1_000_000_000L;
    private static final long BLOCK_WIN_NOW        =   500_000_000L;
    private static final long CREATE_OPEN4         =     8_000_000L;
    private static final long BLOCK_OPEN4          =     7_000_000L;
    private static final long CREATE_CLOSED4       =     2_000_000L;
    private static final long CREATE_OPEN3         =       500_000L;
    private static final long BLOCK_OPEN3          =       450_000L;
    private static final long CREATE_FORK_OPEN3x2  =     15_000_000L; // two open-3 in different directions
    private static final long BLOCK_FORK_OPEN3x2   =     12_000_000L;
    private static final long EXTEND_CHAIN_BONUS   =        50_000L;  // per stone length in the best direction
    private static final long CENTER_BONUS         =        10_000L;  // stronger near center
    private static final long ADJACENT_BONUS       =         5_000L;  // next to any stone

    private static final int SIZE = Board.SIZE; // 10

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
                    System.out.println("[AI] Assigned symbol = " + mySymbol);
                } else if (c.startsWith("REQUEST_MOVE")) {
                    String[] parts = c.split("\\s+", 4);
                    String board = parts[1]; // 100 chars of . X O
                    // parts[2] == whose turn; we already know it's ours when we get this
                    System.out.println("\n=== AI TURN (" + mySymbol + ") ===");
                    makeMove(board);
                }
            }
        });
    }

    // ======= Top-level decision =======
    private void makeMove(String board) {
        char me = mySymbol.charAt(0);
        char opp = other(me);

        // 0) Candidate generation (prune to empty cells near existing stones; if empty board -> center)
        List<int[]> empties = generateCandidateCells(board);

        // 1) Immediate win or block?
        int[] win = findImmediateWin(board, empties, me);
        if (win != null) {
            System.out.printf("[AI] Immediate WIN found at (%d,%d)%n", win[0], win[1]);
            play(win[0], win[1]); return;
        }

        int[] block = findImmediateWin(board, empties, opp);
        if (block != null) {
            System.out.printf("[AI] Blocking opponent WIN at (%d,%d)%n", block[0], block[1]);
            play(block[0], block[1]); return;
        }

        // 2) Score all candidates
        List<ScoredMove> scored = new ArrayList<>();
        for (int[] rc : empties) {
            long score = evaluatePlacement(board, rc[0], rc[1], me, opp);
            scored.add(new ScoredMove(rc[0], rc[1], score));
        }
        if (scored.isEmpty()) {
            int[] fallback = firstEmpty(board);
            if (fallback != null) play(fallback[0], fallback[1]);
            return;
        }
        scored.sort((a, b) -> Long.compare(b.score, a.score));

        // keep only top-N before Gemini
        int PRUNE_N = 20;
        if (scored.size() > PRUNE_N) scored = scored.subList(0, PRUNE_N);

        System.out.println("[AI] Top candidate scores:");
        for (int i=0;i<Math.min(10,scored.size());i++) {
            ScoredMove sm = scored.get(i);
            System.out.printf("   #%d: (%d,%d) score=%d%n", i, sm.r, sm.c, sm.score);
        }

        // 3) If the top score is clearly dominant, just play it (avoid LLM when obvious)
        if (scored.size() == 1 || (scored.get(0).score > scored.get(1).score * 1.6)) {
            ScoredMove best = scored.get(0);
            System.out.printf("[AI] Deterministic pick (%d,%d)%n", best.r, best.c);
            play(best.r, best.c);
            return;
        }

        // 4) Otherwise ask Gemini to choose among a shortlist (top-K)
        int K = Math.min(5, scored.size());
        List<ScoredMove> topK = scored.subList(0, K);
        int[] llmPick = chooseMoveWithGemini(board, me, opp, topK);
        if (llmPick != null) {
            System.out.printf("[AI] Gemini pick (%d,%d)%n", llmPick[0], llmPick[1]);
            play(llmPick[0], llmPick[1]);
            return;
        }

        // 5) Robust fallback
        ScoredMove best = scored.get(0);
        System.out.printf("[AI] Fallback pick (%d,%d)%n", best.r, best.c);
        play(best.r, best.c);
    }

    private void play(int r, int c) {
        ACLMessage m = new ACLMessage(ACLMessage.INFORM);
        m.addReceiver(new AID("game", AID.ISLOCALNAME));
        m.setContent("MOVE " + r + " " + c);
        send(m);
    }

    // ======= Candidate generation =======
    private List<int[]> generateCandidateCells(String board) {
        List<int[]> out = new ArrayList<>();
        boolean anyStone = false;

        // first detect any stone
        for (int i = 0; i < SIZE*SIZE; i++) {
            char ch = board.charAt(i);
            if (ch == 'X' || ch == 'O') { anyStone = true; break; }
        }
        if (!anyStone) {
            out.add(new int[]{SIZE/2, SIZE/2});
            return out;
        }

        boolean[][] taken = new boolean[SIZE][SIZE];
        for (int r = 0; r < SIZE; r++) for (int c = 0; c < SIZE; c++) {
            if (board.charAt(r*SIZE + c) != '.') continue;
            if (hasNeighborStone(board, r, c, 1)) {
                out.add(new int[]{r, c});
                taken[r][c] = true;
            }
        }
        // If pruning too hard yields none, include all empties
        if (out.isEmpty()) {
            for (int r = 0; r < SIZE; r++) for (int c = 0; c < SIZE; c++) {
                if (board.charAt(r*SIZE + c) == '.') out.add(new int[]{r, c});
            }
        }
        return out;
    }

    private boolean hasNeighborStone(String board, int r, int c, int radius) {
        for (int dr = -radius; dr <= radius; dr++)
            for (int dc = -radius; dc <= radius; dc++) {
                if (dr == 0 && dc == 0) continue;
                int rr = r + dr, cc = c + dc;
                if (!inBounds(rr, cc)) continue;
                char ch = board.charAt(rr*SIZE + cc);
                if (ch == 'X' || ch == 'O') return true;
            }
        return false;
    }

    // ======= Immediate win detection =======
    private int[] findImmediateWin(String board, List<int[]> candidates, char sym) {
        for (int[] rc : candidates) {
            if (wouldMakeFive(board, rc[0], rc[1], sym)) return rc;
        }
        return null;
    }

    private boolean wouldMakeFive(String board, int r, int c, char sym) {
        if (!isEmpty(board, r, c)) return false;
        // Check 4 directions
        int[][] dirs = {{0,1},{1,0},{1,1},{1,-1}};
        for (int[] d : dirs) {
            int count = 1 + countRun(board, r, c, d[0], d[1], sym)
                    + countRun(board, r, c, -d[0], -d[1], sym);
            if (count >= 5) return true;
        }
        return false;
    }

    // ======= Heuristic evaluation =======
    private long evaluatePlacement(String board, int r, int c, char me, char opp) {
        long score = 0;

        // Place hypothetically for feature extraction
        // (We don't mutate; we compute using count/opens)
        // For each direction collect (length, openEnds)
        int[][] dirs = {{0,1},{1,0},{1,1},{1,-1}};

        // 1) Offensive patterns for ME
        int bestLenMe = 0;
        int open4Me = 0, closed4Me = 0, open3Me = 0, forkOpen3Me = 0;

        for (int[] d : dirs) {
            LineFeat lf = lineFeatures(board, r, c, d[0], d[1], me);
            bestLenMe = Math.max(bestLenMe, lf.length);

            if (lf.length >= 5) return WIN_NOW; // instant win (should’ve been caught earlier)
            if (lf.length == 4) {
                if (lf.openEnds == 2) open4Me++;
                else if (lf.openEnds == 1) closed4Me++;
            } else if (lf.length == 3) {
                if (lf.openEnds == 2) open3Me++;
            }
        }
        if (open4Me > 0) score += CREATE_OPEN4;
        if (closed4Me > 0) score += CREATE_CLOSED4;
        if (open3Me > 0) score += CREATE_OPEN3;

        // Fork (two or more open-3 in different directions)
        if (open3Me >= 2) {
            score += CREATE_FORK_OPEN3x2;
            forkOpen3Me = 1;
        }

        // 2) Defensive patterns against OPP
        int open4Opp = 0, open3Opp = 0, forkOpen3Opp = 0, wouldBlockWin = 0;
        for (int[] d : dirs) {
            LineFeat lfOpp = lineFeatures(board, r, c, d[0], d[1], opp);
            if (lfOpp.length >= 5) { wouldBlockWin = 1; }
            if (lfOpp.length == 4 && lfOpp.openEnds >= 1) open4Opp++;
            if (lfOpp.length == 3 && lfOpp.openEnds == 2) open3Opp++;
        }
        if (wouldBlockWin == 1) score += BLOCK_WIN_NOW;
        if (open4Opp > 0) score += BLOCK_OPEN4;
        if (open3Opp >= 2) { score += BLOCK_FORK_OPEN3x2; forkOpen3Opp = 1; }
        else if (open3Opp > 0) score += BLOCK_OPEN3;

        // 3) Extend-chain / centrality / adjacency bonuses
        score += bestLenMe * EXTEND_CHAIN_BONUS;

        int center = SIZE / 2;
        long centerDist = Math.abs(r - center) + Math.abs(c - center);
        score += Math.max(0, (CENTER_BONUS * (SIZE * 2 - centerDist))); // closer → larger bonus

        if (hasNeighborStone(board, r, c, 1)) score += ADJACENT_BONUS;

        return score;
    }

    /** Line features if we hypothetically place 'sym' at (r,c) along direction (dr,dc). */
    private LineFeat lineFeatures(String board, int r, int c, int dr, int dc, char sym) {
        // Count contiguous stones of 'sym' on both sides (excluding (r,c))
        int left = countRun(board, r, c, -dr, -dc, sym);
        int right = countRun(board, r, c, dr, dc, sym);
        int length = 1 + left + right;

        // Check openness on both ends (one cell beyond the contiguous run)
        boolean openL = isOpenEnd(board, r, c, -dr, -dc, left);
        boolean openR = isOpenEnd(board, r, c, dr, dc, right);
        int openEnds = (openL ? 1 : 0) + (openR ? 1 : 0);

        return new LineFeat(length, openEnds);
    }

    private boolean isOpenEnd(String board, int r, int c, int dr, int dc, int runLenInThatDirection) {
        int rr = r + (runLenInThatDirection + 1) * dr;
        int cc = c + (runLenInThatDirection + 1) * dc;
        return inBounds(rr, cc) && isEmpty(board, rr, cc);
    }

    private int countRun(String board, int r, int c, int dr, int dc, char sym) {
        int count = 0;
        int rr = r + dr, cc = c + dc;
        while (inBounds(rr, cc) && board.charAt(rr*SIZE + cc) == sym) {
            count++; rr += dr; cc += dc;
        }
        return count;
    }

    private static char other(char s) { return s == 'X' ? 'O' : 'X'; }
    private static boolean inBounds(int r, int c) { return r >= 0 && r < SIZE && c >= 0 && c < SIZE; }
    private static boolean isEmpty(String b, int r, int c) { return b.charAt(r*SIZE + c) == '.'; }

    private int[] firstEmpty(String board) {
        for (int i = 0; i < SIZE*SIZE; i++) {
            if (board.charAt(i) == '.') return new int[]{i/SIZE, i%SIZE};
        }
        return null;
    }

    // ======= LLM shortlist selection =======
    private int[] chooseMoveWithGemini(String board, char me, char opp, List<ScoredMove> topK) {
        StringBuilder shortlist = new StringBuilder();
        for (int i=0; i<topK.size(); i++) {
            ScoredMove sm = topK.get(i);
            shortlist.append(String.format("[%d] %d %d score=%d%n", i, sm.r, sm.c, sm.score));
        }

        String prompt = """
        You are playing Gomoku (10x10, Five-in-a-Row) as '%s'.
        Opponent is '%s'.
        Candidates (row col score):
        %s
        Pick the single best move.
        STRICT: output only "row col".
        """.formatted(me, opp, shortlist);

        System.out.println("[AI] Gemini prompt:\n" + prompt);

        try {
            String answer = gemini.complete(prompt);
            System.out.println("[AI] Gemini response: " + answer);
            return parseRC(answer);
        } catch (Exception e) {
            System.out.println("[AI] Gemini error: " + e.getMessage());
            return null;
        }
    }

    // === utilities ===
    private static int[] parseRC(String text) {
        if (text == null) return null;
        String t = text.trim();
        // Match one- or two-digit row/col
        Matcher m = Pattern.compile("\\b([0-9]{1,2})\\D+([0-9]{1,2})\\b").matcher(t);
        if (m.find()) {
            int r = Integer.parseInt(m.group(1));
            int c = Integer.parseInt(m.group(2));
            return new int[]{r, c};
        }
        return null;
    }


    // ======= helpers =======
    private static class ScoredMove {
        final int r, c; final long score;
        ScoredMove(int r, int c, long score) { this.r = r; this.c = c; this.score = score; }
    }
    private static class LineFeat {
        final int length; final int openEnds;
        LineFeat(int length, int openEnds) { this.length = length; this.openEnds = openEnds; }
    }
}
