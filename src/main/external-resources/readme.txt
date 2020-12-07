***************************************************************
* Mediocre v${project.version} by Jonatan Pettersson
*
* Blog:        http://mediocrechess.blogspot.com/
* Homepage:    http://mediocrechess.sourceforge.net/
* Sourceforge: http://sourceforge.net/projects/mediocrechess/
*
* E-mail:      mediocrechess@gmail.com
*              zlaire@gmail.com (main mail)
***************************************************************

------------
Introduction
------------

Mediocre is the result of a chess programming blog (http://mediocrechess.blogspot.com/).
In the blog every aspect added to Mediocre is described in detail as it develops.

Mediocre is written in Java and as of v0.4 only supports the UCI protocol.

------------
Installation
------------

Simply unpack the archive and use Mediocre.bat or mediocre_v${project.version}.exe to run.

* mediocre_v0.4.exe is a Windows compile which should be significantly stronger.

* Mediocre.bat runs the jar-file which needs Java installed on the computer. See the Settings-section
in this file to read how to adjust the -Xmx1024M flag.

Note: The windows executable version of Mediocre (mediocre_v${project.version}.exe) is compiled by Jim Ablett
and may not be present in all distributions of Mediocre.

--------
Settings
--------

As of version 0.4 Mediocre uses the UCI protocol to adjust its settings, the possible options are

Hash 1-512 (default 16) ........... - Size of main transposition table (in megabytes)
EvalHash 1-32 (default 8) ......... - Size of eval hash table (in megabytes)
PawnHash 1-32 (default 8) ......... - Size of pawn hash table (in megabytes)
Ponder true/false (default false) . - Turn pondering on and off
OwnBook true/false (default true) . - Turn use of own book on and off*

* OwnBook looks for a polyglot book called performance.bin in the same directory as Mediocre.

-------
Console
-------

Mediocre is as of version 0.4 starting in UCI mode. But there are plenty of built in commands that
can be use if started in console mode. This is done by using the -console argument at startup like this:

> mediocre_v0.4.exe -console

Or alternatively for the Java version edit the Mediocre.bat file by adding -console to the end of the line

Note: The console commands are still in development and might not work as expected

--------------
Change History
--------------
     v0.5: * Futility pruning working, should give a noticeable strength increase
           * Added tapered eval and some significant changes to evaluation, shouldn't matter to much in terms of strength or style though
           * Major background changes in the build process and so on (now using Maven)
           
     v0.4: * Any hash move used is now verified, this fixes a very rare occurrence of Mediocre crashing
           * The transposition table is now using the full 64 bit zobrist keys
           * The search was completely rewritten, possibly catching some bugs. Should show help quite a bit in playing strength
           * Ponder implemented
           * Removed the dependency of a settings file, things like hash sizes are now done through the UCI protocol
           * Removed the semi-working xboard protocol entirely. Sorry.

    v0.34: * Fixed a bug in the reptition detection table (repetitions were not replaced so the table would eventually fill up)
           * Fixed a bug in SEE (where black king's attack squares were added to white's side)
           * Fixed a problem with loading the opening book (perfomance.bin was hard coded and hence the only opening book name that was accepted)
           * Fixed the perft-command at line input (wouldn't show the number)
           * Fixed a bug where endgame piece square tables always were used after opening phase
           * Fixed a bug in the xboard protocol where time was reported in milliseconds (not centiseconds)
           * Fixed a bug in the UCI protocol when the same position was analyzed numerous times (reptable was not updated)
           * Fixed a bug with probing pawn eval when no pawns were on the board
           * Fixed a bug with the draw probability in evaluation (it simply wasn't used)
           * Fixed contempt factor, should work now
           * Move generation slightly optimized
           * Some small optimizations here and there
           * Aspiration window researches now only resets the exceeded limit
           * This document was updated to more accurately explain how to install Mediocre in Winboard.

   v0.334: * Mediocre now supports Polyglot opening books
           * It is now possible to give the path to a polyglot opening book in the ini-file
           * Some more code cleaning was made

   v0.333: * Mediocre now comes in a jar-executable and a Windows executable
           * The perft-class has a main-method that can be used to run a standard perft testing procedure
           * The readme was revised

   v0.332: * Fixed a problem regarding fen-strings using the UCI protocol (thanks to Phokham Nonava)
           * Mediocre now checks for legality of inputted moves in both the UCI and Winboard protocol (thanks to Volker Pittlik)

   v0.331: * Fixed a bug that caused the feature command not being recognized by Winboard, and the first id command
             not being recognized by Shredder and Chessbase

    v0.33: * Eval hash added
           * Pawn hash added
           * The zobrist keys are now pseudorandom, meaning they stay the same when restarting the application
           * Pawn zobrist key added to the Board class
           * Pawn eval divided between structure and passed pawns to fit with the pawn hash
           * Switched name between 'depth' and 'ply' in the search, depth is the number of plies left to search
             ply is the number of plies deep the search currently is
           * Fractional plies added
           * Matethreat extension and pawn to 7th rank extension added
           * It is now possible to change the transposition table size in the mediocre.ini file

    v0.32: * Killer moves verification to avoid generating unnecessary moves
           * The finalEval is now started with globalBestMove to make sure we always get the right move there
             (I'm not sure this was ever a problem, but just in case)
           * Passed pawn values halfed in middle/opening
           * Fixed x-ray attacks for queens
           * Simplified castling rights code
           * Added time search to line input and fixed some bugs
           * Dynamic null move depth
           * Futility check moves outside while loop
           * Fixed bug with black queen on 2nd rank bonus
           * Increased bonus for rook on 2nd/7th rank
           * Searched moves instead of HASH_EXACT to determine LMR and PVS (results in more reduced moves)
           * Hung piece check in the evaluation
           * Tempo bonus in evaluation

   v0.311: * Fixed a bug that made the engine enter an infinite loop if it was mated using the winboard protocol
           * Reworked the ended game draw detection using the winboard protocol (does not affect the search)

    v0.31: * Made a few arrays static in the SEE, speeding it up quite a bit
           * Futility pruning added
           * X-ray attacks in the evaluation (e.g. queen attacking empty squares behind own bishop)
           * Eval is adjusted towards 0 if a draw is likely
           * Pawns are valued slightly less but becomes a bit more valuable in endings
           * Knight outposts added
           * King tropism added
           * Rook/queen on 7th rank only awarded if enemy king on 8th or enemy pawns still on 7th
           * Fixed bug with bishop positioning and also one with pawn evaluation

     v0.3: * Mediocre is now stable enough to leave the beta, this is the frist 'sharp' version
           * Complete redesdign of the evaluation
           * In line input mode the command 'eval' now prints a list of the results of the
             different evaluation segments
           * New piece lists that store every type of piece in its own list, no more looping over the pawns
             to check for promoted pieces
           * Simple late move reduction added, every move after the first four are reduced with one ply
             if they were not a capture or a check
           * Static exchange evaluation added, used for sorting of both ordinary and quiescent moves
           * Losing quiescent captures (captures that lose material) are now pruned
           * New move generation methods gen_caps and gen_noncaps replaces the old generatePseudoMoves
           * Sorting moves is now handled inside the alpha-beta method, it is done step by step so
             we do not have to generate all moves up front, but rather one portion at a time
           * Move legality (not leaving king in check) is now verified when the pseudo-legal move is being
             played in the alpha-beta method instead of verifying all moves up front
           * With the new move sorting all non-losing captures are searched before the killer moves,
             the killer moves can only be non-captures now
           * The UCI protcol would crash the engine if an illegal move was sent in the position string,
             while this really should not happen Mediocre now recognizes illegal moves and sends an error
             message instead of crashing

  v0.241b: * Internal iterative deepening was added along with hash move search before generating moves       
           * The aspiration windows were reduced to 10 centipawns
           * The evaluation output was changed back to show the eval from the engine's point of view
             (in accordance with the winboard and uci protocols)

   v0.24b: * The evaluation was readjusted, resulting in quite an improvement in overall strength
           * Fixed an issue with the force mode in winboard, was not leaving force mode on the 'new' command
           * Added searchRoot() that works a bit differently on root nodes than ordinary alpha-beta,
             this should take care of the rare occasions where Mediocre did not have a move to make
           * The thinking output now shows white ahead as positive value and black ahead as negative value,
             the evaluation output no longer depends on what side the engine is playing
           * Fixed the contempt factor again, hopefully it works now (slightly negative number for the
             draw if white is moving, and slightly positive if black)

  v0.232b: * Mediocre now automatically detects what interface is being used, no parameter is needed

  v0.231b: * A bug in the new 'game over' check for winboard that made the engine crash was fixed
           * The transposition tables now use depth/new replacement scheme

   v0.23b: * Completely new evaluation, now accounts for king attacks, pawn evaluation and more
           * Check extension added
           * Draw repetition is now accounted for in the search tree as well
           * A bug concerning contempt factor was fixed
           * Piece lists added, no more 120 loops
           * Now gives correct mate count (and not just a high number) for both uci and winboard,
             evaluations from the transposition tables gives the count when the mate was found
             (this is on the todo-list)
           * The broken 'force' mode in winboard should now be fixed
           * Winboard protocol now sends the score when the game is finished (still needs some work)

   v0.22b: * Fixed a bug in the isAttacked method that made it do unnescessary checks
           * Changed the way unmake move works. The Board-class now has a history array where
             it keeps track of passed positions instead of using the Move-objects
           * The Board-class now keeps track of passed Zobrist keys so we do not have to
             'unmake' them when unmaking a move
           * The alphaBeta() does not generate moves at leaf nodes anymore
           * Changed ordering values for hash moves and killer moves so they always come
             first and second (at high depths the history moves sometimes came first)
           * Fixed an issue with reporting time using the UCI protocol
           * The Board-class now keeps track of king positions so we do not need to loop
             through the boardArray to find them
           * Changed move generation to use arrays instead of Vectors. generateMoves() now
             fills a given array from a certain index and returns the number of moves that
             were added
           * Alpha-beta and quiescent search now uses a common big array for storing
             generated moves instead of creating many small local arrays
           * Moves are now represented by an integer instead of a Move-object
           * The Move-class was completely rewritten and now only contains static
             methods for analyzing the new move integer
           * Fixed yet another issue with en passants (hopefully they work now)
           * The transposition table now uses an integer array instead of using Hashentry
             objects. Many new methods for retrieving different values were added

   v0.21b: * Added support for 'infinite' search in uci (not applicable in winboard)
           * Added support for fixed time per move search for both uci and winboard
           * Added support for fixed depth search for both uci and winboard
           * Added support for the '?' (winboard) and 'stop' (uci) move-now commands,
             the 'move now' command in certain interfaces make Mediocre move immediately
           * Made null-moves only possible if the game is not in a pawn ending
           * Introduced contempt factor to draw values (dependant on game phase)
           * Added a game phase check that decides if the game is in the opening,
             middle game, ending or pawn ending
           * Added final INFINITY variable instead of using 200000 in the code
           * Cleaned up the line input, currently it can only set up a board with FEN
             and do perft/divide, might be enhanced again in later releases
           * Increased the number of nodes before time or interrupt command are checked,
             hopefully this should take care of the bug where Mediocre stops searching

   v0.2b:  * Transposition tables are implemented along with a new draw detection scheme
           * There is now a zobrist key field in the Board-class, the key is updated when
             making and unmaking moves, but can also be generated from scratch with the
             static getZobristKey-method in the Zobrist-class
           * Changed pv search so it is only used if a best move has been found
           * Sorting moves is now done with a much faster bubble-sort method
           * Changed move generation slightly and added a new filterQuis-method which adds
             the right values to the captures
           * Quiescent search now re-uses the moves generated in the alphaBeta method
           * Mediocre is now developed in Eclipse so the folders look a bit different
           * The .bat-files now point to "/bin/" so they work as long they are kept in the
              original folder (you should not have to update the path in them anymore)
           * Sorting methods are completely rewritten, based on orderingValue
           * Preliminary orderingValues are added to moves at generation time
           * Move-objects now have a orderingValue used in sorting
           * Implemented fixed depth search, call Engine.search() with depth>0 to use,
             call with depth=0 for time control mode
           * The line input can now be used to play games with fixed depth (5 for now)
             if you enter moves on the form "e2e4" etc. It is still very crude and is
             supposed to be used for testing
           * Plus many minor tweaks and changes

   v0.12b: * Time mangement added
           * UCI support added
           * Machine vs. Machine in winboard should now work
           * Added support for the 'force' command in winboard
           * Fixed a bug conerning en passant, perft values are now correct
           * Perft and Tests classes were added, these are best called in the new line mode
           * Calling Mediocre without argument now enters a simple line mode
           * Added a few parameters to Engine.search() to allow uci/winboard thinking,
             fixed depth and time management
           * Added setupStart() to Board, which sets up the initial position

   v0.11b: * Null-moves added
           * Added isInCheck() to the engine class (used by null-moves)
           * Move sorting is now done after the mate check to save time

    v0.1b: * Aspiration windows added
           * Killer moves heuristic added
           * Principal Variation Search added
           * A new opening book, shorter but lets the engine play itself better
