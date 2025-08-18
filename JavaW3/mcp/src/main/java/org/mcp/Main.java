package org.mcp;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import io.github.cdimascio.dotenv.Dotenv;

public class Main {
    public static void main(String[] args) throws Exception {
        //        String apiKey = System.getenv("GOOGLE_API_KEY");
        String apiKey = "AIzaSyAKWdP8If6GmCk3m36IUJDGSYBfHa_Vqwk";
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getenv("GOOGLE_API_KEY");
        }
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("ERROR: Set GEMINI_API_KEY or GOOGLE_API_KEY before running.");
            System.exit(1);
        }

        Runtime rt = Runtime.instance();
        rt.setCloseVM(true);

        Profile p = new ProfileImpl();
        p.setParameter(Profile.GUI, "false");
        AgentContainer mc = rt.createMainContainer(p);

        AgentController game = mc.createNewAgent("game", GameMasterAgent.class.getName(), null);
        AgentController human = mc.createNewAgent("human", HumanAgent.class.getName(), null);
        AgentController ai = mc.createNewAgent("ai", TTTAIAgent.class.getName(), null);

        game.start();
        human.start();
        ai.start();
    }
}
