package bguspl.set.ex;

import bguspl.set.Env;

import java.util.*;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * Array of Player Objects
     */
    private final Player[] players;

    /**
     * Dealer Thread Object
     */
    protected Thread dealerThread;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * Boolean used for marking that game was ended before timer ends.
     * Due to lack of valid Sets on board and deck
     */
    private boolean relevant;

    /**
     * Sleep time of dealer during normal timer
     */
    private final int normalSleepTime;

    /**
     * Sleep time of dealer during timeout warnings seconds timer
     */
    private final int shortSleepTime;

    /**
     * Queue used for player id who clamied 'Set'
     */
    protected final BlockingQueue<Integer> setClaimers; // mutual resource representing players which are claiming 'Set'

    /**
     * Boolean used for signing whether board is ready or not/
     */
    protected boolean boardReady;

    /**
     * Integer used for timer logic
     */
    private long timestamp;

    /**
     * Integer used for sampling system time
     */
    private long timer;

    /**
     * Array pf player threads
     */
    Thread [] playerThreads;

    /**
     * Boolean used for bonus mission - dealer shuffles cards if board does not contain Set
     */
    private final boolean ensureSetOnTable = true;

    /**
     * Constructor of Dealer
     */
    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        this.deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        this.setClaimers = new LinkedBlockingQueue<>();
        this.boardReady = false;
        this.relevant = true;
        this.playerThreads = new Thread[players.length]; // create threads for players.
        this.normalSleepTime = 500;
        this.shortSleepTime = 10;
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        dealerThread = Thread.currentThread();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting."); // updating log that dealer thread started

        for (int i = 0; i < players.length; i++) { // creating and starting player threads
            playerThreads[i] = new Thread(players[i], "player " + players[i].id);
            playerThreads[i].start();
        }

        while (!terminate) {
            placeCardsOnTable();
            timerLoop(); // game loop
            updateTimerDisplay(false);
            if(relevant) // a case of external terminate
                removeAllCardsFromTable(); //  removing all the cards from table and UI
        }
        announceWinners(); // announcing Winners

        // waiting for player threads to end
        for (int i = players.length-1; i >= 0; i--) {
            try {
                playerThreads[i].join();
            }catch (InterruptedException ignored){}
        }
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated."); // updating log that dealer thread ended
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        timestamp = System.currentTimeMillis(); // time of thread start
        while (!terminate && timer >= 0) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            if(removeCardsFromTable()){ // removing cards if needed
                placeCardsOnTable(); // placing new cards if available
            }
            updateTimerDisplay(false);
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() { // make the dealer sleep until timeout or element added to setClaimers queue
        try {
            if(timer > env.config.turnTimeoutWarningMillis) { // Regular sleep time or interrupted for some action
                dealerThread.sleep(normalSleepTime);
            }
            else
                dealerThread.sleep(shortSleepTime); // Short sleep time or interrupted for some action
        }
        catch (InterruptedException ignored) {
                    env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " interrupted while wait for setCall");}
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if(!reset){
            long temp = System.currentTimeMillis() - timestamp;
            if(timer>=env.config.turnTimeoutWarningMillis){
                if(temp >= normalSleepTime){
                    timestamp = System.currentTimeMillis(); // sampling timestamp
                    env.ui.setCountdown(timer,false); // updating UI timer
                    timer -= temp;
                }
            }
            else{ // last turnTimeoutWarningMillis (usually 5 seconds in config)
                if(timer>0){
                    if(temp>=shortSleepTime){
                        timestamp = System.currentTimeMillis(); // sampling timestamp
                        env.ui.setCountdown(timer,true); // updating UI timer
                        timer -= temp;
                    }
                }
            }
        }
        else{
            timer = env.config.turnTimeoutMillis;
            env.ui.setCountdown(timer,false);
            timestamp = System.currentTimeMillis();
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     * Includes mode of shuffling board cards if none available Sets on table.
     */
    private void placeCardsOnTable() {
        for(int slot=0; slot<table.slotToCard.length; slot++){
            if(deck.size() > 0 && table.slotToCard[slot] == null ) { // empty places on table and deck isn't empty
                int card = deck.remove((int)Math.floor(Math.random()*(deck.size()))); // removing random card from the deck
                table.placeCard(card,slot);   // updating table on the array and updating display
            }
        }
        table.hints();
        updateTimerDisplay(true);
        List<Integer> cardsToCheck = Arrays.stream(table.slotToCard).filter(Objects::nonNull).collect(Collectors.toList()); // creating list from table slots

        if(!ensureSetOnTable){ // Regular Mode
            boardReady = true; // board is ready for game - contains legal set
            for(int i=0; i<players.length; i++) {
                if (!players[i].human) {
                    synchronized (players[i].aiThread) {
                        players[i].aiThread.notifyAll(); // waking up AI threads
                    }
                }
            }
            cardsToCheck.addAll(deck); // looking for set including the deck into the check
            if(env.util.findSets(cardsToCheck, 1).size() == 0) //no more sets in deck and on table so announceWinners
                announceWinners();
        }

        else{ // Special Mode for bonus mission - shuffle if set isn't exist on the table
            if(env.util.findSets(cardsToCheck, 1).size() > 0){ // there is at least one set on the table
                boardReady = true; // board is ready for game - contains legal set
                for(int i=0; i<players.length; i++){
                    if(!players[i].human){
                        synchronized (players[i].aiThread) {
                            players[i].aiThread.notifyAll(); // waking up AI threads
                        }
                    }
                }
            }
            else{
                cardsToCheck.addAll(deck);  // looking for set including the deck into the check
                if(env.util.findSets(cardsToCheck, 1).size()==0)//no more sets in deck and on table
                    announceWinners();
                else{
                    removeAllCardsFromTable();
                    placeCardsOnTable();
                }
            }
        }
    }

    /**
     * Checks which cards should be removed from the table and removes them.
     * Ensures that all players actionQueues and tokens list are updated.
     */
    private boolean removeCardsFromTable() {// go to the sync queue check if there is a set and removed the card from the table.
        Integer player = -1;
        boolean ans = false;
        player = setClaimers.poll(); // wait until player call for a set (using Blocking Queue special method poll)
        if(player!=null){
            int[] setSlots = new int[players[player].getTokens().size()];
            for(int i=0; i<setSlots.length; i++) // creating array from players tokens list
                setSlots[i] = players[player].getTokens().get(i);

            int[] setCards = new int[setSlots.length];
            for(int i=0; i<setCards.length; i++) //convert player's tokens to set of cards
                setCards[i] = table.slotToCard[setSlots[i]];

            if(env.util.testSet(setCards)){ // set is legal
                boardReady = false; // marking that the board isn't ready
                for (int slot : setSlots) {
                    table.removeCard(slot); // removing cards from table
                }
                fixPlayersActionsQueue(setSlots); // removing the slots from actionsQueue of each player if needed
                fixPlayersTokens(setSlots); // removing the card from other players tokens list
                players[player].point(); // point the player
                ans = true;
            }
            else{
                players[player].penalty();
            }
            synchronized(players[player]){ //waking the player after making a decision
                players[player].notify();
                env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " notified " + player + " with the answer " + players[player].answer);
            }
            return ans;
        }
        return false;
    }

    /**
     * Removing the tokens of a given set from all players tokens list.
     */
    private void fixPlayersTokens(int[] slots){ // removing the verified set from players tokens.
        for (int i=0; i<players.length; i++) { // iterating through all players
            boolean remove = false;
            for(Integer slot : slots){ // iterating through the given Set slots
                if (players[i].tokens.contains(slot)) {
                    players[i].tokens.remove(slot);
                    remove = true;
                }
            }
            if(remove){ // removing a player from setClaimers queue and waking him up with 'IRRELEVANT' answer
                Integer id = players[i].id;
                synchronized (setClaimers) {
                    if(setClaimers.remove(id)){ // remove returns true if removal was done successfully
                        synchronized (players[i]){
                            players[i].irrelevant();
                            players[i].notifyAll(); // waking the player
                        }
                    }
                }
            }
        }
    }

    /**
     * Removing the Set slots from player's next actions queue.
     */
    private void fixPlayersActionsQueue(int[] slots){ // removing irrelevant slots from players queue
        for(int i=0; i<players.length; i++){ // iterating through all players and building new actionsQueue for them
            if(!players[i].actionsQueue.isEmpty()){
                Iterator<Integer> it = players[i].actionsQueue.iterator(); // iterator for queue
                while (it.hasNext()) {
                    Integer temp = it.next();
                    for (int slot : slots) { // iterating through all slots
                        if (temp == slot) {
                            it.remove(); // removes the last element that was loaded by next() method.
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    void removeAllCardsFromTable() {
        boardReady = false; // marking that board is not ready
        synchronized(setClaimers){ // avoiding the player from calling a set
            for(int slot=0; slot<table.slotToCard.length; slot++){
                if(table.slotToCard[slot] != null){
                    int card = table.slotToCard[slot];
                    table.removeCard(slot);
                    deck.add(card); // put the card back in the deck
                }
            }
            setClaimers.clear(); // clearing the setClaimers queue
        }
        for(int i=0; i< players.length; i++){ // clearing all players tokens and actionsQueue
            players[i].tokens.clear();
            players[i].actionsQueue.clear();
            synchronized (players[i]){ // waking up all waiting players
                players[i].irrelevant();
                players[i].notifyAll();
            }
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    int announceWinners() {
        if(relevant){
            int maxScore = 0;
            int winnerCounter =0;
            for (Player player : players) { //getting the max score of the game
                if (player.getScore() >= maxScore)
                    maxScore = player.getScore();
            }
            for (Player player : players) { //counting the number of winners at the game
                if (player.getScore() == maxScore)
                    winnerCounter++;
            }
            int [] winners = new int[winnerCounter];
            int t=0;
            for(int i=0;i< players.length;i++){//creating the final winners array
                if(players[i].getScore() == maxScore) {
                    winners[t] = i;
                    t++;
                }
            }
            env.ui.announceWinner(winners);
            terminate();
            relevant = false;
            return winnerCounter;
        }
        return 0;
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        for (int i = players.length-1 ; i >= 0; i--) // terminating from last to first for bonus
            players[i].terminate();
        terminate = true;
    }
}