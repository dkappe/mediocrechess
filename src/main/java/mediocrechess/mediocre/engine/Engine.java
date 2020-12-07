package mediocrechess.mediocre.engine;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mediocrechess.mediocre.board.Board;
import mediocrechess.mediocre.board.Evaluation;
import mediocrechess.mediocre.board.Move;
import mediocrechess.mediocre.board.See;
import mediocrechess.mediocre.main.Mediocre;
import mediocrechess.mediocre.main.Uci;
import mediocrechess.mediocre.main.Settings;
import mediocrechess.mediocre.def.Definitions;

public class Engine implements Definitions {
	@SuppressWarnings("unused")
	private static Logger logger = LoggerFactory.getLogger(Mediocre.class);
	
	public static final int TIME_CHECK_INTERVAL = 10000; // How often we should check if time is up inside alphaBeta
	public static final int PLY = 16; // Represents a full ply
	
	public static final int[] FUTILITY_VALS = {0, 120, 120, 310, 310, 400}; // Shamelessly stolen from Crafty

	private static int nodesSearched; // Nodes searched for current root move
	private static int totalNodesSearched; // The total number of nodes for the entire search
	private static long startTime; // The time we started searching at
	private static boolean stopSearch; // Used for hard stops
	private static int timeForThisMove; // Set if we are using a fixed time for every move
	private static Move[][] searchMoves; // This will be filled with moves to search
	private static int current_depth; // The depth we are currently searching to in the iterative deepening
	private static int nextTimeCheck; // Keeps track of when to check the time
	//private static int globalBestMove; // Keeps track of the best move (to not rely fully on the tt for this)
	private static boolean useFixedDepth; // Is this a fixed depth search or not?
	private static KillerMoves killers; // Keeps track of killer moves
	private static LineEval finalEval;
	private static int[][] historyValues;
	private static int[][] historyBetaHits;
	private static int rootMovesCount;
	private static boolean ponder;
	
	public static LineEval search(Board board, int depth, int timeLeft, int increment, int movetime, boolean isPonder) throws IOException {
		// Some initalizations
		finalEval = new LineEval(); // Stores the evaluation and principal variation
		searchMoves = new Move[64][256]; // Initialize and fill with move objects, (64 ply with 256 possible moves in each)
		for(int i = 0; i < searchMoves.length; i++)
			for(int j = 0; j < searchMoves[i].length; j++)
				searchMoves[i][j] = new Move();
		if(board.gen_allLegalMoves(searchMoves[0], 0) == 0) return finalEval;
		totalNodesSearched = 0;
		startTime = System.currentTimeMillis();
		stopSearch = false;
		if(movetime == 0) timeForThisMove = calculateTime(board, timeLeft, increment); // Calculate the time for this move
		else timeForThisMove = movetime;
		nextTimeCheck = TIME_CHECK_INTERVAL;
		useFixedDepth = depth != 0;
		killers = new KillerMoves();
		historyValues = new int[128][128];
		historyBetaHits = new int[128][128];
		ponder = isPonder;
		
		nodesSearched = 0;
		
		rootMovesCount = board.gen_allLegalMoves(searchMoves[0], 0);
		for(int i = 0; i < rootMovesCount; i++) {
			board.makeMove(searchMoves[0][i].move);
			searchMoves[0][i].score = -alphaBeta(board, 1*PLY, -INFINITY, INFINITY, false, 1);
			board.unmakeMove(searchMoves[0][i].move);
		}
		
		int alpha = -INFINITY;
		int beta = INFINITY;
		int researchAlphaCount = 0;
		int researchBetaCount = 0;

		// Iterative deepening
		for(current_depth = 1; current_depth <= 64;) {
			Move bestMove = alphaBetaRoot(board, current_depth*PLY, alpha, beta, true, 0);
			int eval = bestMove.score;

			if(stopSearch) {
				break;
			}
			
			if (eval <= alpha) {
				if(researchAlphaCount == 0) {
					alpha -= 100;
					researchAlphaCount++;
				} else if(researchAlphaCount ==1) {
					alpha -= 200;
					researchAlphaCount++;
				} else {
					alpha = -INFINITY;
					researchAlphaCount++;
				}
			  continue;
			} else if (eval >= beta) {
				if(researchBetaCount == 0) {
					beta += 100;
					researchBetaCount++;
				} else if(researchBetaCount ==1) {
					beta += 200;
					researchBetaCount++;
				} else {
					beta = INFINITY;
					researchBetaCount++;
				}
			  continue;
			} else if (bestMove.move == 0) {
				// No root move achieved > alpha so need to open the window
				alpha = -INFINITY;
				beta = INFINITY;
				continue;
			}
			
			finalEval = new LineEval(Settings.getInstance().getTranspositionTable().collectPV(board, current_depth), eval); // Record the evaluation and principal variation, flip the eval if it's black to move so black ahead is always negative
			finalEval.line[0] = bestMove.move;
			
			
			System.out.println(receiveThinking(startTime, finalEval)); // Get a thinking string and send
			
			alpha = eval -60; // Get ready for a new search, with a new window
			beta = eval +60;  // The current window equals 3/10 of a pawn
			researchAlphaCount = 0;
			researchBetaCount = 0;
			
			if(alpha <= -INFINITY) alpha = -INFINITY;
			if(beta >= INFINITY) beta = INFINITY;
			
			if(!ponder) {
				if(useFixedDepth) {
					if(current_depth == depth || eval == -(MATE_VALUE +1)) break;
				} else if(movetime != 0) {
					if(System.currentTimeMillis() - startTime > timeForThisMove || eval == -(MATE_VALUE +1)) break; // We have reached the allocated time or found a mate, and exit
				} else	{
					// If we used 90% of the time so far, we break here
					if(System.currentTimeMillis() - startTime > timeForThisMove*0.9 || eval == -(MATE_VALUE +1)) {
						break; // We have reached the allocated time or found a mate, and exit
					}
				}
			}
			current_depth++; // Go to the next depth
		}
		
		return finalEval;
	} // END search
	
