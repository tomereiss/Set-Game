package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;


class DealerTest {
    private Dealer dealer;
    private Table table;
    private Player[] players;

    @BeforeEach
    void setUp() {
        Properties properties = new Properties();
        properties.put("Rows", "3");
        properties.put("Columns", "4");
        properties.put("FeatureSize", "3");
        properties.put("FeatureCount", "4");
        properties.put("TableDelaySeconds", "0");
        properties.put("PlayerKeys1", "81,87,69,82,65,83,68,70,90,88,67,86");
        properties.put("PlayerKeys2", "85,73,79,80,74,75,76,59,77,44,46,47");
        TableTest.MockLogger logger = new TableTest.MockLogger();
        Config config = new Config(logger, properties);
        Integer[] slotToCard = new Integer[config.tableSize];
        Integer[] cardToSlot = new Integer[config.deckSize];
        Env env = new Env(logger, config, new TableTest.MockUserInterface(), new TableTest.MockUtil());
        table = new Table(env, slotToCard, cardToSlot);

        Player playerOne = new Player(env, dealer, table, 0, true);
        Player playerTwo = new Player(env, dealer, table, 1, true);
        Player playerThree = new Player(env, dealer, table, 2, true);

        players = new Player[3];
        players[0] = playerOne;
        players[1] = playerTwo;
        players[2] = playerThree;

        dealer = new Dealer(env, table, players);

    }


    @Test
    void announceWinners() {
        // giving playerOne 2 point
        players[0].point();
        players[0].point();

        // giving playerTwo 4 points
        players[1].point();
        players[1].point();
        players[1].point();
        players[1].point();

        // giving playerThree 4 points
        players[2].point();
        players[2].point();
        players[2].point();
        players[2].point();

        // playerTwo and playerThree are both winners
        int winnerCounter = dealer.announceWinners();
        assertEquals(2, winnerCounter); // two winners are expected

        // giving playerOne total of 5 points so he's the winner
        players[0].point();
        players[0].point();
        players[0].point();

        // after using announceWinner one time we are expecting for 0 in next time
        winnerCounter = dealer.announceWinners();
        assertEquals(0, winnerCounter); // PlayerOne is winner
    }

    @Test
    void removeAllCardsFromTable() {
        dealer.removeAllCardsFromTable();

        // making sure boardReady boolean was changed to false
        assertFalse(dealer.boardReady);

        //making sure the table array was cleared
        for (int i=0; i<table.slotToCard.length; i++)
            assertNull(table.cardToSlot[i]);

        //making sure the table array was cleared
        for (int i=0; i<table.slotToCard.length; i++)
            assertNull(table.slotToCard[i]);

        for(Player player : players){
            assertEquals(0, player.actionsQueue.size());
            assertEquals(0, player.getTokens().size());
            assertEquals(Player.Verdict.IRRELEVANT, player.answer);
        }

        //making sure setClaimers queue was cleared
        assertEquals(0, dealer.setClaimers.size());
    }

}