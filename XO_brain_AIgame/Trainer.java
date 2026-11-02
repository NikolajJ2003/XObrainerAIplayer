package XO_brain_AIgame;

import java.io.IOException;

public class Trainer {
    public static void main(String[] args) throws IOException {
        int games = args.length > 0 ? Integer.parseInt(args[0]) : 2000;
        Agent a = new Agent(BoardGUI.WEIGHTS_FILE);
        a.epsilon = 0.1;
        Agent b = new Agent(a);               // shares the same weights as a
        Agent[] agents = { a, b };
        int[] results = new int[3];           // draws, O wins, X wins

        for (int n = 1; n <= games; n++) {
            Game g = new Game();
            int winner = -1;
            while (!g.isFull()) {
                int p = g.player();
                if (g.place(agents[p].chooseMove(g))) { winner = p; break; }
            }
            a.learn(winner == -1 ? 0 : (winner == 0 ? 1 : -1));
            b.learn(winner == -1 ? 0 : (winner == 1 ? 1 : -1));
            results[winner + 1]++;
            if (n % 500 == 0) {
                System.out.println(n + " games  draws/O/X = " + results[0] + "/" + results[1] + "/" + results[2]);
            }
        }
        a.save(BoardGUI.WEIGHTS_FILE);
    }
}