	public static Move alphaBetaRoot(Board board, int depth, int alpha, int beta, boolean allowNull, int ply) throws IOException {
		Move bestMove = new Move(); // Initialize the best move
		int eval = 0; // Initialize the eval
		int bestEval = -INFINITY;
		int eval_type = HASH_ALPHA;
		int searchedMoves = 0; // Number of moves that have been searched, more than 1 will enable pvs, 0 at the end of move generation will result in a draw/mate check

		// Swap previous pv move to front
		for(int i = 0; i < rootMovesCount; i++) {
			if(searchMoves[0][i].move == finalEval.line[0]) {
				Move tempMove = searchMoves[0][0];
				searchMoves[0][0] = searchMoves[0][i];
				searchMoves[0][i] = tempMove;
			}
		}
		
		sortMoves(searchMoves[0], depth/PLY==1 ? 0 : 1, rootMovesCount);
		
		// Go through the generated moves one by one
		for(int i = 0; i < rootMovesCount; i++) {

			board.makeMove(searchMoves[ply][i].move); // Make the move on the board

			// Report what move we're looking at currently
			if((depth/PLY > 10 && !stopSearch && timeForThisMove > 1000 && System.currentTimeMillis() - startTime > timeForThisMove*0.5)) {
				System.out.println("info currmove " + Move.inputNotation(searchMoves[ply][i].move) + " currmovenumber " + searchedMoves);
			}
			
			if(searchedMoves >= 1) {
				if(searchedMoves > 3 &&
						depth > 3*PLY &&
						!board.isInCheck()) {
					eval = -alphaBeta(board, depth-(2*PLY), -alpha-1,-alpha, true, ply+1);
				} else {
					// 	PVS search
					eval = -alphaBeta(board, depth-PLY, -alpha -1, -alpha, true, ply+1);
				}

				if(eval > alpha && eval < beta) {
					// Full depth search
					eval = -alphaBeta(board, depth-PLY, -beta, -alpha, true, ply+1);
				}
			} else {
				eval = -alphaBeta(board, depth-PLY, -beta, -alpha, true, ply+1);
			}

			searchedMoves++;

			board.unmakeMove(searchMoves[ply][i].move); // Reset the board
			
			// Update sorting
			searchMoves[ply][i].score += nodesSearched;
			totalNodesSearched += nodesSearched;
			nodesSearched = 0;

			if(eval > bestEval)	{

				bestEval = eval;


				// If the evaluation is bigger than alpha (but less than beta) this is our new best move
				if(eval > alpha) {
					bestMove.move = searchMoves[ply][i].move;
					bestMove.score = eval;
					eval_type = HASH_EXACT;
					alpha = eval;
				}
			}
		}// End for loop


		// If there wasn't a legal move, it's either stalemate or checkmate
		if(searchedMoves == 0) {
			if(board.isInCheck()) bestMove.score = (MATE_VALUE+ply);
			else bestMove.score = DRAW_VALUE;
		}

		Settings.getInstance().getTranspositionTable().record(board.zobristKey, current_depth, eval_type, bestEval, bestMove.move);
		
		return bestMove;
	} // END alphaBetaRoot
	
