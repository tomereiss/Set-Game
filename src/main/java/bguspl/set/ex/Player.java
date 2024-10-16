package bguspl.set.ex;

import bguspl.set.Env;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import static java.lang.Thread.currentThread;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * Game dealer.
     */
    private final Dealer dealer;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    protected Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    protected final boolean human;

    /**
     * True iff game should be terminated due to an external event.
     */
    protected volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    /**
     * The queue of player next actions
     */
    protected final BlockingQueue<Integer> actionsQueue;

    /**
     * Enum representing an answer from the dealer
     */
    protected Verdict answer;

    /**
     * True iff player isn't frozen
     */
    private boolean playerIsAwake;

    /**
     * List of current player tokens on Table
     */
    protected final List<Integer> tokens;

    /**
     * Emum used for representing an answer from dealer
     */
    enum Verdict{
        POINT,
        PENALTY,
        IRRELEVANT
    }

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.dealer = dealer;
        this.score = 0;

        this.actionsQueue = new LinkedBlockingQueue<>(env.config.featureSize);
        this.answer = Verdict.IRRELEVANT;
        this.playerIsAwake = true;
        this.tokens = new ArrayList<>();
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = currentThread();
        env.logger.log(Level.INFO, "Thread " + playerThread.getName() + "starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) {
            performAction(); // performing actions from actionsQueue until calling for Set
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {} // waiting for aiThread to end
        env.logger.log(Level.INFO, "Thread " + playerThread.getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        aiThread = new Thread(() -> {
            env.logger.log(Level.INFO, "Thread " + currentThread().getName() + " starting.");
            int keyCode = 0;

            while(!terminate){
                if(playerIsAwake && dealer.boardReady) { // checking that key press is relevant at the moment
                    keyCode = (int) Math.floor(Math.random() * (env.config.tableSize)); // generating random between 0 to tableSize
                    keyPressed(keyCode); // takes actionQueue key
                }
                else{
                    synchronized (aiThread){
                        try {
                            aiThread.wait(); // waiting if board isn't ready or player is frozen
                        } catch (InterruptedException terminate){
                            Thread.currentThread().interrupt();}
                    }
                }
            }
            env.logger.log(Level.INFO, "Thread " + currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        terminate = true;
        if(!human) // interrupting the aiThread if non-human thread.
            aiThread.interrupt();
        if(playerThread != null)
            playerThread.interrupt(); // interrupting playerThread
    }

    /**
     * This method is called when a key is pressed.
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {//insert "remove token" or "adding token" action to the actionQueue
        if(playerIsAwake && dealer.boardReady && table.slotToCard[slot] != null)  { //check if there is a card on the slot or the queue is full
            actionsQueue.offer(slot); // adding slot into the blocking queue
        }
    }

    /**
     * This method is performing actions using the player's actionsQueue.
     */
    private void performAction(){
        if(dealer.boardReady) { // checking if the board is ready
            Integer currentAction = 0;
            try {
                currentAction = actionsQueue.take(); //if the blocking queue is empty, wait for an element
            }catch (InterruptedException terminate){
                Thread.currentThread().interrupt();
            }
            if(tokens.contains(currentAction)){ //case of removal from tokens list
                table.removeToken(id, currentAction);
                tokens.remove(currentAction);
            }
            else{ // case of addition into tokens list
                if(tokens.size() < env.config.featureSize){ //case of addition from tokens list
                    table.placeToken(id, currentAction);
                    tokens.add(currentAction);
                    if(tokens.size() == env.config.featureSize){ // calling for Set
                        playerIsAwake = false;
                        callSet();
                    }
                }
            }
        }
    }

    /**
     * This method is used for calling a set after placing three tokens
     */
    private void callSet() {
        synchronized(this) {
            try {
                dealer.setClaimers.put(id); // pushing player id into blocking queue
                dealer.dealerThread.interrupt();
                this.wait(); // waiting for dealer answer
            } catch (InterruptedException terminate) {
                Thread.currentThread().interrupt();
            }
        }
        if (answer==Verdict.POINT) // dealer answer for set was positive
            freeze(env.config.pointFreezeMillis); // point freeze

        else if(answer==Verdict.PENALTY){ // dealer answer for set was negative
            freeze(env.config.penaltyFreezeMillis); // penalty freeze
        }
        playerIsAwake = true; // allowing the player to make actions

        if(!human) { // waking up the aiThread using notifyAll
            synchronized (aiThread) {
                aiThread.notifyAll();
            }
        }
    }

    /**
     * This method is used for freezing a player according to dealers answer.
     * Player will be stuck inside this function until timer runs out
     */
    public void freeze(long timer){ //manage the freeze process (timer and display)
        env.ui.setFreeze(id,timer); // setting freeze in UI
        long timestamp = System.currentTimeMillis();
        while(timer>0){
            long temp = System.currentTimeMillis() - timestamp;
            if(temp >= env.config.pointFreezeMillis){
                timer -= temp;
                env.ui.setFreeze(id,timer); // updating freeze value in UI
                timestamp = System.currentTimeMillis();
            }
        }
        env.ui.setFreeze(id,0); // reset the freeze in UI
        answer = Verdict.IRRELEVANT; // reset the boolean
    }

    /**
     * Giving point to a player using Verdict enum
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() { //dealer calls that function
        score++; // adding 1 to score field
        env.ui.setScore(id, score); // updating the score on UI
        answer = Verdict.POINT; // changing the dealer answer - function being called by dealer
    }

    /**
     * Penalize a player using Verdict enum
     */
    public void penalty() { //dealer calls that function
        answer = Verdict.PENALTY;
    }

    /**
     * Marks that the set was irrelevant due to cards removal.
     * function being called by dealer.
     */
    public void irrelevant(){ // marks that the set was irrelevant due to cards removal. dealer calls that function.
        answer = Verdict.IRRELEVANT;
    }

    /**
     * Get function for player's score
     */
    public int getScore() {
        return score;
    }

    /**
     * Get function for player's tokens list
     */
    public List<Integer> getTokens(){
        return tokens;
    }

}
