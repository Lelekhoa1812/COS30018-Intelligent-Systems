package org.mcp;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import io.github.cdimascio.dotenv.Dotenv;
import javax.swing.*;

public class Main {
    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("GOOGLE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getenv("GOOGLE_API_KEY");
        }
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("ERROR: Set GEMINI_API_KEY or GOOGLE_API_KEY before running.");
            System.exit(1);
        }

        // Ask for mode
        Object[] options = { "Player vs Player", "Player vs Computer" };
        int choice = JOptionPane.showOptionDialog(
                null,
                "Select game mode:",
                "Gomoku — Mode",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]
        );
        boolean pvp = (choice == 0);

        if (!pvp && (apiKey == null || apiKey.isBlank())) {
            JOptionPane.showMessageDialog(null,
                    "GOOGLE_API_KEY (or GEMINI_API_KEY) is not set.\n" +
                            "AI mode requires a valid Gemini API key.",
                    "Missing API Key",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        Runtime rt = Runtime.instance();
        rt.setCloseVM(true);

        // Create Player
        Profile p = new ProfileImpl();
        p.setParameter(Profile.GUI, "false");
        AgentContainer mc = rt.createMainContainer(p);

        // Create GameMaster with mode arg
        String modeArg = pvp ? "PVP" : "PVC";
        AgentController game = mc.createNewAgent("game", GameMasterAgent.class.getName(), new Object[]{ modeArg });

        if (pvp) {
            AgentController human1 = mc.createNewAgent("human1", HumanAgent.class.getName(), new Object[]{ "Player 1" });
            AgentController human2 = mc.createNewAgent("human2", HumanAgent.class.getName(), new Object[]{ "Player 2" });
            game.start();
            human1.start();
            human2.start();
        } else {
            AgentController human = mc.createNewAgent("human", HumanAgent.class.getName(), new Object[]{ "You" });
            AgentController ai = mc.createNewAgent("ai", TTTAIAgent.class.getName(), null);
            game.start();
            human.start();
            ai.start();
        }
    }
}