	public static int alphaBeta(Board board, int depth, int alpha, int beta, boolean allowNull, int ply) throws IOException {
		int bestMove = 0; // Initialize the best move
		int eval = 0; // Initialize the eval

		// Check if we've run out of time
		if(!useFixedDepth) {
			nextTimeCheck--;
			if(nextTimeCheck == 0) {
				nextTimeCheck = TIME_CHECK_INTERVAL;
				if(shouldWeStop()) {
					stopSearch = true;
					return 0;
				}
			}
		}

		// If we're not in a root node and there's a threefold repetition detected, or the fifty move rule is reached (in any node) return draw
		if((depth/PLY != current_depth && Settings.getInstance().getRepTable().repExists(board.zobristKey)) || board.movesFifty >= 100) {
			return DRAW_VALUE;
		}
		
		

		// Check if the value in the hashtable was found at same or higher depth search
		if(Settings.getInstance().getTranspositionTable().entryExists(board.zobristKey) && Settings.getInstance().getTranspositionTable().getDepth(board.zobristKey) >= depth/PLY) {
			if(Settings.getInstance().getTranspositionTable().getFlag(board.zobristKey) == HASH_EXACT) {
				// Since this is stored as an exact value we can use it right away
				searchMoves[ply][0].move = Settings.getInstance().getTranspositionTable().getMove(board.zobristKey);
				return Settings.getInstance().getTranspositionTable().getEval(board.zobristKey);
			} else if(Settings.getInstance().getTranspositionTable().getFlag(board.zobristKey) == HASH_ALPHA && Settings.getInstance().getTranspositionTable().getEval(board.zobristKey) <= alpha) {
				// Since this was stored as an alpha value and it's less than the current alpha (i.e. greater than the beta since they've been swapped for this level, = opponent wouldn't go down this path) we can use cut off here
				searchMoves[ply][0].move = Settings.getInstance().getTranspositionTable().getMove(board.zobristKey);
				return Settings.getInstance().getTranspositionTable().getEval(board.zobristKey);
			} else if(Settings.getInstance().getTranspositionTable().getFlag(board.zobristKey) == HASH_BETA && Settings.getInstance().getTranspositionTable().getEval(board.zobristKey) >= beta) {
				// Since this was stored as a beta and is greater than the current beta (i.e. less than alpha = there is atleast one better move already found) we can cut off here
				searchMoves[ply][0].move = Settings.getInstance().getTranspositionTable().getMove(board.zobristKey);
				return Settings.getInstance().getTranspositionTable().getEval(board.zobristKey);
			}
			

		}

		boolean isInCheck = board.isInCheck();
		if(isInCheck) {
			depth += PLY;
		}
		

		// We've reached the deepest ply so start the quiescent search
		if(depth/PLY <= 0) {
			eval = quiescentSearch(board, alpha, beta, ply);
			
			// We've gotten an eval for this position so store it depending how it compares to alpha/beta
			/*if(eval >= beta) Settings.getInstance().getTranspositionTable().record(board.zobristKey, depth/PLY, HASH_BETA, eval, 0);
			else if(eval <= alpha) Settings.getInstance().getTranspositionTable().record(board.zobristKey, depth/PLY, HASH_ALPHA, eval, 0);
			else Settings.getInstance().getTranspositionTable().record(board.zobristKey, depth/PLY, HASH_EXACT, eval, 0);*/
			return eval;
		}
		
		// Null move
		boolean threat = false;
		if (beta - alpha <= 1 && // non-PV node
				allowNull && // Don't do two null moves in a row
				!isInCheck &&
				depth > PLY &&
				Evaluation.getGamePhase(board) != PHASE_PAWN_ENDING) { 
			
			int R = (depth > 6*PLY) ? PLY*3 : PLY*2;
			
			board.nullmoveToggle();
			eval = -alphaBeta(board, depth-PLY-R, -beta, -beta+1, false, ply+1);
			board.nullmoveToggle();
			
			if(eval >= beta) {
				return eval;	
			}
			if(eval <= -MATE_BOUND)	{
				threat = true;
			}
		}
		
		if(stopSearch) return 0; // Stop the search if it's been detected

		int hashMove =  Settings.getInstance().getTranspositionTable().getMove(board.zobristKey);
		
		if(hashMove == 0 && beta - alpha > 1 && depth/PLY >= 5) {
			alphaBeta(board, depth-2*PLY, alpha, beta, false, ply+1);
			hashMove = searchMoves[ply+1][0].move;			
		}
		
		if(hashMove != 0 && !board.validateHashMove(hashMove)) {
			hashMove = 0;
		}
		
		Settings.getInstance().getRepTable().recordRep(board.zobristKey);
		
		int generationState = GEN_HASH;
		int tempMove;
		int bestEval = -INFINITY;
		int eval_type = HASH_ALPHA;
		int searchedMoves = 0; // Number of moves that have been searched, more than 1 will enable pvs, 0 at the end of move generation will result in a draw/mate check
		int currentMovesCount = 0;
		int capturesCount = 0;
		int startLosingCaptures = -1;
		int startIndex = 0;
		int killerOne = 0;
		int killerTwo = 0;
		int killerOneOld = 0;
		int killerTwoOld = 0;

		int materialEval = 0;
		
		boolean fprune = false;
		int fmargin = 0;
		// Futility pruning, if we're at a frontier node
		// check if the current evaluation + 200 (500 for pre frontier nodes)
		// reaches up to alpha, if it doesn't the node is poor and we
		// do not search it if it's not a checking move (determined below)
		if(depth <= 5*PLY && !isInCheck) {
			materialEval = Evaluation.evaluate(board, true);
			if((materialEval + FUTILITY_VALS[depth/PLY])  <= alpha) 	{
				fmargin = FUTILITY_VALS[depth/PLY];
				fprune = true;
			}
		}

		// Outer loop, this loops 6 times, one time for every part of move generation
		while(generationState < GEN_END) {		
			// Generate part of the moves depending on how many times we have looped
			switch(generationState) {
			case GEN_HASH:
			
				// If there is a hash move (which we got before or from iid), record it and give it a very high ordering value
				if(hashMove != 0) {
					searchMoves[ply][currentMovesCount].move = hashMove;
					searchMoves[ply][currentMovesCount].score = 10000;
					currentMovesCount++;
				}
				break;
			case GEN_CAPS:
				// Generate all pseudo legal captures on the board
				currentMovesCount = board.gen_caps(searchMoves[ply], 0);
				capturesCount = currentMovesCount;

				// Go through the moves and assign the ordering values according to see
				for(int i = 0; i < currentMovesCount; i++) {
					tempMove = searchMoves[ply][i].move;

					// If the capture is the same as the hashMove, we have already searched it
					// so give it a very low value, it will be skipped when run into below.
					if(tempMove == hashMove) {
						searchMoves[ply][i].score = -10000;
					}
					else searchMoves[ply][i].score = See.see(board, tempMove); // The move is not a duplicate so give it a see value
				}

				// We now have ordering values for all the captures so order them
				sortMoves(searchMoves[ply], 0, currentMovesCount);
				
				int index = 0;
				while(index < capturesCount && startLosingCaptures == -1)  {
					if(searchMoves[ply][index].score < 0) {
						startLosingCaptures = index;
						break;
					}
					index++;
				}
				
				currentMovesCount = index;
				
				break;
			case GEN_KILLERS:
				currentMovesCount = capturesCount;
				if(killers.getPrimary(ply) != hashMove && board.validateKiller(killers.getPrimary(ply))) {
					killerOne = killers.getPrimary(ply);
					searchMoves[ply][currentMovesCount].move = killers.getPrimary(ply);
					searchMoves[ply][currentMovesCount].score = 5000;
					currentMovesCount++;
				}
				if(killers.getSecondary(ply) != hashMove && board.validateKiller(killers.getSecondary(ply))) {
					killerTwo = killers.getSecondary(ply);
					searchMoves[ply][currentMovesCount].move = killers.getSecondary(ply);
					searchMoves[ply][currentMovesCount].score = 4000;
					currentMovesCount++;
				}
				startIndex = capturesCount;
				break;
			case GEN_NONCAPS:
				// Generate all pseudo legal captures on the board
				currentMovesCount = capturesCount;
				currentMovesCount += board.gen_noncaps(searchMoves[ply], currentMovesCount);

				// Go through the moves and assign the ordering values
				for(int i = capturesCount; i < currentMovesCount; i++) {
					tempMove = searchMoves[ply][i].move;

					if(tempMove == hashMove || tempMove == killerOne || tempMove == killerTwo|| tempMove == killerOneOld|| tempMove == killerTwoOld) {
						searchMoves[ply][i].score = -10000; // Move is already searched so skip it
					} else {
						if(historyValues[Move.fromIndex(searchMoves[ply][i].move)][Move.toIndex(searchMoves[ply][i].move)] != 0)
							searchMoves[ply][i].score = 1000* historyBetaHits[Move.fromIndex(searchMoves[ply][i].move)][Move.toIndex(searchMoves[ply][i].move)] / historyValues[Move.fromIndex(searchMoves[ply][i].move)][Move.toIndex(searchMoves[ply][i].move)];
					}
					
				}

				// Sort the non-captures
				sortMoves(searchMoves[ply], capturesCount, currentMovesCount);
				
				startIndex = capturesCount;
				
				break;
			case GEN_LOSINGCAPS:
				if(startLosingCaptures != -1) {
					currentMovesCount = capturesCount;
					startIndex = startLosingCaptures;
				} else {
					startIndex = 0;
					currentMovesCount = 0;
				}
				break;
			}

			// Go through the generated moves one by one
			for(int i = startIndex; i < currentMovesCount; i++) {
				

				if(searchMoves[ply][i].score == -10000) {
					continue; // This means the move has already been searched so skip it
				}
				
				if(Move.pieceMoving(searchMoves[ply][i].move) == W_PAWN  && Board.rank(Move.toIndex(searchMoves[ply][i].move)) == 6) threat = true;
				else if(Move.pieceMoving(searchMoves[ply][i].move) == B_PAWN  && Board.rank(Move.toIndex(searchMoves[ply][i].move)) == 1) threat = true;
				
				// Futility pruning, if we decided that we could not reach alpha
				// above, see if the move is a checking move, if it isn't just
				// set the score to whatever the eval was and continue with the next move
				// Do this before makeMove - if we are going to prune there is no use making the move
				// Do not prune the 1st move - we need to know
				// if we have any legal moves or are stalemated.
				if(searchedMoves >=1 && fprune && !threat && !board.isInCheck())  {
					// If the move was a capture we add the value of the captured piece
					// if the move was not a capture this will add 0 (leaving materialEval unchanged)
					int gain = Math.abs(Evaluation.PIECE_VALUE_ARRAY[Move.capture(searchMoves[ply][i].move)+7]);
					int moveType = Move.moveType(searchMoves[ply][i].move);
					if (moveType >= PROMOTION_QUEEN) {
						gain += Evaluation.PIECE_VALUE_ARRAY[moveType + 5];
					}
					if((materialEval+gain+fmargin) <= alpha) {
						if(materialEval+gain > bestEval) bestEval = materialEval+gain;
						continue;
					}
				}
				
				board.makeMove(searchMoves[ply][i].move); // Make the move on the board
				nodesSearched++;

				// Make sure we don't leave the king in check when making this move
				// If black to move and black is attacking the white king, the move made above was illegal so skip it
				if(board.toMove == BLACK_TO_MOVE && board.isAttacked(board.w_king.pieces[0], BLACK)) {
					board.unmakeMove(searchMoves[ply][i].move);
					continue;
				} else if(board.toMove == WHITE_TO_MOVE && board.isAttacked(board.b_king.pieces[0], WHITE)) {
					board.unmakeMove(searchMoves[ply][i].move);
					continue;
				}				
				
				historyValues[Move.fromIndex(searchMoves[ply][i].move)][Move.toIndex(searchMoves[ply][i].move)] += depth;
				
				if(searchedMoves >= 1) {
					// Late move reduction
					if(searchedMoves > 3 &&
							generationState == GEN_NONCAPS &&
							depth > 3*PLY &&
							!threat &&
							!board.isInCheck()) {
						eval = -alphaBeta(board, depth-(2*PLY), -alpha-1,-alpha, true, ply+1);
					} else {
						// PVS search
						eval = -alphaBeta(board, depth-PLY, -alpha -1, -alpha, true, ply+1);
					}

					if(eval > alpha && eval < beta) {
						// Full depth search
						eval = -alphaBeta(board, depth-PLY, -beta, -alpha, true, ply+1);
					}
				} else {
					eval = -alphaBeta(board, depth-PLY, -beta, -alpha, true, ply+1);
				}

				searchedMoves++;
				
				board.unmakeMove(searchMoves[ply][i].move); // Reset the board

				if(eval > bestEval)	{
					
					if(eval >= beta) {
						historyBetaHits[Move.fromIndex(searchMoves[ply][i].move)][Move.toIndex(searchMoves[ply][i].move)] += depth;
						// If the evaluation is bigger than beta, we cutoff here (since there is another move the opponent will choose so this will never happen)
						if(!stopSearch) Settings.getInstance().getTranspositionTable().record(board.zobristKey, depth/PLY, HASH_BETA, eval, searchMoves[ply][i].move);
						searchMoves[ply][0].move = searchMoves[ply][i].move;
						// Remove this from the rep table since it didn't happen
						Settings.getInstance().getRepTable().removeRep(board.zobristKey);
						
						// Add this move as a killer since it caused a cutoff
						// (do not add captures as killers since they're searched early anyway)
						if(Move.capture(searchMoves[ply][i].move) == 0) {
							killers.addKiller(searchMoves[ply][i], ply);
						}
						
						return eval;
					}

					bestEval = eval;
					

					// If the evaluation is bigger than alpha (but less than beta) this is our new best move
					if(eval > alpha) {
						eval_type = HASH_EXACT;
						bestMove = searchMoves[ply][i].move;
						alpha = eval;
					}
				}
			}// End for loop

			generationState++; // We have gone through all the generated moves from the last state so move to the next
		}// End while loop

		// If there wasn't a legal move, it's either stalemate or checkmate
		if(searchedMoves == 0) {
			if(board.isInCheck()) {
				// Don't count this position toward repetitions, since the game is over anyway
				Settings.getInstance().getRepTable().removeRep(board.zobristKey);
				searchMoves[ply][0].move = 0;
				return (MATE_VALUE+ply);
			}
			return DRAW_VALUE;
		}

		if(!stopSearch)	Settings.getInstance().getTranspositionTable().record(board.zobristKey, depth/PLY, eval_type, bestEval, bestMove);
		searchMoves[ply][0].move = bestMove;
		
		Settings.getInstance().getRepTable().removeRep(board.zobristKey);
		
		return alpha;
	} //END alphaBeta

