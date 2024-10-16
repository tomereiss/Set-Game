package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import bguspl.set.UserInterface;
import bguspl.set.Util;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerTest {

    Player player;
    @Mock
    Util util;
    @Mock
    private UserInterface ui;
    @Mock
    private Table table;
    @Mock
    private Dealer dealer;
    @Mock
    private Logger logger;

    void assertInvariants() {
        assertTrue(player.id >= 0);
        assertTrue(player.human);
    }

    @BeforeEach
    void setUp() {
        // purposely do not find the configuration files (use defaults here).
        Env env = new Env(logger, new Config(logger, (String) null), ui, util);
        player = new Player(env, dealer, table, 0, true);
        assertInvariants();
    }

    @AfterEach
    void tearDown() {
        assertInvariants();
    }

    @Test
    void point() {
        // calculate the expected score for later
        int expectedScore = player.getScore() + 1;

        // call the method we are testing
        player.point();

        // check that the score was increased correctly
        assertEquals(expectedScore, player.getScore());

        // check that ui.setScore was called with the player's id and the correct score
        verify(ui).setScore(eq(player.id), eq(expectedScore));

        // check that answer was updated correctly
        assertEquals(Player.Verdict.POINT, player.answer);
    }

    @Test
    void terminate() {
        // calculate the expected score for later
        boolean expected = true;

        // call the method we are testing
        player.terminate();

        // check that the boolean was changed correctly
        assertTrue(expected, String.valueOf(player.terminate));
    }

    @Test
    void penalty() {
        // call the method we are testing
        player.penalty();

        // check that answer was updated correctly
        assertEquals(Player.Verdict.PENALTY, player.answer);
    }

    @Test
    void irrelevant() {
        // call the method we are testing
        player.irrelevant();

        // check that answer was updated correctly
        assertEquals(Player.Verdict.IRRELEVANT, player.answer);
    }
}