package XO_brain_AIgame;

import java.util.ArrayList;
import java.util.List;

public class Game {
    public static final int N = 10;
    public final int[] shape = new int[N * N];   // 0 empty, 1 = O, 2 = X
    public final int[] color = new int[N * N];   // 0 empty, 1 = pink, 2 = green
    public int turn = 0;                         // 0-7, same cycle as your Turn
    public int moveCount = 0;
    public final List<Integer> history = new ArrayList<>();

    public static int shapeAt(int t) { return t % 2 + 1; }
    public static int colorAt(int t) { return (t >= 6 ? 1 - t % 2 : t % 2) + 1; }

    public int player() { return turn % 2; }     // 0 = O, 1 = X
    public boolean isFull() { return moveCount >= N * N; }

    /** Places the current piece. Returns true if this move wins. */
    public boolean place(int i) {
        shape[i] = shapeAt(turn);
        color[i] = colorAt(turn);
        history.add(i);
        moveCount++;
        boolean win = wins(i);
        turn = (turn + 1) % 8;
        return win;
    }

    /** Would a piece with shape s / colour c on cell i make 5 in a row? (board is left unchanged) */
    public boolean wouldWin(int i, int s, int c) {
        shape[i] = s; color[i] = c;
        boolean w = wins(i);
        shape[i] = 0; color[i] = 0;
        return w;
    }

    public Game copy() {
        Game g = new Game();
        System.arraycopy(shape, 0, g.shape, 0, shape.length);
        System.arraycopy(color, 0, g.color, 0, color.length);
        g.turn = turn;
        g.moveCount = moveCount;
        return g;
    }

    private static final int[][] DIRS = { {0, 1}, {1, 0}, {1, 1}, {1, -1} };

    private boolean wins(int i) {
        int r = i / N, c = i % N;
        for (int[] d : DIRS) {
            if (run(shape, r, c, d) >= 5 || run(color, r, c, d) >= 5) return true;
        }
        return false;
    }

    private int run(int[] a, int r, int c, int[] d) {
        int v = a[r * N + c], n = 1;
        for (int sign = -1; sign <= 1; sign += 2) {
            for (int s = 1; s < 5; s++) {
                int rr = r + sign * d[0] * s, cc = c + sign * d[1] * s;
                if (rr < 0 || rr >= N || cc < 0 || cc >= N || a[rr * N + cc] != v) break;
                n++;
            }
        }
        return n;
    }
}