	/**
	 * 
	 * @param board
	 * @param alpha
	 * @param beta
	 * @param ply
	 * @return
	 */
	private static int quiescentSearch(Board board, int alpha, int beta, int ply) {
		boolean inCheck = board.isInCheck();
		int eval;

		// Start with getting a score, if this is too good (above beta), just return, else go on with search
		if(!inCheck) {
			int standPatEval = Evaluation.evaluate(board, true);
		
			if(standPatEval > alpha) {
				if(standPatEval >= beta) return beta;
				alpha = standPatEval;
			}
		} 
		
		int currentMoveCount;
		if(inCheck) {
			currentMoveCount = board.gen_checkEvasions(searchMoves[ply], 0);
			sortMoves(searchMoves[ply], 0, currentMoveCount);
			
		} else {
			// Generate the caps and queen promotions, and score them
			currentMoveCount = board.gen_caps_and_promotions(searchMoves[ply], 0);
			for(int i = 0; i < currentMoveCount; i++) {
				// Order by MVV/LVA, but skip below if SEE says it's a losing capture
				// This is done to avoid doing SEE on all moves since it's quite costly
				if(Move.moveType(searchMoves[ply][i].move) == PROMOTION_QUEEN) {
					searchMoves[ply][i].score = 250000;
				} else {
					searchMoves[ply][i].score = (256*Evaluation.PIECE_VALUE_ARRAY_ABS[Move.capture(searchMoves[ply][i].move)+7]-Evaluation.PIECE_VALUE_ARRAY_ABS[Move.pieceMoving(searchMoves[ply][i].move)+7]);
				}
			}
			
			sortMoves(searchMoves[ply], 0, currentMoveCount);
		}
		
		
		int searchedMoves = 0;
		
		for(int i = 0; i < currentMoveCount; i++) {
			// This doesn't apply to check evasions moves
			// If value of the captured piece is less than the value of the capturer,
			// check the SEE score (i.e. capturing a pawn with a queen can be beneficial, but can be silly if the pawn is protected)
			// if the SEE score is losing (<0), skip the move
			if(!inCheck && Move.moveType(searchMoves[ply][i].move) != PROMOTION_QUEEN && Evaluation.PIECE_VALUE_ARRAY_ABS[Move.capture(searchMoves[ply][i].move)+7] < Evaluation.PIECE_VALUE_ARRAY_ABS[Move.pieceMoving(searchMoves[ply][i].move)+7] && See.see(board, searchMoves[ply][i].move)<0) {
				continue;
			}

			board.makeMove(searchMoves[ply][i].move);
			nodesSearched++;
			
			if(board.toMove == BLACK_TO_MOVE && board.isAttacked(board.w_king.pieces[0], BLACK)) {
				board.unmakeMove(searchMoves[ply][i].move);
				continue;
			} else if(board.toMove == WHITE_TO_MOVE && board.isAttacked(board.b_king.pieces[0], WHITE)) {
				board.unmakeMove(searchMoves[ply][i].move);
				continue;
			}
						
			searchedMoves++;
			eval = -quiescentSearch(board, -beta, -alpha, ply+1);
			board.unmakeMove(searchMoves[ply][i].move);
			
			if(eval > alpha) {
				if(eval >= beta) return beta;
				
				alpha = eval;
			}
		}
		
		if(inCheck && searchedMoves == 0) {	
			alpha = (MATE_VALUE+ply);
		}
		
		return alpha;
	}
	
	
	 /**
	  * Takes the time left and calculates how much is to be
	  * used on this move, increment adds to the thinking time 
	  * @param board
	  * @param timeLeft
	  * @param increment
	  * @return
	  */
	private static int calculateTime(Board board, int timeLeft, int increment) {
		int timeForThisMove; // The maximum time we are allowed to use on this move
		int percent = 40; // How many percent of the time we will use; percent=20 -> 5%, percent=40 -> 2.5% etc. (formula is 100/percent, i.e. 100/40 =2.5)

		timeForThisMove = timeLeft/percent+(increment); // Use the percent + increment for the move
		if(timeForThisMove >= timeLeft)	timeForThisMove = timeLeft -500; // If the increment puts us above the total time left use the timeleft - 0.5 seconds
		if(timeForThisMove < 0)	timeForThisMove = 100; // If 0.5 seconds puts us below 0 use 0.1 seconds

		return timeForThisMove;
	} // END calculateTime
	
