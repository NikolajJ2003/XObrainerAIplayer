package XO_brain_AIgame;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Agent {
    static final int NF = 15;
    static final int[][] WINDOWS = buildWindows();

    final double[] w;
    final Random rng = new Random();
    double epsilon = 0.0;     // chance of a random move (exploration)
    double alpha = 0.01;      // learning rate
    final List<double[]> trajectory = new ArrayList<>();

    public Agent(String file) {
        w = new double[NF];
        initPriors();
        try {
            List<String> lines = Files.readAllLines(Paths.get(file));
            if (lines.size() == NF) {
                for (int k = 0; k < NF; k++) w[k] = Double.parseDouble(lines.get(k).trim());
            }
        } catch (IOException | NumberFormatException e) {
            // no saved weights yet: keep the priors
        }
    }

    /** Second agent that shares the same weights (for self-play). */
    public Agent(Agent other) {
        w = other.w;
        epsilon = other.epsilon;
        alpha = other.alpha;
    }

    private void initPriors() {
        // index = type*3 + (k-2), types: myShape, oppShape, myColor, oppColor; 12 = opp threats, 13 = my threats
        w[1] = 0.2;  w[2] = 0.4;
        w[4] = -0.2; w[5] = -0.4;
        w[7] = 0.1;  w[8] = 0.2;
        w[10] = -0.1; w[11] = -0.2;
        w[12] = -1.5; w[13] = 1.0;
    }

    public void save(String file) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (double x : w) sb.append(x).append('\n');
        Files.write(Paths.get(file), sb.toString().getBytes(),StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public static void logGame(String file, int aiPlayer, double result, List<Integer> history) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(aiPlayer).append(';').append(result).append(';');
        for (int m : history) sb.append(m).append(' ');
        sb.append('\n');
        Files.write(Paths.get(file), sb.toString().getBytes(),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    // ---------------------------------------------------------------- choosing a move

    public int chooseMove(Game g) {
        int me = g.player();
        int s = Game.shapeAt(g.turn), c = Game.colorAt(g.turn);
        int os = Game.shapeAt((g.turn + 1) % 8), oc = Game.colorAt((g.turn + 1) % 8);

        List<Integer> empties = new ArrayList<>();
        for (int i = 0; i < Game.N * Game.N; i++) if (g.shape[i] == 0) empties.add(i);

        int pick = -1;
        // 1. take a win if there is one
        for (int i : empties) if (g.wouldWin(i, s, c)) { pick = i; break; }

        // 2. otherwise block cells where the opponent's next piece would win
        List<Integer> pool = null;
        if (pick < 0) {
            List<Integer> block = new ArrayList<>();
            for (int i : empties) if (g.wouldWin(i, os, oc)) block.add(i);
            pool = block.isEmpty() ? nearbyCells(g, empties) : block;
        }

        // 3. otherwise let the learned value function (or exploration) decide
        if (pick < 0) {
            if (rng.nextDouble() < epsilon) {
                pick = pool.get(rng.nextInt(pool.size()));
            } else {
                double best = -2;
                for (int i : pool) {
                    double v = value(afterFeatures(g, i, me)) + rng.nextDouble() * 1e-6; // random tie-break
                    if (v > best) { best = v; pick = i; }
                }
            }
        }
        trajectory.add(afterFeatures(g, pick, me));
        return pick;
    }

    private List<Integer> nearbyCells(Game g, List<Integer> empties) {
        if (g.moveCount == 0) {
            List<Integer> centre = new ArrayList<>();
            centre.add(44); centre.add(45); centre.add(54); centre.add(55);
            return centre;
        }
        List<Integer> out = new ArrayList<>();
        for (int i : empties) {
            int r = i / Game.N, c = i % Game.N;
            boolean near = false;
            for (int dr = -2; dr <= 2 && !near; dr++) {
                for (int dc = -2; dc <= 2; dc++) {
                    int rr = r + dr, cc = c + dc;
                    if (rr < 0 || rr >= Game.N || cc < 0 || cc >= Game.N) continue;
                    if (g.shape[rr * Game.N + cc] != 0) { near = true; break; }
                }
            }
            if (near) out.add(i);
        }
        return out.isEmpty() ? empties : out;
    }

    // ---------------------------------------------------------------- learning

    /** Call when the game is over. z = +1 the agent won, -1 it lost, 0 draw. */
    public void learn(double z) {
        int n = trajectory.size();
        for (int t = n - 1; t >= 0; t--) {
            double[] f = trajectory.get(t);
            double v = value(f);
            double target = (t == n - 1) ? z : value(trajectory.get(t + 1));
            double delta = (target - v) * (1 - v * v);   // tanh derivative
            for (int k = 0; k < NF; k++) w[k] += alpha * delta * f[k];
        }
        trajectory.clear();
    }

    double value(double[] f) {
        double sum = 0;
        for (int k = 0; k < NF; k++) sum += w[k] * f[k];
        return Math.tanh(sum);
    }

    private double[] afterFeatures(Game g, int cell, int me) {
        Game a = g.copy();
        a.place(cell);
        return features(a, me);
    }

    /** Features of a position, seen from player `me` (the one who just moved). */
    double[] features(Game g, int me) {
        double[] f = new double[NF];
        int[] val = { me + 1, 2 - me, me + 1, 2 - me };      // myShape, oppShape, myColor, oppColor
        boolean[] isShape = { true, true, false, false };
        int oppS = Game.shapeAt(g.turn), oppC = Game.colorAt(g.turn);                       // opponent's next piece
        int meS = Game.shapeAt((g.turn + 1) % 8), meC = Game.colorAt((g.turn + 1) % 8);     // my piece after that

        for (int[] win : WINDOWS) {
            for (int t = 0; t < 4; t++) {
                int[] arr = isShape[t] ? g.shape : g.color;
                int v = val[t], k = 0;
                boolean dead = false;
                for (int idx : win) {
                    int x = arr[idx];
                    if (x == v) k++;
                    else if (x != 0) { dead = true; break; }
                }
                if (dead || k < 2 || k > 4) continue;
                f[t * 3 + (k - 2)] += 1;
                if (k == 4) {
                    if (v == (isShape[t] ? oppS : oppC)) f[12] += 1;   // opponent can finish it next move
                    if (v == (isShape[t] ? meS : meC))   f[13] += 1;   // I can finish it on my next move
                }
            }
        }
        f[14] = 1;   // bias
        return f;
    }

    private static int[][] buildWindows() {
        List<int[]> list = new ArrayList<>();
        int[][] dirs = { {0, 1}, {1, 0}, {1, 1}, {1, -1} };
        for (int r = 0; r < Game.N; r++) {
            for (int c = 0; c < Game.N; c++) {
                for (int[] d : dirs) {
                    int er = r + 4 * d[0], ec = c + 4 * d[1];
                    if (er < 0 || er >= Game.N || ec < 0 || ec >= Game.N) continue;
                    int[] win = new int[5];
                    for (int s = 0; s < 5; s++) win[s] = (r + d[0] * s) * Game.N + (c + d[1] * s);
                    list.add(win);
                }
            }
        }
        return list.toArray(new int[0][]);
    }
}