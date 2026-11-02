package XO_brain_AIgame;

//import java.io.IOException;
//import java.io.PrintWriter;
//import java.net.Socket;
//import java.util.Scanner;

import java.io.IOException;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import java.awt.Container;
import java.awt.GridLayout;
//import java.awt.List;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.Color;
import java.awt.Font;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;


@SuppressWarnings("static-access")
public class BoardGUI extends JFrame implements ActionListener {
    static BoardGUI window;
    public Container mainWindow = null;
    public Container nextPieceExamples = null;
    public Container nextPieceExampleLeft = null;
    public Container nextPieceExampleRight = null;
    public Container board = null;
    public JLabel nextPieceLabel = null;
    public JButton[] boardButtons = new JButton[10*10];
    public JButton[] nextPieceButtonsX = new JButton[4];
    public JButton[] nextPieceButtonsO = new JButton[4];
    Object source;
    public String[] playerShape = new String[2];
    public Color[] playerColor = new Color[2];
    int player = 0;
    static boolean isAlive = true;
    public int Turn = 0;
    static final String WEIGHTS_FILE = "xo_weights.txt";
    static final String LOG_FILE = "xo_games.csv";
    Game game = new Game();
    Agent agent = new Agent(WEIGHTS_FILE);
    int aiPlayer = 1;   // 0 = O (moves first), 1 = X
    
    public static void main(String[] args) {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        Double width = screenSize.getWidth()*0.8;
        Double height = screenSize.getHeight()*0.8;
        int x_size = width.intValue(), y_size = height.intValue() ; //
        window = new BoardGUI();

        //new Thread(new ClientThread(netin,netout,window)).start(); //kommonikations tråd
        //new Thread(new AdminThread(new Scanner(System.in),netout,window)).start(); //En tråd der lader brugeren selv skrive til serveren
        
        window.setTitle("XO Brain");
        window.setSize(y_size, y_size);
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setVisible(true);
    }

    public BoardGUI() {
        mainWindow = getContentPane();
        mainWindow.setLayout(new BorderLayout(0, 10));

        // ---------- Top: label + piece previews ----------
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));

        nextPieceLabel = new JLabel("Next piece", SwingConstants.CENTER);
        nextPieceLabel.setFont(new Font("Arial", Font.ITALIC, 20));
        nextPieceLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        top.add(nextPieceLabel);
        top.add(Box.createVerticalStrut(8));

        JPanel previewRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        Dimension pieceSize = new Dimension(50, 50);   // square preview boxes

        for (int i = 0; i < nextPieceButtonsX.length; i++) {
            nextPieceButtonsX[i] = new JButton("");
            nextPieceButtonsX[i].setFont(new Font("Arial", Font.PLAIN, 20));
            nextPieceButtonsX[i].setBackground(Color.WHITE);
            nextPieceButtonsX[i].setPreferredSize(pieceSize);
            nextPieceButtonsX[i].addActionListener(this);
            previewRow.add(nextPieceButtonsX[i]);
        }

        previewRow.add(Box.createHorizontalStrut(40));  // the gap in the middle

        for (int i = 0; i < nextPieceButtonsO.length; i++) {
            nextPieceButtonsO[i] = new JButton("");
            nextPieceButtonsO[i].setFont(new Font("Arial", Font.PLAIN, 20));
            nextPieceButtonsO[i].setBackground(Color.WHITE);
            nextPieceButtonsO[i].setPreferredSize(pieceSize);
            nextPieceButtonsO[i].addActionListener(this);
            previewRow.add(nextPieceButtonsO[i]);
        }

        nextPieceExamples = previewRow;
        top.add(previewRow);
        mainWindow.add(top, BorderLayout.NORTH);

        // ---------- Center: square 10x10 board ----------
        JPanel boardPanel = new JPanel(new GridLayout(10, 10));
        for (int i = 0; i < boardButtons.length; i++) {
            boardButtons[i] = new JButton("");
            boardButtons[i].setFont(new Font("Arial", Font.PLAIN, 20));
            boardButtons[i].setBackground(Color.WHITE);
            boardButtons[i].addActionListener(this);
            boardPanel.add(boardButtons[i]);
        }
        board = boardPanel;

        // Wrapper that keeps the board square and centered, however the window is resized
        JPanel holder = new JPanel(null) {
            @Override
            public void doLayout() {
                int s = Math.min(getWidth(), getHeight());
                boardPanel.setBounds((getWidth() - s) / 2, (getHeight() - s) / 2, s, s);
            }
        };
        holder.add(boardPanel);
        mainWindow.add(holder, BorderLayout.CENTER);

        playerShape[0] = "O"; playerShape[1] = "X";
        playerColor[0] = Color.PINK; playerColor[1] = Color.GREEN;

        updateNextPieces();
        agent.epsilon = 0.02;   // tiny bit of exploration so it keeps learning
    }

    public void actionPerformed(ActionEvent e) {
        if (!isAlive || game.player() == aiPlayer) return;
        for (int i = 0; i < boardButtons.length; i++) {
            if (e.getSource() == boardButtons[i] && game.shape[i] == 0) {
                playMove(i);
                if (isAlive && game.player() == aiPlayer) aiTurn();
                break;
            }
        }
    }

    private void aiTurn() {
        playMove(agent.chooseMove(game));
    }

    private void playMove(int cell) {
        boolean won = game.place(cell);
        boardButtons[cell].setText(playerShape[game.shape[cell] - 1]);
        boardButtons[cell].setBackground(playerColor[game.color[cell] - 1]);
        Turn = game.turn;               // keep the preview code in sync
        player = game.player();
        updateNextPieces();
        if (won) endGame((game.moveCount - 1) % 2);   // the player who just moved
        else if (game.isFull()) endGame(-1);
    }

    private void endGame(int winner) {
        isAlive = false;
        double z = (winner == -1) ? 0 : (winner == aiPlayer ? 1 : -1);   // reward / punishment
        agent.learn(z);
        try {
            agent.save(WEIGHTS_FILE);
            Agent.logGame(LOG_FILE, aiPlayer, z, game.history);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        String msg = (winner == -1) ? "It's a draw" : "The winner is " + playerShape[winner];
        if (JOptionPane.showConfirmDialog(null, msg + "\nWould you like to play again?") == JOptionPane.YES_OPTION) {
            for (JButton b : boardButtons) { b.setText(""); b.setBackground(Color.WHITE); }
            game = new Game();
            Turn = 0;
            player = 0;
            isAlive = true;
            updateNextPieces();
            aiPlayer = 1 - aiPlayer;            // alternate who starts
            if (aiPlayer != winner) aiTurn();
        } else {
            System.exit(0);
        }
    }

    private void updateNextPieces() {
        for (int p = 0; p < 2; p++) {
            JButton[] buttons = (p == 0) ? nextPieceButtonsO : nextPieceButtonsX;
            for (int k = 0; k < 4; k++) {
                int t = 2 * k + p;                  // turn number (0-7) this piece is played on
                int slot = (p == 0) ? 3 - k : k;    // O's first piece is the right edge, X's the left edge
                JButton b = buttons[slot];

                if (t < Turn) {
                    // already used: vanish, but keep the layout in place
                    b.setText("");
                    b.setContentAreaFilled(false);
                    b.setBorderPainted(false);
                } else {
                    b.setText(playerShape[p]);
                    b.setBackground(t >= 6 ? playerColor[(p + 1) % 2] : playerColor[p]);
                    b.setContentAreaFilled(true);
                    b.setBorderPainted(true);
                }
            }
        }
    }
}