	/**
	 *  class KillerMoves
	 *
	 *  Holds killer moves for the alphaBeta search
	 */
	public static class KillerMoves {
		private int[] primaryKillerss; // The index corresponds to the ply the killer move is located in
		private int[] secondaryKillerss;

		public KillerMoves() {
			primaryKillerss = new int[128]; // Assuming we never search over 128 plies deep
			secondaryKillerss = new int[128];
		}
		
		public int getPrimary(int ply) {
			return primaryKillerss[ply];
		}
		
		public int getSecondary(int ply) {
			return secondaryKillerss[ply];
		}

		/**
		 *  Inserts a new killer move into either primary or secondary array
		 *
		 *  @param move The killer move
		 *  @param depth The ply the killer move exists in
		 */
		public void addKiller(Move move, int depth) {
			if(primaryKillerss[depth] != move.move) {
				secondaryKillerss[depth] = primaryKillerss[depth];
				primaryKillerss[depth] = move.move;				
			}
		} // END addKiller
	} // END KillerMoves
	
	/**
	 *  class LineEval
	 *
	 *  Holds a line and evaluation number used to return in search
	 */
	public static class LineEval {
		public int[] line;
		public int eval;

		public LineEval() {
			this.line = new int[128];
			this.eval = 0;
		}

		public LineEval(int[] line, int eval) {
			this.line = line;
			this.eval = eval;
		}
	} // END  LineEval
	
