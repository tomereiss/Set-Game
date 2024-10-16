General Info:
There are 12 drawn cards that are placed on the table, and each player try to find many combinations of three cards that are a “legal set”. A set is legal, if for any of the features (color, number, shape, texture) the three cards are all the same or all different. The goal is to gain maximum "legal set" when the deck is finished. The players can declare set in the UI, by locating tokens at the slots in the boardGame. If on the declared slots, the cards consider a "legal set" then the player score one point and get penalty otherwise. 
There are 3 main objects in the project: dealer, player, and the board game (table).
The dealer is implemented as a single thread, which is the main thread in charge of the game flow. In addition, there is a thread for each player, and for “non-human” players- there is another thread to simulate the key presses. The boardGame is a shared resource of the dealer and the players, so in every change of the board the dealer declares that the board isn't ready until the changes have done.
Flow Summary of the project:
Initialization in main():
•	The main thread and logger are initialized.
•	Configuration and utility objects are created.
•	user interface and Environment are set up.
Create the main objects:
•	Create new boardGame with the set-up environment.
•	Create the array of players according to configuration.
•	Create new dealer and start its thread.
The dealer responsible for managing the flow of the game. Therefore, in the run method of dealer's thread the game loop is taking place. 
Dealer flow:
•	Creates and starts player threads.
•	Place cards for the first time on the boardGame.
•	Run the Game loop - Until timer is countered 0 and the game not terminated:
o	The dealer's thread sleeps until one of two options happen: (1) 1 second is pass so the dealer needs to update game's timer; (2) some action happened on the boardGame.
o	updates the timer.
o	Removes cards if set is being called or in case that all cards in the boardGame need to be shuffled due to end of timer (in default 60 sec). because boardGame has been changed, so the dealer deletes the tokens which on the removed cards from all players database and make changes in their actionQueues (fixPlayersActionsQueue (), fixPlayersTokens() methods).
o	If cards removed, then the dealer put more cards on the boardGame to replace the removed cards. 
o	If there are no valid sets on the boardGame and in the deck, then the dealer announces the winner. 
•	Announces winners and waits for player threads to finish.
Player flow:
•	Create artificial Thread if needed according to config, that will be responsible for generating key presses.
•	While the game not terminated, perform actions from the actionQueue. Actions such as key presses are added to this queue by the artificial thread or by the keyboard thread and processed sequentially. The process including adding and removing token from the boardGame. 
======================================================================================

Concurrency Handling in the Set Game Project
The Set game project involves complex concurrency management to handle multiple player threads, a dealer thread, and shared resources like the game table. Players call for "sets" simultaneously, requiring the dealer to manage these calls fairly. Additionally, the dealer must verify if the called "sets" are "legal sets" to determine the appropriate action: either awarding points or imposing a penalty. If a player is penalized, they receive a freeze time during which they cannot play. Once a player calls for a "set," they must wait for the dealer's response and cannot take any further actions in the game until the dealer has ruled. Here, we provide a detailed description of how concurrency is managed, focusing on the setClaimers blocking queue in the Dealer class, the actionsQueue in the Player class, and the mechanisms ensuring fair treatment of set claims:
Key Concurrency Components
Dealer Class:
•	Manages the overall game flow.
•	Handles the setClaimers queue where players claim sets.
•	Coordinates player threads and manages the game timer.
Player Class:
•	Each player runs in its own thread.
•	Uses an actionsQueue to manage player actions (e.g., key presses).
•	For non-human players, an additional AI thread generates actions.
Table Class:
•	Shared resource representing the game board.
•	Interacted with by both the dealer and player threads.


Detailed Analysis of Concurrency Handling
Set Claimers Queue – Dealer field:
setClaimers is a BlockingQueue used by players to claim a set. When a player believes they have found a set, they add their ID to the setClaimers queue and notify the dealer thread. Players wait (wait()) after claiming a set until the dealer processes their claim. ** players execute wait() in synchronize block with "this" as the key. This helpful when the dealer notify because it notify the player object. 
The dealer interrupts its sleep to handle set claims promptly, ensuring players are not left waiting indefinitely.
The dealer checks the setClaimers queue during its main loop. The dealer's thread polls this queue to process set claims (wait until player call for a set using Blocking Queue special method poll() – non blocking method). If a player claims a set, the dealer validates it, updates the game state, and responds to the player.
Using a BlockingQueue for set claims ensures that set claims are processed in a first-come, first-served order. This prevents race conditions where multiple players might claim a set at the same time.
The dealer notifies player threads (notifyAll()) after processing set claims to wake them up and continue gameplay. This mechanism ensures that players are promptly informed about the result of their set claim.
BoardReady – Dealer field:
every player has instance of the dealer object, therefore the variable boardReady is shared with all the objects (players and dealer). In every change of the board, the dealer changes the variable (Boolean) to false and then the players can't perform their actions from the actionQueue.
Actions Queue – Player field:
actionsQueue is a BlockingQueue used to manage player actions .Actions such as key presses are added to this queue by key board thread or by AI thread and processed sequentially.The player's thread take an action from the actionsQueue and perform it. This means basically to add or remove token from the boardGame. If no action in the queue, player's thread waits until key press performed (using Blocking Queue special method take() – blocking method).
playerIsAwake – Player field:
The AI thread waits if the board is not ready, or the player is not awake. The variable sets to false when the player claim for "set".
**Synchronized AI Thread:**
The AI thread waits if the board is not ready, or the player is not awake (penalty or wait to dealer's response). Therefore, in putCardsOnTable in the dealer and in callSet in the player, at the end of the function the dealer/player thread notify the AI thread to continue generating key presses. 