	/**
	 *  Checks whether the search should stop or not,
	 *  
	 *  @return boolean true for stop, false for not
	 */
	public static boolean shouldWeStop() throws IOException {
		if(!ponder && ((System.currentTimeMillis() - startTime) > timeForThisMove)) return true;
		
		if(Uci.reader.ready()) {
			String line = Uci.reader.readLine();
			if("stop".equals(line))	return true;
			if("ponderhit".equals(line)) {
				ponder = false;
			}
		}

		return false;

	} // END shouldWeStop
	
	/**
	 *  Returns a thinking line
	 *
	 *  @param time The time the search began
	 *  @param finalEval The evaluation and pv
	 *  @return String The thinking string
	 */
	private static String receiveThinking(long time, LineEval finalEval) {
		// Built the pv line
		String pvString = "";
		for(int i = 0; i < 128; i++) {
			if(i == 0) {
				pvString += (Move.inputNotation(finalEval.line[0]) + " ");
			} else if(finalEval.line[i] == 0) break;
			else pvString += (Move.inputNotation(finalEval.line[i]) + " ");					
		}

		// Calculate the nodes per second, we need decimal values
		// to get the most accurate result.
		// If we have searched less than 1 second return the nodesSearched
		// since the numbers tend to get crazy at lower times

		long splitTime = (System.currentTimeMillis() - time);
		int nps;
		if((splitTime / 1000) < 1) nps = totalNodesSearched;
		else {
			Double decimalTime = new Double(totalNodesSearched/(splitTime/1000D));
			nps = decimalTime.intValue();
		}

		// Send the info to the uci interface
		if(finalEval.eval >= MATE_BOUND) {
			int rest = ((-MATE_VALUE) - finalEval.eval)%2;
			int mateInN = (((-MATE_VALUE)-finalEval.eval)-rest)/2+rest;
			return "info score mate " + mateInN + " depth " + current_depth + " nodes " + totalNodesSearched + " nps " + nps + " time " + splitTime + " pv " + pvString;				
		} else if(finalEval.eval <= -MATE_BOUND) {
			int rest = ((-MATE_VALUE) + finalEval.eval)%2;
			int mateInN = (((-MATE_VALUE)+finalEval.eval)-rest)/2+rest;
			return "info score mate " + -mateInN + " depth " + current_depth + " nodes " + totalNodesSearched + " nps " + nps + " time " + splitTime + " pv " + pvString;				
		}
		return "info score cp " + finalEval.eval + " depth " + current_depth + " nodes " + totalNodesSearched + " nps " + nps + " time " + splitTime + " pv " + pvString;

	} // END receiveThinking
	
	/**
	 * Simple insertion sort based on scores for the moves
	 * @param a
	 * @throws Exception
	 */
	private static void sortMoves(Move[] moves, int from, int to) {
		for (int i = from+1; i < to; i++) {
			int j = i;
			Move B = moves[i];
			while ((j > from) && (moves[j-1].score < B.score)) {
				moves[j] = moves[j-1];
				j--;
			}
			moves[j] = B;
		}
	}
}
