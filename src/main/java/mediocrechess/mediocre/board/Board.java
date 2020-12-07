package mediocrechess.mediocre.board;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import mediocrechess.mediocre.def.Definitions;
import mediocrechess.mediocre.main.Mediocre;
import mediocrechess.mediocre.transtable.Zobrist;

/**
 * class Board
 * 
 * Represents a position on the board and also contains methods for making and
 * unmaking a move, as well as generating all possible (legal) moves on the
 * board.
 * 
 * First created: 2006-12-14
 * 
 * @author Jonatan Pettersson (mediocrechess@gmail.com)
 */

public class Board implements Definitions {
	private static Logger logger = (Logger)LoggerFactory.getLogger(Mediocre.class);
	// ***
	// Variables used to describe the position
	// ***

	public int[] boardArray; // Represents the 0x88 board
	public int[] boardArrayUnique; // Keeps track of what index a piece on a
									// certain square has in the corresponding
									// piece list

	public int toMove; // Side to move on the board

	public int enPassant; // Index on boardArray where en passant is
	// available, -1 if none

	public int white_castle; // White's ability to castle
	public int black_castle; // Black's ability to castle

	public int movesFifty; // Keeps track of half-moves since last capture
	// or pawn move

	public int movesFull; // Keeps track of total full moves in the game
	public int[] history; // Keeps track of previous positions for unmake move
	public int[] captureHistory;
	public long[] zobristHistory; // Same as history but keeps tracks of
									// passed zobrist keys
	public long[] pawnZobristHistory;

	public int historyIndex; // Keeps track of number of takebacks in the
								// history arrays

	public long zobristKey; // The zobrist key for the position
	public long pawnZobristKey; // The zobrist key for the pawns
	
	public int[] tempArray; // Used for temporary things, like keeping a list of attacking squares (so we don't have to init it every time)

	public PieceList w_pawns;
	public PieceList b_pawns;
	public PieceList w_knights;
	public PieceList b_knights;
	public PieceList w_bishops;
	public PieceList b_bishops;
	public PieceList w_rooks;
	public PieceList b_rooks;
	public PieceList w_queens;
	public PieceList b_queens;
	public PieceList w_king;
	public PieceList b_king;

	// END Variables

	/**
	 * Creates an empty board
	 * 
	 * @return An empty Board
	 */
	public Board() {
		this.boardArray = new int[128];
		this.boardArrayUnique = new int[128];
		this.toMove = 1; // White to move
		this.enPassant = -1; // No en passants available
		this.white_castle = CASTLE_NONE;
		this.black_castle = CASTLE_NONE;
		this.w_pawns = new PieceList();
		this.b_pawns = new PieceList();
		this.w_knights = new PieceList();
		this.b_knights = new PieceList();
		this.w_bishops = new PieceList();
		this.b_bishops = new PieceList();
		this.w_rooks = new PieceList();
		this.b_rooks = new PieceList();
		this.w_queens = new PieceList();
		this.b_queens = new PieceList();
		this.w_king = new PieceList();
		this.b_king = new PieceList();
		this.history = new int[4096];
		this.captureHistory = new int[4096];
		this.zobristHistory = new long[4096];
		this.pawnZobristHistory = new long[4096];
		this.tempArray = new int[256];
		this.zobristKey = 0;
		this.pawnZobristKey = 0;
	} // END Board()

	/**
	 * The general class for the piece lists
	 * 
	 * This class is quite fragile so it has to be used right, for example
	 * editing the pieces array in any way but using the internal methods is
	 * dangerous (we have to remember updating the boardArraUnique etc)
	 * 
	 * Also trying to remove a piece if count==0 will not be pleasant, we need
	 * to be careful to never do things like this
	 * 
	 * It is possible to write this class in a safer way, but it costs a little
	 * time and should not be necessary
	 * 
	 * We only work with boardArrayUnique here and never touch the boardArray,
	 * that is done elsewhere (in makeMove and unmakeMove)
	 * 
	 * For promotions you need to remove the pawn/promoted piece (make/unmake)
	 * from the corresponding list and add it to the other. Make sure you always
	 * add AFTER removing the first piece since boardArrayUnique is reset after
	 * removing a piece (making it impossible to find the added piece if it is
	 * not added after the remove)
	 */
	public class PieceList {
		public int[] pieces; // The indexes the white of the certain type is
								// on
		public int count; // The number of pieces (how many slots in the array
							// are filled with indexes)

		public PieceList() {
			this.pieces = new int[10];
			this.count = 0;
		}

		/**
		 * Removes a piece from the list and updates the boardArrayUnique
		 * accordingly
		 * 
		 * Used when a capture is made
		 * 
		 * @param boardIndex
		 *            The index where the captured piece resided
		 */
		public void removePiece(int boardIndex) {
			count--; // We now have one less piece in the array
			int listIndex = boardArrayUnique[boardIndex]; // Get the place in
															// the pieces list
															// where the
															// particular piece
															// resides
			boardArrayUnique[boardIndex] = -1; // Remove the piece from the
												// board array

			pieces[listIndex] = pieces[count]; // Overwrite the removed piece
												// with the last slot in the
												// array (we decremented count
												// above so 'count' is the
												// previous last slot in the
												// array)
			boardArrayUnique[pieces[count]] = listIndex; // Update the
															// boardArrayUnique
															// so we get the
															// changed index of
															// the moved slot
			pieces[count] = 0; // Erase the last slot
		} // END removePiece()

		/**
		 * Adds a piece to the list
		 * 
		 * @param boardIndex
		 *            Index where the new piece should be
		 */
		public void addPiece(int boardIndex) {
			boardArrayUnique[boardIndex] = count; // Record the list index for
													// the piece ('count' works
													// here as last filled index
													// +1)
			pieces[count] = boardIndex; // Record the board index in the list
			count++; // Now we can increment the number of pieces
		} // END addPiece()

		/**
		 * Updates the index of a piece in the list, used when a piece is moving
		 * 
		 * If the to-square is occupied already, find out by what piece and
		 * remove it from the corresponding list
		 * 
		 * @param from
		 *            The square the piece was on before the change
		 * @param to
		 *            The new index the piece should have
		 */
		public void updateIndex(int from, int to) {

			int listIndex = boardArrayUnique[from]; // Get the place in the
													// pieces list where the
													// particular piece resides
			boardArrayUnique[from] = -1; // Reset the square it was moving
											// from

			if (boardArray[to] != 0) // The to-square was not empty so remove
										// whatever piece was on it from the
										// corresponding list
			{
				switch (boardArray[to]) {
				case W_PAWN:
					w_pawns.removePiece(to);
					break;
				case B_PAWN:
					b_pawns.removePiece(to);
					break;
				case W_KNIGHT:
					w_knights.removePiece(to);
					break;
				case B_KNIGHT:
					b_knights.removePiece(to);
					break;
				case W_BISHOP:
					w_bishops.removePiece(to);
					break;
				case B_BISHOP:
					b_bishops.removePiece(to);
					break;
				case W_ROOK:
					w_rooks.removePiece(to);
					break;
				case B_ROOK:
					b_rooks.removePiece(to);
					break;
				case W_QUEEN:
					w_queens.removePiece(to);
					break;
				case B_QUEEN:
					b_queens.removePiece(to);
					break;
				case W_KING:
					logger.error(getFen());
					logger.error("White king was captured");
					System.err.println("WKING was capture");
					break;
				case B_KING:
					logger.error(getFen());
					logger.error("White king was captured");
					System.err.println("BKING was capture");
					break;
				}

			}
			boardArrayUnique[to] = listIndex; // Record the new position of
												// the piece

			pieces[listIndex] = to;
		} // END updateIndex()

	} // END PieceList

	/**
	 * Makes a Move on the board and updates the zobrist key accordingly
	 * 
	 * @param Move
	 *            move
	 */
	public final void makeMove(int move) {

		// Backup information about the position for use in unmake
		history[historyIndex] = 0;
		if (enPassant != -1) {
			history[historyIndex] = enPassant;
		}

		history[historyIndex] = history[historyIndex] | (white_castle << 7)
				| (black_castle << 9) | (movesFifty << 16);

		// If the move is an en passant this will be 0 here, but changed when we catch en passant below
		captureHistory[historyIndex] = boardArray[Move.toIndex(move)]; 

		zobristHistory[historyIndex] = zobristKey;
		pawnZobristHistory[historyIndex] = pawnZobristKey;

		// Done with backing up, continue

		// Unmake the enpassant square that is on the board now, and replace it below when it is set again
		if (enPassant != -1)
			zobristKey ^= Zobrist.EN_PASSANT[enPassant]; 

		zobristKey ^= Zobrist.SIDE; // Toggles the side to move

		// Remove the castling rights now on the board, and replace them below
		// when they are set again
		zobristKey ^= Zobrist.W_CASTLING_RIGHTS[white_castle];
		zobristKey ^= Zobrist.B_CASTLING_RIGHTS[black_castle];

		enPassant = -1; // Set the en passant square to none, will be reset
						// below if it becomes available

		toMove *= -1; // Switch side to move (multiply toMove by -1 simply
						// switches from negative to positive
		// and vice versa)

		// Update the king index
		// if(Move.pieceMoving(move) == W_KING) wking_index =
		// Move.toIndex(move);
		// if(Move.pieceMoving(move) == B_KING) bking_index =
		// Move.toIndex(move);

		// Set the piece list index of the piece that is moving
		switch (Move.pieceMoving(move)) {
		case W_PAWN:
			w_pawns.updateIndex(Move.fromIndex(move), Move.toIndex(move));
			break;
		case B_PAWN:
			b_pawns.updateIndex(Move.fromIndex(move), Move.toIndex(move));
			break;
		case W_KNIGHT:
			w_knights.updateIndex(Move.fromIndex(move), Move.toIndex(move));
			break;
		case B_KNIGHT:
			b_knights.updateIndex(Move.fromIndex(move), Move.toIndex(move));
			break;
		case W_BISHOP:
			w_bishops.updateIndex(Move.fromIndex(move), Move.toIndex(move));
			break;
		case B_BISHOP:
			b_bishops.updateIndex(Move.fromIndex(move), Move.toIndex(move));
			break;
		case W_ROOK:
			w_rooks.updateIndex(Move.fromIndex(move), Move.toIndex(move));
			break;
		case B_ROOK:
			b_rooks.updateIndex(Move.fromIndex(move), Move.toIndex(move));
			break;
		case W_QUEEN:
			w_queens.updateIndex(Move.fromIndex(move), Move.toIndex(move));
			break;
		case B_QUEEN:
			b_queens.updateIndex(Move.fromIndex(move), Move.toIndex(move));
			break;
		case W_KING:
			w_king.updateIndex(Move.fromIndex(move), Move.toIndex(move));
			break;
		case B_KING:
			b_king.updateIndex(Move.fromIndex(move), Move.toIndex(move));
			break;
		}

		if (Move.pieceMoving(move) != W_PAWN
				&& Move.pieceMoving(move) != B_PAWN && Move.capture(move) == 0)
			movesFifty++; // Increment the moves fifty if not a pawn moving or
							// a capture
		else
			movesFifty = 0; // If a pawned moved or it was a capture, reset the
							// movesFifty count

		if (Move.pieceMoving(move) < 0)
			movesFull++; // Increment the total of full moves if the moving
							// piece is black

		// Now find out what kind of move it is and act accordingly
		switch (Move.moveType(move)) {
		case ORDINARY_MOVE: {
			// Remove and replace the piece in the zobrist key
			if (toMove == -1) // We have switched side so black means a white
								// piece is moving
			{
				zobristKey ^= Zobrist.PIECES[Math.abs(Move.pieceMoving(move)) - 1][0][Move
						.fromIndex(move)];
				zobristKey ^= Zobrist.PIECES[Math.abs(Move.pieceMoving(move)) - 1][0][Move
						.toIndex(move)];

				if (Move.pieceMoving(move) == W_PAWN) {
					pawnZobristKey ^= Zobrist.PIECES[Math.abs(Move
							.pieceMoving(move)) - 1][0][Move.fromIndex(move)];
					pawnZobristKey ^= Zobrist.PIECES[Math.abs(Move
							.pieceMoving(move)) - 1][0][Move.toIndex(move)];
				}
			} else {
				zobristKey ^= Zobrist.PIECES[Math.abs(Move.pieceMoving(move)) - 1][1][Move
						.fromIndex(move)];
				zobristKey ^= Zobrist.PIECES[Math.abs(Move.pieceMoving(move)) - 1][1][Move
						.toIndex(move)];

				if (Move.pieceMoving(move) == B_PAWN) {
					pawnZobristKey ^= Zobrist.PIECES[Math.abs(Move
							.pieceMoving(move)) - 1][1][Move.fromIndex(move)];
					pawnZobristKey ^= Zobrist.PIECES[Math.abs(Move
							.pieceMoving(move)) - 1][1][Move.toIndex(move)];
				}
			}

			boardArray[Move.toIndex(move)] = boardArray[Move.fromIndex(move)]; // Set
																				// target
																				// square
			boardArray[Move.fromIndex(move)] = EMPTY_SQUARE; // Clear the
																// original
																// square

			// Check for en passant
			// If the piece moving is a white or black pawn, and it has moved
			// 2 squares (the Math.abs line checks that)
			// set the en passant square to the right index and exit the move
			if ((Move.pieceMoving(move) == W_PAWN || Move.pieceMoving(move) == B_PAWN)
					&& Math.abs(Move.toIndex(move) - Move.fromIndex(move)) == 32) {
				enPassant = Move.fromIndex(move)
						+ (Move.toIndex(move) - Move.fromIndex(move)) / 2;
				break;
			}
			break;
		}

		case SHORT_CASTLE: {
			if (Move.pieceMoving(move) == W_KING) // White king castles short
			{
				zobristKey ^= Zobrist.PIECES[Math.abs(Move.pieceMoving(move)) - 1][0][Move
						.fromIndex(move)];
				zobristKey ^= Zobrist.PIECES[Math.abs(Move.pieceMoving(move)) - 1][0][Move
						.toIndex(move)];

				// And the rook
				zobristKey ^= Zobrist.PIECES[W_ROOK - 1][0][7];
				zobristKey ^= Zobrist.PIECES[W_ROOK - 1][0][5];

				// Change the place of the rook in the piece list as well
				w_rooks.updateIndex(H1, F1);

				boardArray[6] = boardArray[4]; // Put white king
				boardArray[5] = boardArray[7]; // Put white rook
				boardArray[7] = EMPTY_SQUARE; // Empty the rook square
				boardArray[4] = EMPTY_SQUARE; // Empty the king square

				white_castle = CASTLE_NONE; // Make further castling impossible
											// for white
			} else // Black king castles short
			{
				zobristKey ^= Zobrist.PIECES[Math.abs(Move.pieceMoving(move)) - 1][1][Move
						.fromIndex(move)];
				zobristKey ^= Zobrist.PIECES[Math.abs(Move.pieceMoving(move)) - 1][1][Move
						.toIndex(move)];

				// And the rook, stils W_ROOK since '1' takes care of color
				zobristKey ^= Zobrist.PIECES[W_ROOK - 1][1][119];
				zobristKey ^= Zobrist.PIECES[W_ROOK - 1][1][117];

				// Change the place of the rook in the piece list as well
				b_rooks.updateIndex(H8, F8);

				boardArray[118] = boardArray[116]; // Put black king
				boardArray[117] = boardArray[119]; // Put black rook
				boardArray[119] = EMPTY_SQUARE; // Empty the rook square
				boardArray[116] = EMPTY_SQUARE; // Empty the king square

				black_castle = CASTLE_NONE; // Make further castling impossible
											// for black
			}
			break;
		}

		case LONG_CASTLE: {
			if (Move.pieceMoving(move) == W_KING) // White king castles long
			{
				zobristKey ^= Zobrist.PIECES[Math.abs(Move.pieceMoving(move)) - 1][0][Move
						.fromIndex(move)];
				zobristKey ^= Zobrist.PIECES[Math.abs(Move.pieceMoving(move)) - 1][0][Move
						.toIndex(move)];

				// And the rook
				zobristKey ^= Zobrist.PIECES[W_ROOK - 1][0][0];
				zobristKey ^= Zobrist.PIECES[W_ROOK - 1][0][3];

				// Change the place of the rook in the piece list as well
				w_rooks.updateIndex(A1, D1);

				boardArray[2] = boardArray[4]; // Put white king
				boardArray[3] = boardArray[0]; // Put white rook
				boardArray[0] = EMPTY_SQUARE; // Empty the rook square
				boardArray[4] = EMPTY_SQUARE; // Empty the king square

				white_castle = CASTLE_NONE; // Make further castling impossible
											// for white
			} else // Black king castles long
			{
				zobristKey ^= Zobrist.PIECES[Math.abs(Move.pieceMoving(move)) - 1][1][Move
						.fromIndex(move)];
				zobristKey ^= Zobrist.PIECES[Math.abs(Move.pieceMoving(move)) - 1][1][Move
						.toIndex(move)];

				// And the rook, still W_ROOK since '1' takes care of color
				zobristKey ^= Zobrist.PIECES[W_ROOK - 1][1][112];
				zobristKey ^= Zobrist.PIECES[W_ROOK - 1][1][115];

				// Change the place of the rook in the piece list as well
				b_rooks.updateIndex(A8, D8);

				boardArray[114] = boardArray[116]; // Put black king
				boardArray[115] = boardArray[112]; // Put black rook
				boardArray[112] = EMPTY_SQUARE; // Empty the rook square
				boardArray[116] = EMPTY_SQUARE; // Empty the king square

				black_castle = CASTLE_NONE; // Make further castling impossible
											// for black
			}
			break;
		}

		case EN_PASSANT: {
			// Remove and replace the piece in the zobrist key
			if (toMove == -1) // We have switched side so this means white is
								// moving
			{
				zobristKey ^= Zobrist.PIECES[Math.abs(Move.pieceMoving(move)) - 1][0][Move
						.fromIndex(move)];
				zobristKey ^= Zobrist.PIECES[Math.abs(Move.pieceMoving(move)) - 1][0][Move
						.toIndex(move)];

				pawnZobristKey ^= Zobrist.PIECES[Math.abs(Move
						.pieceMoving(move)) - 1][0][Move.fromIndex(move)];
				pawnZobristKey ^= Zobrist.PIECES[Math.abs(Move
						.pieceMoving(move)) - 1][0][Move.toIndex(move)];

				// Remove the pawn from the piece list as well
				b_pawns.removePiece(Move.toIndex(move) - 16); // It is a black
																// pawn that was
																// captured en
																// passant

				captureHistory[historyIndex] = boardArray[Move.toIndex(move) - 16];

				// Since it's an en passant capture we also need to remove the
				// captured pawn
				// which resides one square up/down from the target square
				// If it's a white pawn capturing, clear the square below it
				boardArray[Move.toIndex(move) - 16] = EMPTY_SQUARE;

				// Black pawn is to be removed
				zobristKey ^= Zobrist.PIECES[W_PAWN - 1][1][Move.toIndex(move) - 16];
				pawnZobristKey ^= Zobrist.PIECES[W_PAWN - 1][1][Move
						.toIndex(move) - 16];

			} else {
				zobristKey ^= Zobrist.PIECES[Math.abs(Move.pieceMoving(move)) - 1][1][Move
						.fromIndex(move)];
				zobristKey ^= Zobrist.PIECES[Math.abs(Move.pieceMoving(move)) - 1][1][Move
						.toIndex(move)];

				pawnZobristKey ^= Zobrist.PIECES[Math.abs(Move
						.pieceMoving(move)) - 1][1][Move.fromIndex(move)];
				pawnZobristKey ^= Zobrist.PIECES[Math.abs(Move
						.pieceMoving(move)) - 1][1][Move.toIndex(move)];

				// Remove the pawn from the piece list as well
				w_pawns.removePiece(Move.toIndex(move) + 16); // It is a white
																// pawn that was
																// captured en
																// passant

				captureHistory[historyIndex] = boardArray[Move.toIndex(move) + 16];

				boardArray[Move.toIndex(move) + 16] = EMPTY_SQUARE;

				// White pawn is to be removed
				zobristKey ^= Zobrist.PIECES[W_PAWN - 1][0][Move.toIndex(move) + 16];
				pawnZobristKey ^= Zobrist.PIECES[W_PAWN - 1][0][Move
						.toIndex(move) + 16];
			}
			boardArray[Move.toIndex(move)] = boardArray[Move.fromIndex(move)]; // Set
																				// target
																				// square
			boardArray[Move.fromIndex(move)] = EMPTY_SQUARE; // Clear the
																// original
																// square
			break;
		}

			// Put a promoted piece of the right color on the target square
			// We use the toMove property to get the right color but since
			// we already changed side to move we need the opposite color
			// 
			// E.g. promotion to queen and white to move would result in
			// 2*(-(-1)) = 2 and for black 2*(-(1)) = -2
		case PROMOTION_QUEEN: {
			boardArray[Move.toIndex(move)] = W_QUEEN * (-toMove);

			if (toMove == -1) {
				zobristKey ^= Zobrist.PIECES[W_QUEEN - 1][0][Move.toIndex(move)];
				zobristKey ^= Zobrist.PIECES[W_PAWN - 1][0][Move
						.fromIndex(move)];

				pawnZobristKey ^= Zobrist.PIECES[W_PAWN - 1][0][Move
						.fromIndex(move)];

				w_pawns.removePiece(Move.toIndex(move));
				w_queens.addPiece(Move.toIndex(move));

			} else {
				zobristKey ^= Zobrist.PIECES[W_QUEEN - 1][1][Move.toIndex(move)];
				zobristKey ^= Zobrist.PIECES[W_PAWN - 1][1][Move
						.fromIndex(move)];

				pawnZobristKey ^= Zobrist.PIECES[W_PAWN - 1][1][Move
						.fromIndex(move)];

				b_pawns.removePiece(Move.toIndex(move));
				b_queens.addPiece(Move.toIndex(move));
			}
			boardArray[Move.fromIndex(move)] = EMPTY_SQUARE; // Empty the
																// square it
																// moved from
			break;
		}
		case PROMOTION_ROOK: {
			boardArray[Move.toIndex(move)] = W_ROOK * (-toMove);

			if (toMove == -1) {
				zobristKey ^= Zobrist.PIECES[W_ROOK - 1][0][Move.toIndex(move)];
				zobristKey ^= Zobrist.PIECES[W_PAWN - 1][0][Move
						.fromIndex(move)];

				pawnZobristKey ^= Zobrist.PIECES[W_PAWN - 1][0][Move
						.fromIndex(move)];

				w_pawns.removePiece(Move.toIndex(move));
				w_rooks.addPiece(Move.toIndex(move));
			} else {
				zobristKey ^= Zobrist.PIECES[W_ROOK - 1][1][Move.toIndex(move)];
				zobristKey ^= Zobrist.PIECES[W_PAWN - 1][1][Move
						.fromIndex(move)];

				pawnZobristKey ^= Zobrist.PIECES[W_PAWN - 1][1][Move
						.fromIndex(move)];

				b_pawns.removePiece(Move.toIndex(move));
				b_rooks.addPiece(Move.toIndex(move));
			}
			boardArray[Move.fromIndex(move)] = EMPTY_SQUARE; // Empty the
																// square it
																// moved from
			break;
		}
		case PROMOTION_BISHOP: {
			boardArray[Move.toIndex(move)] = W_BISHOP * (-toMove);

			if (toMove == -1) {
				zobristKey ^= Zobrist.PIECES[W_BISHOP - 1][0][Move
						.toIndex(move)];
				zobristKey ^= Zobrist.PIECES[W_PAWN - 1][0][Move
						.fromIndex(move)];

				pawnZobristKey ^= Zobrist.PIECES[W_PAWN - 1][0][Move
						.fromIndex(move)];

				w_pawns.removePiece(Move.toIndex(move));
				w_bishops.addPiece(Move.toIndex(move));
			} else {
				zobristKey ^= Zobrist.PIECES[W_BISHOP - 1][1][Move
						.toIndex(move)];
				zobristKey ^= Zobrist.PIECES[W_PAWN - 1][1][Move
						.fromIndex(move)];

				pawnZobristKey ^= Zobrist.PIECES[W_PAWN - 1][1][Move
						.fromIndex(move)];

				b_pawns.removePiece(Move.toIndex(move));
				b_bishops.addPiece(Move.toIndex(move));
			}
			boardArray[Move.fromIndex(move)] = EMPTY_SQUARE; // Empty the
																// square it
																// moved from
			break;
		}
		case PROMOTION_KNIGHT: {
			boardArray[Move.toIndex(move)] = W_KNIGHT * (-toMove);

			if (toMove == -1) {
				zobristKey ^= Zobrist.PIECES[W_KNIGHT - 1][0][Move
						.toIndex(move)];
				zobristKey ^= Zobrist.PIECES[W_PAWN - 1][0][Move
						.fromIndex(move)];
				pawnZobristKey ^= Zobrist.PIECES[W_PAWN - 1][0][Move
						.fromIndex(move)];
				w_pawns.removePiece(Move.toIndex(move));
				w_knights.addPiece(Move.toIndex(move));
			} else {
				zobristKey ^= Zobrist.PIECES[W_KNIGHT - 1][1][Move
						.toIndex(move)];
				zobristKey ^= Zobrist.PIECES[W_PAWN - 1][1][Move
						.fromIndex(move)];
				pawnZobristKey ^= Zobrist.PIECES[W_PAWN - 1][1][Move
						.fromIndex(move)];
				b_pawns.removePiece(Move.toIndex(move));
				b_knights.addPiece(Move.toIndex(move));
			}
			boardArray[Move.fromIndex(move)] = EMPTY_SQUARE; // Empty the
																// square it
																// moved from
			break;
		}

		}

		// Check for castling rights changes
		if (white_castle != CASTLE_NONE) {
			if (Move.pieceMoving(move) == W_KING)
				white_castle = CASTLE_NONE;
			else if (Move.toIndex(move) == A1 || Move.fromIndex(move) == A1) {
				if (white_castle == CASTLE_BOTH || white_castle == CASTLE_SHORT)
					white_castle = CASTLE_SHORT;
				else
					white_castle = CASTLE_NONE;
			} else if (Move.toIndex(move) == H1 || Move.fromIndex(move) == H1) {
				if (white_castle == CASTLE_BOTH || white_castle == CASTLE_LONG)
					white_castle = CASTLE_LONG;
				else
					white_castle = CASTLE_NONE;
			}

		}
		if (black_castle != CASTLE_NONE) {
			if (Move.pieceMoving(move) == B_KING)
				black_castle = CASTLE_NONE;
			else if (Move.toIndex(move) == A8 || Move.fromIndex(move) == A8) {
				if (black_castle == CASTLE_BOTH || black_castle == CASTLE_SHORT)
					black_castle = CASTLE_SHORT;
				else
					black_castle = CASTLE_NONE;
			} else if (Move.toIndex(move) == H8 || Move.fromIndex(move) == H8) {
				if (black_castle == CASTLE_BOTH || black_castle == CASTLE_LONG)
					black_castle = CASTLE_LONG;
				else
					black_castle = CASTLE_NONE;
			}
		}

		// If the move is a capture, remove the captured piece from zobrist as
		// well
		if (toMove == -1) // We have switched side so black means a white
							// piece is moving
		{
			if (Move.capture(move) != 0 && Move.moveType(move) != EN_PASSANT) {
				zobristKey ^= Zobrist.PIECES[Math.abs(Move.capture(move)) - 1][1][Move
						.toIndex(move)];

				if (Move.capture(move) == B_PAWN)
					pawnZobristKey ^= Zobrist.PIECES[Math.abs(Move
							.capture(move)) - 1][1][Move.toIndex(move)];
			}

		} else {
			if (Move.capture(move) != 0 && Move.moveType(move) != EN_PASSANT) {
				zobristKey ^= Zobrist.PIECES[Math.abs(Move.capture(move)) - 1][0][Move
						.toIndex(move)];

				if (Move.capture(move) == W_PAWN)
					pawnZobristKey ^= Zobrist.PIECES[Math.abs(Move
							.capture(move)) - 1][0][Move.toIndex(move)];
			}
		}

		// Apply the now changed castling rights to zobrist key
		zobristKey ^= Zobrist.W_CASTLING_RIGHTS[white_castle];
		zobristKey ^= Zobrist.B_CASTLING_RIGHTS[black_castle];

		// Apply the now changed en passant square to zobrist key
		if (enPassant != -1)
			zobristKey ^= Zobrist.EN_PASSANT[enPassant];

		historyIndex++;

	} // END makeMove()

	/**
	 * Just like switching side, but toggles the zobrist as well
	 */
	public final void nullmoveToggle() {
		toMove *= -1;
		zobristKey ^= Zobrist.SIDE;
	} // END makeNullmove()

	/**
	 * Unmakes a Move on the board
	 * 
	 * @param Move
	 *            move
	 */
	public final void unmakeMove(int move) {
		historyIndex--; // Go back one step in the history so we find the right
						// unmake variables

		// Use the history to reset known variables
		if (((history[historyIndex]) & 127) == 0) {
			enPassant = -1;
		} else {
			enPassant = ((history[historyIndex]) & 127);
		}
		white_castle = ((history[historyIndex] >> 7) & 3);
		black_castle = ((history[historyIndex] >> 9) & 3);
		movesFifty = ((history[historyIndex] >> 16) & 127);
		zobristKey = zobristHistory[historyIndex];
		pawnZobristKey = pawnZobristHistory[historyIndex];

		// We wait with resetting the capture until we know if it is an en
		// passant or not

		toMove *= -1; // Switch side to move

		// Update the king index

		// if(Move.pieceMoving(move) == W_KING) wking_index =
		// Move.fromIndex(move);
		// if(Move.pieceMoving(move) == B_KING) bking_index =
		// Move.fromIndex(move);

		// switch(Move.pieceMoving(move))
		switch (boardArray[Move.toIndex(move)]) {
		case W_PAWN:
			w_pawns.updateIndex(Move.toIndex(move), Move.fromIndex(move));
			break;
		case B_PAWN:
			b_pawns.updateIndex(Move.toIndex(move), Move.fromIndex(move));
			break;
		case W_KNIGHT:
			w_knights.updateIndex(Move.toIndex(move), Move.fromIndex(move));
			break;
		case B_KNIGHT:
			b_knights.updateIndex(Move.toIndex(move), Move.fromIndex(move));
			break;
		case W_BISHOP:
			w_bishops.updateIndex(Move.toIndex(move), Move.fromIndex(move));
			break;
		case B_BISHOP:
			b_bishops.updateIndex(Move.toIndex(move), Move.fromIndex(move));
			break;
		case W_ROOK:
			w_rooks.updateIndex(Move.toIndex(move), Move.fromIndex(move));
			break;
		case B_ROOK:
			b_rooks.updateIndex(Move.toIndex(move), Move.fromIndex(move));
			break;
		case W_QUEEN:
			w_queens.updateIndex(Move.toIndex(move), Move.fromIndex(move));
			break;
		case B_QUEEN:
			b_queens.updateIndex(Move.toIndex(move), Move.fromIndex(move));
			break;
		case W_KING:
			w_king.updateIndex(Move.toIndex(move), Move.fromIndex(move));
			break;
		case B_KING:
			b_king.updateIndex(Move.toIndex(move), Move.fromIndex(move));
			break;
		}

		// If the move we're taking back was a black piece moving
		// decrement the movesFull.
		if (Move.pieceMoving(move) < 0)
			movesFull--;

		switch (Move.moveType(move)) {
		case SHORT_CASTLE:

			if (Move.pieceMoving(move) == W_KING) // White king castles short
			{
				w_rooks.updateIndex(F1, H1); // Put back the rook in the
												// piece list

				boardArray[4] = boardArray[6]; // Put back white king
				boardArray[7] = boardArray[5]; // Put back white rook
				boardArray[5] = EMPTY_SQUARE; // Empty castling squares
				boardArray[6] = EMPTY_SQUARE; // .

			} else if (Move.pieceMoving(move) == B_KING) // Black king
															// castles short
			{
				b_rooks.updateIndex(F8, H8); // Put back the rook in the
												// piece list

				boardArray[116] = boardArray[118]; // Put back black king
				boardArray[119] = boardArray[117]; // Put back black rook
				boardArray[117] = EMPTY_SQUARE; // Empty castling squares
				boardArray[118] = EMPTY_SQUARE; // .

			}
			break; // Done with unmake

		case LONG_CASTLE:

			if (Move.pieceMoving(move) == W_KING) // White king castles long
			{
				w_rooks.updateIndex(D1, A1); // Put back the rook in the
												// piece list

				boardArray[4] = boardArray[2]; // Put back white king
				boardArray[0] = boardArray[3]; // Put back white rook
				boardArray[2] = EMPTY_SQUARE; // Empty castling squares
				boardArray[3] = EMPTY_SQUARE; // .

			} else if (Move.pieceMoving(move) == B_KING) // Black king
															// castles lon
			{
				b_rooks.updateIndex(D8, A8); // Put back the rook in the
												// piece list

				boardArray[116] = boardArray[114]; // Put back black king
				boardArray[112] = boardArray[115]; // Put back black rook
				boardArray[114] = EMPTY_SQUARE; // Empty castling squares
				boardArray[115] = EMPTY_SQUARE; // .

			}
			break; // Done with unmake

		case EN_PASSANT:

			// Remove and replace the piece in the piece lists
			if (toMove == 1) {
				// Put back the black pawn
				boardArray[Move.toIndex(move) - 16] = B_PAWN;
				b_pawns.addPiece(Move.toIndex(move) - 16);
			} else {
				// Put back the white pawn
				boardArray[Move.toIndex(move) + 16] = W_PAWN;
				w_pawns.addPiece(Move.toIndex(move) + 16);
			}

			boardArray[Move.fromIndex(move)] = boardArray[Move.toIndex(move)]; // Put
																				// back
																				// the
																				// pawn
			boardArray[Move.toIndex(move)] = EMPTY_SQUARE; // Clear the
															// original square

			break;

		default: {
			boardArray[Move.fromIndex(move)] = Move.pieceMoving(move); // Put
																		// back
																		// the
																		// piece
																		// (white/black
																		// pawn)
			boardArray[Move.toIndex(move)] = captureHistory[historyIndex]; // Put
																			// back
																			// the
																			// captured
																			// piece,
																			// 0 if
																			// it
																			// wasn't
																			// a
																			// capture

			// If it was a capture put it back in the piece list
			if (captureHistory[historyIndex] != 0) {
				switch (boardArray[Move.toIndex(move)]) {
				case W_PAWN:
					w_pawns.addPiece(Move.toIndex(move));
					break;
				case B_PAWN:
					b_pawns.addPiece(Move.toIndex(move));
					break;
				case W_KNIGHT:
					w_knights.addPiece(Move.toIndex(move));
					break;
				case B_KNIGHT:
					b_knights.addPiece(Move.toIndex(move));
					break;
				case W_BISHOP:
					w_bishops.addPiece(Move.toIndex(move));
					break;
				case B_BISHOP:
					b_bishops.addPiece(Move.toIndex(move));
					break;
				case W_ROOK:
					w_rooks.addPiece(Move.toIndex(move));
					break;
				case B_ROOK:
					b_rooks.addPiece(Move.toIndex(move));
					break;
				case W_QUEEN:
					w_queens.addPiece(Move.toIndex(move));
					break;
				case B_QUEEN:
					b_queens.addPiece(Move.toIndex(move));
					break;
				}
			}

			// If it was a promotion, remove the promoted piece from the
			// corresponding list
			// and put back the pawn in the pawns list
			if (Move.moveType(move) >= PROMOTION_QUEEN) {
				if (toMove == 1) // The move we're unmaking was white
									// promoting
				{
					switch (Move.moveType(move)) {
					case PROMOTION_QUEEN:
						w_queens.removePiece(Move.fromIndex(move));
						w_pawns.addPiece(Move.fromIndex(move));

						break;
					case PROMOTION_ROOK:
						w_rooks.removePiece(Move.fromIndex(move));
						w_pawns.addPiece(Move.fromIndex(move));

						break;
					case PROMOTION_BISHOP:
						w_bishops.removePiece(Move.fromIndex(move));
						w_pawns.addPiece(Move.fromIndex(move));

						break;
					case PROMOTION_KNIGHT:
						w_knights.removePiece(Move.fromIndex(move));
						w_pawns.addPiece(Move.fromIndex(move));

						break;
					}

				} else // Black promoted
				{
					switch (Move.moveType(move)) {
					case PROMOTION_QUEEN:
						b_queens.removePiece(Move.fromIndex(move));
						b_pawns.addPiece(Move.fromIndex(move));
						break;
					case PROMOTION_ROOK:
						b_rooks.removePiece(Move.fromIndex(move));
						b_pawns.addPiece(Move.fromIndex(move));

						break;
					case PROMOTION_BISHOP:
						b_bishops.removePiece(Move.fromIndex(move));
						b_pawns.addPiece(Move.fromIndex(move));

						break;
					case PROMOTION_KNIGHT:
						b_knights.removePiece(Move.fromIndex(move));
						b_pawns.addPiece(Move.fromIndex(move));

						break;
					}

				}
			}

			break;
		}

		}
	} // END unmakeMove()

	
	/**
	 * Validates that the given move is legal on the current board
	 * @param move
	 * @return
	 */
	public final boolean validateHashMove(int move) {
		if (move == 0)
			return false;

		int from = Move.fromIndex(move);
		int to = Move.toIndex(move);
		int piece = Move.pieceMoving(move);
		int type = Move.moveType(move);
		int capture = Move.capture(move);

		if (boardArray[from] != piece)
			return false; // Check if the piece exists on the index

		int possiblePieces = ATTACK_ARRAY[to - from + 128];
		
		if(possiblePieces == ATTACK_NONE) return false;
				
		if (toMove == WHITE_TO_MOVE) {
			if(piece == W_QUEEN && !(possiblePieces == ATTACK_KQBbP || possiblePieces == ATTACK_KQBwP || possiblePieces == ATTACK_KQR || possiblePieces == ATTACK_QB || possiblePieces == ATTACK_QR)) return false;
			if(piece == W_ROOK && !(possiblePieces == ATTACK_KQR || possiblePieces == ATTACK_QR)) return false;
			if(piece == W_BISHOP && !(possiblePieces == ATTACK_KQBbP || possiblePieces == ATTACK_KQBwP || possiblePieces == ATTACK_QB)) return false;
			if(piece == W_KNIGHT && !(possiblePieces == ATTACK_N)) return false;
			
			
			if (piece < 0)
				return false; // Make sure it is the right side moving
			if (piece == W_PAWN) {
				if(type == EN_PASSANT && to == enPassant) return true;
				if(capture == 0) {
					if (to == from + 16 && boardArray[from + 16] == EMPTY_SQUARE)
						return true;
					else if (rank(from) == 1 && to == from + 32
							&& boardArray[from + 16] == EMPTY_SQUARE
							&& boardArray[from + 32] == EMPTY_SQUARE)
						return true;
					else
						return false;
				} else {
					if(possiblePieces != ATTACK_KQBwP) return false;
					return (((from + 15) == to || (from + 17) == to) && boardArray[to] == capture);
				}
			} else if (type == SHORT_CASTLE) {
				if (white_castle == CASTLE_SHORT || white_castle == CASTLE_BOTH) {
					if (boardArray[F1] == EMPTY_SQUARE
							&& boardArray[G1] == EMPTY_SQUARE) {
						if (!isAttacked(E1, BLACK) && !isAttacked(F1, BLACK)
								&& !isAttacked(G1, BLACK))
							return true;
					}
				}
			} else if (type == LONG_CASTLE) {
				if (white_castle == CASTLE_LONG || white_castle == CASTLE_BOTH) {
					if (boardArray[D1] == EMPTY_SQUARE
							&& boardArray[C1] == EMPTY_SQUARE
							&& boardArray[B1] == EMPTY_SQUARE) {
						if (!isAttacked(E1, BLACK) && !isAttacked(D1, BLACK)
								&& !isAttacked(C1, BLACK))
							return true;
					}
				}
			} else {
				if (traverseDelta(from, to))
					return true;
			}
		} else // Black to move
		{
			if(piece == B_QUEEN && !(possiblePieces == ATTACK_KQBbP || possiblePieces == ATTACK_KQBwP || possiblePieces == ATTACK_KQR || possiblePieces == ATTACK_QB || possiblePieces == ATTACK_QR)) return false;
			if(piece == B_ROOK && !(possiblePieces == ATTACK_KQR || possiblePieces == ATTACK_QR)) return false;
			if(piece == B_BISHOP && !(possiblePieces == ATTACK_KQBbP || possiblePieces == ATTACK_KQBwP || possiblePieces == ATTACK_QB)) return false;
			if(piece == B_KNIGHT && !(possiblePieces == ATTACK_N)) return false;
			
			if (piece > 0)
				return false; // Make sure it is the right side moving
			if (piece == B_PAWN) {
				if(type == EN_PASSANT && to == enPassant) return true;
				if(capture == 0) {
					if (to == from - 16 && boardArray[from - 16] == EMPTY_SQUARE)
						return true;
					else if (rank(from) == 6 && to == from - 32
							&& boardArray[from - 16] == EMPTY_SQUARE
							&& boardArray[from - 32] == EMPTY_SQUARE)
						return true;
					else
						return false;
				} else {
					if(possiblePieces != ATTACK_KQBbP) return false;
					return (((from - 15) == to || (from - 17) == to) && boardArray[to] == capture);
				}
			} else if (type == SHORT_CASTLE) {
				if (black_castle == CASTLE_SHORT || black_castle == CASTLE_BOTH) {
					if (boardArray[F8] == EMPTY_SQUARE
							&& boardArray[G8] == EMPTY_SQUARE) {
						if (!isAttacked(E8, WHITE) && !isAttacked(F8, WHITE)
								&& !isAttacked(G8, WHITE))
							return true;
					}
				}
			} else if (type == LONG_CASTLE) {
				if (black_castle == CASTLE_LONG || black_castle == CASTLE_BOTH) {
					if (boardArray[D8] == EMPTY_SQUARE
							&& boardArray[C8] == EMPTY_SQUARE
							&& boardArray[B8] == EMPTY_SQUARE) {
						if (!isAttacked(E8, WHITE) && !isAttacked(D8, WHITE)
								&& !isAttacked(C8, WHITE))
							return true;
					}
				}
			} else {
				if (traverseDelta(from, to))
					return true;
			}
		}
		return false;
	} // END validateHashMove()
	
	/**
	 * Takes a move fromt the killers in the search and checks if it is possible
	 * to play on the board.
	 * 
	 * Killers can't be captures so we do not have to check that
	 * 
	 * @param move
	 *            The move to verify
	 * @return true if the move can be played, false if not
	 */
	public final boolean validateKiller(int move) {
		if (move == 0)
			return false;

		int from = Move.fromIndex(move);
		int to = Move.toIndex(move);
		int piece = Move.pieceMoving(move);
		int type = Move.moveType(move);

		if (boardArray[from] != piece)
			return false; // Check if the piece exists on the index
		if (boardArray[to] != EMPTY_SQUARE)
			return false; // Make sure is not a capture

		if (toMove == WHITE_TO_MOVE) {
			if (piece < 0)
				return false; // Make sure it is the right side moving
			if (piece == W_PAWN) {
				if (to == from + 16 && boardArray[from + 16] == EMPTY_SQUARE)
					return true;
				else if (rank(from) == 1 && to == from + 32
						&& boardArray[from + 16] == EMPTY_SQUARE
						&& boardArray[from + 32] == EMPTY_SQUARE)
					return true;
				else
					return false;
			} else if (type == SHORT_CASTLE) {
				if (white_castle == CASTLE_SHORT || white_castle == CASTLE_BOTH) {
					if (boardArray[F1] == EMPTY_SQUARE
							&& boardArray[G1] == EMPTY_SQUARE) {
						if (!isAttacked(E1, BLACK) && !isAttacked(F1, BLACK)
								&& !isAttacked(G1, BLACK))
							return true;
					}
				}
			} else if (type == LONG_CASTLE) {
				if (white_castle == CASTLE_LONG || white_castle == CASTLE_BOTH) {
					if (boardArray[D1] == EMPTY_SQUARE
							&& boardArray[C1] == EMPTY_SQUARE
							&& boardArray[B1] == EMPTY_SQUARE) {
						if (!isAttacked(E1, BLACK) && !isAttacked(D1, BLACK)
								&& !isAttacked(C1, BLACK))
							return true;
					}
				}
			} else {
				if (traverseDelta(from, to))
					return true;
			}
		} else // Black to move
		{
			if (piece > 0)
				return false; // Make sure it is the right side moving
			if (piece == B_PAWN) {
				if (to == from - 16 && boardArray[from - 16] == EMPTY_SQUARE)
					return true;
				else if (rank(from) == 6 && to == from - 32
						&& boardArray[from - 16] == EMPTY_SQUARE
						&& boardArray[from - 32] == EMPTY_SQUARE)
					return true;
				else
					return false;
			} else if (type == SHORT_CASTLE) {
				if (black_castle == CASTLE_SHORT || black_castle == CASTLE_BOTH) {
					if (boardArray[F8] == EMPTY_SQUARE
							&& boardArray[G8] == EMPTY_SQUARE) {
						if (!isAttacked(E8, WHITE) && !isAttacked(F8, WHITE)
								&& !isAttacked(G8, WHITE))
							return true;
					}
				}
			} else if (type == LONG_CASTLE) {
				if (black_castle == CASTLE_LONG || black_castle == CASTLE_BOTH) {
					if (boardArray[D8] == EMPTY_SQUARE
							&& boardArray[C8] == EMPTY_SQUARE
							&& boardArray[B8] == EMPTY_SQUARE) {
						if (!isAttacked(E8, WHITE) && !isAttacked(D8, WHITE)
								&& !isAttacked(C8, WHITE))
							return true;
					}
				}
			} else {
				if (traverseDelta(from, to))
					return true;
			}
		}
		return false;
	} // END validateKiller()

	/**
	 * Sets the board to the starting position
	 * rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1
	 */
	public final void setupStart() {
		inputFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
		Zobrist.getZobristKey(this); // Get the inital zobrist key
	} // END setupStart()

	/**
	 * Returns the current position in FEN-notation
	 * 
	 * @return A string with FEN-notation
	 */
	public final String getFen() {
		String fen_string = ""; // This holds the FEN-string

		// ***
		// The following lines adds the pieces and empty squares to the FEN
		// ***

		int index = 112; // Keeps track of the index on the board
		int empties = 0; // Number of empty squares in a row

		while (index >= 0) // Run until end of the real board
		{
			if ((index & 0x88) != 0) // Reached the end of a rank
			{
				if (empties != 0) {
					fen_string += empties; // Add the empties number if it's
											// not 0
					empties = 0;
				}
				index -= 24; // Jump to the next rank
				if (index >= 0)
					fen_string += "/"; // Add to mark a new rank, if we're not
										// at the end
			} else // The index is on the real board
			{
				if (boardArray[index] != EMPTY_SQUARE) // If a piece is on the
														// square
				// i.e. the square it not empty
				{
					if (empties != 0)
						fen_string += empties; // Add the empty square number
					// if it's not 0
					empties = 0; // Reset empties (since we now have a piece
									// coming)
				}

				switch (boardArray[index]) {
				// Add the piece on the square
				case W_KING:
					fen_string += "K";
					break;
				case W_QUEEN:
					fen_string += "Q";
					break;
				case W_ROOK:
					fen_string += "R";
					break;
				case W_BISHOP:
					fen_string += "B";
					break;
				case W_KNIGHT:
					fen_string += "N";
					break;
				case W_PAWN:
					fen_string += "P";
					break;
				case B_KING:
					fen_string += "k";
					break;
				case B_QUEEN:
					fen_string += "q";
					break;
				case B_ROOK:
					fen_string += "r";
					break;
				case B_BISHOP:
					fen_string += "b";
					break;
				case B_KNIGHT:
					fen_string += "n";
					break;
				case B_PAWN:
					fen_string += "p";
					break;
				default:
					empties++; // If no piece, increment the empty square count
				}
				index++; // Go to the next square
			}

		}

		// END Adding pieces

		fen_string += " "; // Add space for next part

		// Adds side to move (important space before the letter here)
		if (toMove == WHITE_TO_MOVE)
			fen_string += "w"; // White's move
		else
			fen_string += "b"; // Black's move

		fen_string += " "; // Add space for next part

		// Castling rights
		if (white_castle == CASTLE_NONE && black_castle == CASTLE_NONE)
			fen_string += "-"; // Neither
		else // Atleast one side can castle one way
		{
			switch (white_castle) // Check white's castling rights
			{
			case CASTLE_SHORT:
				fen_string += "K";
				break;
			case CASTLE_LONG:
				fen_string += "Q";
				break;
			case CASTLE_BOTH:
				fen_string += "KQ";
				break;
			}

			switch (black_castle) // Check black's castling rights
			{
			case CASTLE_SHORT:
				fen_string += "k";
				break;
			case CASTLE_LONG:
				fen_string += "q";
				break;
			case CASTLE_BOTH:
				fen_string += "kq";
				break;
			}
		}

		fen_string += " "; // Add space for next part

		// En passant square

		if (enPassant == -1)
			fen_string += "-"; // If no en passant is available
		else // An en passant is available
		{
			switch (enPassant % 16) // Find the file
			{
			case 0:
				fen_string += "a";
				break;
			case 1:
				fen_string += "b";
				break;
			case 2:
				fen_string += "c";
				break;
			case 3:
				fen_string += "d";
				break;
			case 4:
				fen_string += "e";
				break;
			case 5:
				fen_string += "f";
				break;
			case 6:
				fen_string += "g";
				break;
			case 7:
				fen_string += "h";
				break;
			default:
				fen_string += "Error in ep square";
			}
			switch ((enPassant - (enPassant % 16)) / 16) // Find the rank
			{
			case 2:
				fen_string += "3";
				break;
			case 5:
				fen_string += "6";
				break;
			default:
				fen_string += "Error in ep square"; // Since en passants only
													// can occur
				// on 3rd and 6th rank, any other
				// rank is an error
			}
		}

		fen_string += " "; // Add space for next part
		fen_string += movesFifty; // Add half-moves since last capture/pawn
									// move
		fen_string += " ";
		fen_string += movesFull; // Add number of full moves in the game so
									// far

		return fen_string; // Returns the finished FEN-string
	} // END getFEN()

	/**
	 * Takes a FEN-string and sets the board accordingly
	 * 
	 * @param String
	 *            fen
	 */
	public final void inputFen(String fen) {
		historyIndex = 0; // Reset to make sure we start from the beginning
		String trimmedFen = fen.trim(); // Removes any white spaces in front or
										// behind the string
		boardArray = new int[128]; // Empties the board from any pieces

		for (int i = 0; i < 128; i++) {
			boardArrayUnique[i] = -1;
		}
		// Reset the piece lists
		this.w_pawns = new PieceList();
		this.b_pawns = new PieceList();
		this.w_knights = new PieceList();
		this.b_knights = new PieceList();
		this.w_bishops = new PieceList();
		this.b_bishops = new PieceList();
		this.w_rooks = new PieceList();
		this.b_rooks = new PieceList();
		this.w_queens = new PieceList();
		this.b_queens = new PieceList();
		this.w_king = new PieceList();
		this.b_king = new PieceList();

		String currentChar; // Holds the current character in the fen

		int i = 0; // Used to go through the fen-string character by character

		int boardIndex = 112; // Keeps track of current index on the board
								// (while adding pieces)
		// Starts at "a8" (index 112) since the fen string starts on this square

		int currentStep = 0; // This will be incremented when a space is
								// detected in the string
		// 0 - Pieces
		// 1 - Side to move
		// 2 - Castling rights
		// 3 - En passant square
		// 4 - Half-moves (for 50 move rule) and full moves

		white_castle = CASTLE_NONE; // Resetting, will be changed below if
									// castling rights are found
		black_castle = CASTLE_NONE;
		boolean fenFinished = false; // Set to true when we're at the end of
										// the fen-string
		while (!fenFinished && i < trimmedFen.length()) {
			currentChar = trimmedFen.substring(i, i + 1); // Gets the current
															// character from
															// the fen-string

			// If a space is detected, get the next character, and move to next
			// step
			if (" ".equals(currentChar)) {
				i++;
				currentChar = trimmedFen.substring(i, i + 1);
				currentStep++;
			}

			switch (currentStep) // Determine what step we're on
			{
			case 0: // Pieces
			{
				switch (currentChar.charAt(0)) // See what piece is on the
												// square
				{
				// If character is a '/' move to first file on next rank
				case '/':
					boardIndex -= 24;
					break;

				// If the character is a piece, add it and move to next square
				case 'K':
					boardArray[boardIndex] = W_KING;
					w_king.addPiece(boardIndex);
					boardIndex++;
					break;
				case 'Q':
					boardArray[boardIndex] = W_QUEEN;
					w_queens.addPiece(boardIndex);
					boardIndex++;
					break;
				case 'R':
					boardArray[boardIndex] = W_ROOK;
					w_rooks.addPiece(boardIndex);
					boardIndex++;
					break;
				case 'B':
					boardArray[boardIndex] = W_BISHOP;
					w_bishops.addPiece(boardIndex);
					boardIndex++;
					break;
				case 'N':
					boardArray[boardIndex] = W_KNIGHT;
					w_knights.addPiece(boardIndex);
					boardIndex++;
					break;
				case 'P':
					boardArray[boardIndex] = W_PAWN;
					w_pawns.addPiece(boardIndex);
					boardIndex++;
					break;
				case 'k':
					boardArray[boardIndex] = B_KING;
					b_king.addPiece(boardIndex);
					boardIndex++;
					break;
				case 'q':
					boardArray[boardIndex] = B_QUEEN;
					b_queens.addPiece(boardIndex);
					boardIndex++;
					break;
				case 'r':
					boardArray[boardIndex] = B_ROOK;
					b_rooks.addPiece(boardIndex);
					boardIndex++;
					break;
				case 'b':
					boardArray[boardIndex] = B_BISHOP;
					b_bishops.addPiece(boardIndex);
					boardIndex++;
					break;
				case 'n':
					boardArray[boardIndex] = B_KNIGHT;
					b_knights.addPiece(boardIndex);
					boardIndex++;
					break;
				case 'p':
					boardArray[boardIndex] = B_PAWN;
					b_pawns.addPiece(boardIndex);
					boardIndex++;
					break;

				// If no piece was found, it has to be a number of empty squares
				// so move to that board index
				default:
					boardIndex += Integer.parseInt(currentChar);
				}
				break;
			}
			case 1: // Side to move
			{
				if ("w".equals(currentChar))
					toMove = WHITE_TO_MOVE;
				else
					toMove = BLACK_TO_MOVE;
				break;
			}
			case 2: // Castling rights
			{
				// '-' states that no castling is available so we simply keep
				// the values
				// we set before the while-loop, and don't need to do anything
				// here.
				switch (currentChar.charAt(0)) {
				// White can atleast castle short
				case 'K':
					white_castle = CASTLE_SHORT;
					break;

				case 'Q': // White can atleast castle long
				{
					// If white already can castle short, do both, else only
					// long
					if (white_castle == CASTLE_SHORT)
						white_castle = CASTLE_BOTH;
					else
						white_castle = CASTLE_LONG;
					break;
				}
					// Black can atleast castle short
				case 'k':
					black_castle = CASTLE_SHORT;
					break;

				case 'q': // Black can atleast castle long
				{
					// If black already can castle short, do both, else only
					// long
					if (black_castle == CASTLE_SHORT)
						black_castle = CASTLE_BOTH;
					else
						black_castle = CASTLE_LONG;
					break;
				}
				}
				break;
			}
			case 3: // En passant
			{
				if ("-".equals(currentChar))
					enPassant = -1;
				else {
					switch (currentChar.charAt(0)) // Find the file
					{
					case 'a':
						enPassant = 0;
						break;
					case 'b':
						enPassant = 1;
						break;
					case 'c':
						enPassant = 2;
						break;
					case 'd':
						enPassant = 3;
						break;
					case 'e':
						enPassant = 4;
						break;
					case 'f':
						enPassant = 5;
						break;
					case 'g':
						enPassant = 6;
						break;
					case 'h':
						enPassant = 7;
						break;
					}
					// Get the next character (the rank)
					i++;
					currentChar = trimmedFen.substring(i, i + 1);

					// On rank 3 or else rank 6
					if ("3".equals(currentChar))
						enPassant += 32; // Add 2 ranks to index
					else
						enPassant += 80; // Add 5 ranks to index
				}
				break;
			}
			case 4: // Half-moves (50 move rule) and full moves
			{
				// If the next character is a space, we're done with half-moves
				// and
				// can insert them
				if (" ".equals(trimmedFen.substring(i + 1, i + 2))) {
					movesFifty = Integer.parseInt(currentChar);
				}
				// If the next character is not a space, we know it's a number
				// and since half-moves can't be higher than 50 (or it can, but
				// the game
				// is drawn so there's not much point to it), we can assume
				// there are two numbers and then we're done with half-moves.
				else {
					movesFifty = Integer.parseInt(trimmedFen
							.substring(i, i + 2));
					i++;
				}
				i += 2;
				movesFull = Integer.parseInt(trimmedFen.substring(i));
				fenFinished = true; // We're done with the fen-string and can
									// exit the loop
				break;
			}
			}
			i++; // Move to the next character in the fen-string
		}
		zobristKey = Zobrist.getZobristKey(this); // The board is now setup so
													// we can get the inital
													// zobrist key
		pawnZobristKey = Zobrist.getPawnZobristKey(this);
	} // END inputFEN()
	
	/** 
	 * Fill the moves array from startIndex with all legal moves
	 * 
	 * @param moves
	 *            The array to fill
	 * @param startIndex
	 *            Where to start filling
	 * @return Number of moves generated
	 */
	public final int gen_allLegalMoves(Move[] moves, int startIndex) {
		int nMoves = 0;

		nMoves = gen_noncaps(moves, startIndex);

		nMoves += gen_caps(moves, nMoves+startIndex);


		/* Filter out the moves that leaves the king in check */
		int totalLegalMoves = 0;
		for(int i = startIndex; i < nMoves+startIndex; i++) {
			int thisMove = moves[i].move;
			makeMove(thisMove);

			if(toMove == WHITE_TO_MOVE) {
				if(isAttacked(b_king.pieces[0], WHITE)) {
					moves[i].move = 0;
				} else {
					moves[i].move = 0;
					moves[startIndex+totalLegalMoves].move = thisMove;
					totalLegalMoves++;
				}
			} else {
				if(isAttacked(w_king.pieces[0], BLACK)) {
					moves[i].move = 0;
				} else {
					moves[i].move = 0;
					moves[startIndex+totalLegalMoves].move = thisMove;
					totalLegalMoves++;
				}
			}
			unmakeMove(thisMove);
			
		}
	
		return totalLegalMoves;
	} // END gen_allLegalMoves()
	
	/**
	 * Help method for gen_checkEvasions
	 * @param moves will contain the checking move (to reverse engineer later)
	 * @param attacked square that is under attack
	 * @param side who is the attacker
	 * @return Number of attackers found (0-2 obviously)
	 */
	private final int getAttackers(int[] attackingSquares, int attacked, int side) {
		int pieceAttack;
		int attackers = 0;

		if (side == WHITE) {
			// Pawns, only two possible squares
			if (((attacked - 17) & 0x88) == 0
					&& boardArray[attacked - 17] == W_PAWN)
				attackingSquares[attackers++] = attacked-17;
			if (((attacked - 15) & 0x88) == 0
					&& boardArray[attacked - 15] == W_PAWN)
				attackingSquares[attackers++] = attacked-15;

			// Knights
			for (int i = 0; i < w_knights.count; i++) {
				if (ATTACK_ARRAY[attacked - w_knights.pieces[i] + 128] == ATTACK_N)
					attackingSquares[attackers++] = w_knights.pieces[i];
			}

			// Bishops
			for (int i = 0; i < w_bishops.count; i++) {
				pieceAttack = ATTACK_ARRAY[attacked - w_bishops.pieces[i] + 128];
				if (pieceAttack == ATTACK_KQBwP || pieceAttack == ATTACK_KQBbP
						|| pieceAttack == ATTACK_QB) {
					if (traverseDelta(w_bishops.pieces[i], attacked)) 
						attackingSquares[attackers++] = w_bishops.pieces[i];
				}
			}
			// Rooks
			for (int i = 0; i < w_rooks.count; i++) {
				pieceAttack = ATTACK_ARRAY[attacked - w_rooks.pieces[i] + 128];
				if (pieceAttack == ATTACK_KQR || pieceAttack == ATTACK_QR) {
					if (traverseDelta(w_rooks.pieces[i], attacked))
						attackingSquares[attackers++] = w_rooks.pieces[i];
				}
			}
			// Queen
			for (int i = 0; i < w_queens.count; i++) {
				pieceAttack = ATTACK_ARRAY[attacked - w_queens.pieces[i] + 128];
				if (pieceAttack != ATTACK_NONE && pieceAttack != ATTACK_N) {
					if (traverseDelta(w_queens.pieces[i], attacked))
						attackingSquares[attackers++] = w_queens.pieces[i];
				}
			}
			// King
			// Can't be king so skip
		} else {
			// Pawns, only two possible squares
			// No need for out of bounds checks here since we add to the index
			// (can never get below zero)
			if (((attacked + 17) & 0x88) == 0
					&& boardArray[attacked + 17] == B_PAWN)
				attackingSquares[attackers++] = attacked+17;
			if (((attacked + 15) & 0x88) == 0
					&& boardArray[attacked + 15] == B_PAWN)
				attackingSquares[attackers++] = attacked+15;

			// Knights
			for (int i = 0; i < b_knights.count; i++) {
				if (ATTACK_ARRAY[attacked - b_knights.pieces[i] + 128] == ATTACK_N)
					attackingSquares[attackers++] = b_knights.pieces[i];
			}

			// Bishops
			for (int i = 0; i < b_bishops.count; i++) {
				pieceAttack = ATTACK_ARRAY[attacked - b_bishops.pieces[i] + 128];
				if (pieceAttack == ATTACK_KQBwP || pieceAttack == ATTACK_KQBbP
						|| pieceAttack == ATTACK_QB) {
					if (traverseDelta(b_bishops.pieces[i], attacked))
						attackingSquares[attackers++] = b_bishops.pieces[i];
				}
			}
			// Rooks
			for (int i = 0; i < b_rooks.count; i++) {
				pieceAttack = ATTACK_ARRAY[attacked - b_rooks.pieces[i] + 128];
				if (pieceAttack == ATTACK_KQR || pieceAttack == ATTACK_QR) {
					if (traverseDelta(b_rooks.pieces[i], attacked))
						attackingSquares[attackers++] = b_rooks.pieces[i];
				}
			}
			// Queen
			for (int i = 0; i < b_queens.count; i++) {
				pieceAttack = ATTACK_ARRAY[attacked - b_queens.pieces[i] + 128];
				if (pieceAttack != ATTACK_NONE && pieceAttack != ATTACK_N) {
					if (traverseDelta(b_queens.pieces[i], attacked))
						attackingSquares[attackers++] = b_queens.pieces[i];
				}
			}
			// King
			// Can't be king so skip
		}
		return attackers;
	}
	
	/**
	 * Help method for gen_checkEvasions
	 * @param moves will contain the checking move (to reverse engineer later)
	 * @param attacked square that is under attack
	 * @param side who is the attacker
	 * @return Number of attackers found (0-2 obviously)
	 */
	private final int getAttackingMoves(Move[] moves, int attacked, int side, int startIndex) {
		int pieceAttack;
		int attackers = startIndex;

		if (side == WHITE) {
			// Pawns
			if (((attacked - 17) & 0x88) == 0
					&& boardArray[attacked - 17] == W_PAWN) {
				if(rank(attacked) != 7)
					moves[attackers++].move = Move.createMove(W_PAWN, attacked-17, attacked, boardArray[attacked], ORDINARY_MOVE, 0);
				else {
					moves[attackers++].move = Move.createMove(W_PAWN, attacked-17, attacked, boardArray[attacked], PROMOTION_QUEEN, 0);
					moves[attackers++].move = Move.createMove(W_PAWN, attacked-17, attacked, boardArray[attacked], PROMOTION_ROOK, 0);
					moves[attackers++].move = Move.createMove(W_PAWN, attacked-17, attacked, boardArray[attacked], PROMOTION_BISHOP, 0);
					moves[attackers++].move = Move.createMove(W_PAWN, attacked-17, attacked, boardArray[attacked], PROMOTION_KNIGHT, 0);
				}
			}
			if (((attacked - 15) & 0x88) == 0
					&& boardArray[attacked - 15] == W_PAWN) {
				if(rank(attacked) != 7)
					moves[attackers++].move = Move.createMove(W_PAWN, attacked-15, attacked,  boardArray[attacked], ORDINARY_MOVE, 0);
				else {
					moves[attackers++].move = Move.createMove(W_PAWN, attacked-15, attacked, boardArray[attacked], PROMOTION_QUEEN, 0);
					moves[attackers++].move = Move.createMove(W_PAWN, attacked-15, attacked, boardArray[attacked], PROMOTION_ROOK, 0);
					moves[attackers++].move = Move.createMove(W_PAWN, attacked-15, attacked, boardArray[attacked], PROMOTION_BISHOP, 0);
					moves[attackers++].move = Move.createMove(W_PAWN, attacked-15, attacked, boardArray[attacked], PROMOTION_KNIGHT, 0);
				}
			}
			
			if (enPassant != -1 && rank(enPassant) == 5 && (attacked + 16) == enPassant) {
				// Check the both squares where an en passant capture ispossible from
				int from = enPassant - 17;
				if ((from & 0x88) == 0) {
					if (boardArray[from] == W_PAWN) {
						moves[attackers++].move = Move.createMove(W_PAWN, from, enPassant, B_PAWN, EN_PASSANT, 0);
						
					}
				}

				from = enPassant - 15;
				if ((from & 0x88) == 0) {
					if (boardArray[from] == W_PAWN) {
						moves[attackers++].move = Move.createMove(W_PAWN, from, enPassant, B_PAWN, EN_PASSANT, 0);
						
					}
				}
			}

			// Knights
			for (int i = 0; i < w_knights.count; i++) {
				if (ATTACK_ARRAY[attacked - w_knights.pieces[i] + 128] == ATTACK_N)
					moves[attackers++].move = Move.createMove(W_KNIGHT, w_knights.pieces[i], attacked,  boardArray[attacked], ORDINARY_MOVE, 0);
			}

			// Bishops
			for (int i = 0; i < w_bishops.count; i++) {
				pieceAttack = ATTACK_ARRAY[attacked - w_bishops.pieces[i] + 128];
				if (pieceAttack == ATTACK_KQBwP || pieceAttack == ATTACK_KQBbP
						|| pieceAttack == ATTACK_QB) {
					if (traverseDelta(w_bishops.pieces[i], attacked)) 
						moves[attackers++].move = Move.createMove(W_BISHOP, w_bishops.pieces[i], attacked,  boardArray[attacked], ORDINARY_MOVE, 0);
				}
			}
			// Rooks
			for (int i = 0; i < w_rooks.count; i++) {
				pieceAttack = ATTACK_ARRAY[attacked - w_rooks.pieces[i] + 128];
				if (pieceAttack == ATTACK_KQR || pieceAttack == ATTACK_QR) {
					if (traverseDelta(w_rooks.pieces[i], attacked))
						moves[attackers++].move = Move.createMove(W_ROOK, w_rooks.pieces[i], attacked,  boardArray[attacked], ORDINARY_MOVE, 0);
				}
			}
			// Queen
			for (int i = 0; i < w_queens.count; i++) {
				pieceAttack = ATTACK_ARRAY[attacked - w_queens.pieces[i] + 128];
				if (pieceAttack != ATTACK_NONE && pieceAttack != ATTACK_N) {
					if (traverseDelta(w_queens.pieces[i], attacked))
						moves[attackers++].move = Move.createMove(W_QUEEN, w_queens.pieces[i], attacked,  boardArray[attacked], ORDINARY_MOVE, 0);
				}
			}
			// King
			// Can't be king so skip
		} else {
			// Pawns, only two possible squares
			// No need for out of bounds checks here since we add to the index
			// (can never get below zero)
			if (((attacked + 17) & 0x88) == 0
					&& boardArray[attacked + 17] == B_PAWN) {
				if(rank(attacked) != 0)
					moves[attackers++].move = Move.createMove(B_PAWN, attacked+17, attacked,  boardArray[attacked], ORDINARY_MOVE, 0);
				else {
					moves[attackers++].move = Move.createMove(B_PAWN, attacked+17, attacked,  boardArray[attacked], PROMOTION_QUEEN, 0);
					moves[attackers++].move = Move.createMove(B_PAWN, attacked+17, attacked,  boardArray[attacked], PROMOTION_ROOK, 0);
					moves[attackers++].move = Move.createMove(B_PAWN, attacked+17, attacked,  boardArray[attacked], PROMOTION_BISHOP, 0);
					moves[attackers++].move = Move.createMove(B_PAWN, attacked+17, attacked,  boardArray[attacked], PROMOTION_KNIGHT, 0);
				}
			}
			if (((attacked + 15) & 0x88) == 0
					&& boardArray[attacked + 15] == B_PAWN) {
				if(rank(attacked) != 0)
					moves[attackers++].move = Move.createMove(B_PAWN, attacked+15, attacked,  boardArray[attacked], ORDINARY_MOVE, 0);
				else {
					moves[attackers++].move = Move.createMove(B_PAWN, attacked+15, attacked,  boardArray[attacked], PROMOTION_QUEEN, 0);
					moves[attackers++].move = Move.createMove(B_PAWN, attacked+15, attacked,  boardArray[attacked], PROMOTION_ROOK, 0);
					moves[attackers++].move = Move.createMove(B_PAWN, attacked+15, attacked,  boardArray[attacked], PROMOTION_BISHOP, 0);
					moves[attackers++].move = Move.createMove(B_PAWN, attacked+15, attacked,  boardArray[attacked], PROMOTION_KNIGHT, 0);
				}
			}
			if (enPassant != -1 && rank(enPassant) == 2 && (attacked - 16) == enPassant) {
				// Check the both squares where an en passant capture ispossible from
				int from = enPassant + 17;
				if ((from & 0x88) == 0) {
					if (boardArray[from] == B_PAWN) {
						moves[attackers++].move = Move.createMove(B_PAWN, from, enPassant, W_PAWN, EN_PASSANT, 0);
						
					}
				}

				from = enPassant + 15;
				if ((from & 0x88) == 0) {
					if (boardArray[from] == B_PAWN) {
						moves[attackers++].move = Move.createMove(B_PAWN, from, enPassant, W_PAWN, EN_PASSANT, 0);
						
					}
				}
			}

			// Knights
			for (int i = 0; i < b_knights.count; i++) {
				if (ATTACK_ARRAY[attacked - b_knights.pieces[i] + 128] == ATTACK_N)
					moves[attackers++].move = Move.createMove(B_KNIGHT, b_knights.pieces[i], attacked,  boardArray[attacked], ORDINARY_MOVE, 0);
			}

			// Bishops
			for (int i = 0; i < b_bishops.count; i++) {
				pieceAttack = ATTACK_ARRAY[attacked - b_bishops.pieces[i] + 128];
				if (pieceAttack == ATTACK_KQBwP || pieceAttack == ATTACK_KQBbP
						|| pieceAttack == ATTACK_QB) {
					if (traverseDelta(b_bishops.pieces[i], attacked))
						moves[attackers++].move = Move.createMove(B_BISHOP, b_bishops.pieces[i], attacked,  boardArray[attacked], ORDINARY_MOVE, 0);
				}
			}
			// Rooks
			for (int i = 0; i < b_rooks.count; i++) {
				pieceAttack = ATTACK_ARRAY[attacked - b_rooks.pieces[i] + 128];
				if (pieceAttack == ATTACK_KQR || pieceAttack == ATTACK_QR) {
					if (traverseDelta(b_rooks.pieces[i], attacked))
						moves[attackers++].move = Move.createMove(B_ROOK, b_rooks.pieces[i], attacked,  boardArray[attacked], ORDINARY_MOVE, 0);
				}
			}
			// Queen
			for (int i = 0; i < b_queens.count; i++) {
				pieceAttack = ATTACK_ARRAY[attacked - b_queens.pieces[i] + 128];
				if (pieceAttack != ATTACK_NONE && pieceAttack != ATTACK_N) {
					if (traverseDelta(b_queens.pieces[i], attacked))
						moves[attackers++].move = Move.createMove(B_QUEEN, b_queens.pieces[i], attacked,  boardArray[attacked], ORDINARY_MOVE, 0);
				}
			}
			// King
			// Can't be king so skip
		}
		return attackers;
	}
	
	/**
	 * Help method for gen_checkEvasions
	 * @param moves will contain the checking move (to reverse engineer later)
	 * @param attacked square that is under attack
	 * @param side who is the attacker
	 * @return Number of attackers found (0-2 obviously)
	 */
	private final int getInterveneMoves(Move[] moves, int attacked, int side, int startIndex) {
		int pieceAttack;
		int attackers = startIndex;

		if (side == WHITE) {
			// Pawns
			for (int i = 0; i < w_pawns.count; i++) {
				int from = w_pawns.pieces[i]; // Index the current pawn is on
				if(file(from) != file(attacked)) continue; // Can't intervene if not on the same file

				// Check if the pawn can reach the square
				if((attacked - from) == 16 || ((attacked-from) == 32 && rank(from) == 1 && boardArray[from+16] == EMPTY_SQUARE)) {
					// Last rank so promotion
					if(rank(attacked) == 7) {
						moves[attackers++].move = Move.createMove(W_PAWN, from, attacked, 0, PROMOTION_QUEEN, 0);
						moves[attackers++].move = Move.createMove(W_PAWN, from, attacked, 0, PROMOTION_ROOK, 0);
						moves[attackers++].move = Move.createMove(W_PAWN, from, attacked, 0, PROMOTION_BISHOP, 0);
						moves[attackers++].move = Move.createMove(W_PAWN, from, attacked, 0, PROMOTION_KNIGHT, 0);
					} else {
						// No last rank so ordinary pawn move
						moves[attackers++].move = Move.createMove(W_PAWN, from, attacked, 0, ORDINARY_MOVE, 0);
					}
				}
			}

			// Knights
			for (int i = 0; i < w_knights.count; i++) {
				if (ATTACK_ARRAY[attacked - w_knights.pieces[i] + 128] == ATTACK_N)
					moves[attackers++].move = Move.createMove(W_KNIGHT, w_knights.pieces[i], attacked, 0, ORDINARY_MOVE, 0);
			}

			// Bishops
			for (int i = 0; i < w_bishops.count; i++) {
				pieceAttack = ATTACK_ARRAY[attacked - w_bishops.pieces[i] + 128];
				if (pieceAttack == ATTACK_KQBwP || pieceAttack == ATTACK_KQBbP
						|| pieceAttack == ATTACK_QB) {
					if (traverseDelta(w_bishops.pieces[i], attacked)) 
						moves[attackers++].move = Move.createMove(W_BISHOP, w_bishops.pieces[i], attacked, 0, ORDINARY_MOVE, 0);
				}
			}
			// Rooks
			for (int i = 0; i < w_rooks.count; i++) {
				pieceAttack = ATTACK_ARRAY[attacked - w_rooks.pieces[i] + 128];
				if (pieceAttack == ATTACK_KQR || pieceAttack == ATTACK_QR) {
					if (traverseDelta(w_rooks.pieces[i], attacked))
						moves[attackers++].move = Move.createMove(W_ROOK, w_rooks.pieces[i], attacked, 0, ORDINARY_MOVE, 0);
				}
			}
			// Queen
			for (int i = 0; i < w_queens.count; i++) {
				pieceAttack = ATTACK_ARRAY[attacked - w_queens.pieces[i] + 128];
				if (pieceAttack != ATTACK_NONE && pieceAttack != ATTACK_N) {
					if (traverseDelta(w_queens.pieces[i], attacked))
						moves[attackers++].move = Move.createMove(W_QUEEN, w_queens.pieces[i], attacked, 0, ORDINARY_MOVE, 0);
				}
			}
			// King
			// Can't be king so skip
		} else {
			// Pawns
			for (int i = 0; i < b_pawns.count; i++) {
				int from = b_pawns.pieces[i]; // Index the current pawn is on
				if(file(from) != file(attacked)) continue; // Can't intervene if not on the same file

				// Check if the pawn can reach the square
				if((from - attacked) == 16 || ((from-attacked) == 32 && rank(from) == 6 && boardArray[from-16] == EMPTY_SQUARE)) {
					// Last rank so promotion
					if(rank(attacked) == 0) {
						moves[attackers++].move = Move.createMove(B_PAWN, from, attacked, 0, PROMOTION_QUEEN, 0);
						moves[attackers++].move = Move.createMove(B_PAWN, from, attacked, 0, PROMOTION_ROOK, 0);
						moves[attackers++].move = Move.createMove(B_PAWN, from, attacked, 0, PROMOTION_BISHOP, 0);
						moves[attackers++].move = Move.createMove(B_PAWN, from, attacked, 0, PROMOTION_KNIGHT, 0);
					} else {
						// No last rank so ordinary pawn move
						moves[attackers++].move = Move.createMove(B_PAWN, from, attacked, 0, ORDINARY_MOVE, 0);
					}
				}
			}

			// Knights
			for (int i = 0; i < b_knights.count; i++) {
				if (ATTACK_ARRAY[attacked - b_knights.pieces[i] + 128] == ATTACK_N)
					moves[attackers++].move = Move.createMove(B_KNIGHT, b_knights.pieces[i], attacked, 0, ORDINARY_MOVE, 0);
			}

			// Bishops
			for (int i = 0; i < b_bishops.count; i++) {
				pieceAttack = ATTACK_ARRAY[attacked - b_bishops.pieces[i] + 128];
				if (pieceAttack == ATTACK_KQBwP || pieceAttack == ATTACK_KQBbP
						|| pieceAttack == ATTACK_QB) {
					if (traverseDelta(b_bishops.pieces[i], attacked))
						moves[attackers++].move = Move.createMove(B_BISHOP, b_bishops.pieces[i], attacked, 0, ORDINARY_MOVE, 0);
				}
			}
			// Rooks
			for (int i = 0; i < b_rooks.count; i++) {
				pieceAttack = ATTACK_ARRAY[attacked - b_rooks.pieces[i] + 128];
				if (pieceAttack == ATTACK_KQR || pieceAttack == ATTACK_QR) {
					if (traverseDelta(b_rooks.pieces[i], attacked))
						moves[attackers++].move = Move.createMove(B_ROOK, b_rooks.pieces[i], attacked, 0, ORDINARY_MOVE, 0);
				}
			}
			// Queen
			for (int i = 0; i < b_queens.count; i++) {
				pieceAttack = ATTACK_ARRAY[attacked - b_queens.pieces[i] + 128];
				if (pieceAttack != ATTACK_NONE && pieceAttack != ATTACK_N) {
					if (traverseDelta(b_queens.pieces[i], attacked))
						moves[attackers++].move = Move.createMove(B_QUEEN, b_queens.pieces[i], attacked, 0, ORDINARY_MOVE, 0);
				}
			}
			// King
			// Can't be king so skip
		}
		return attackers;
	}

	/**
	 * Returns all pseudo legal evasions from a check
	 * @param moves
	 * @param startIndex
	 * @return
	 */
	public final int gen_checkEvasions(Move[] moves, int startIndex) {
		int nMoves = 0;
		if(toMove == WHITE_TO_MOVE) {
			int attackers = getAttackers(tempArray, w_king.pieces[0], BLACK);
			if(attackers == 0) return 0; // Not in check
			
			int attacker1 = tempArray[0];
			//int attacker2 = tempArray[1];
			int attacker1delta = DELTA_ARRAY[w_king.pieces[0] - Move.fromIndex(tempArray[0]) + 128];
			//int attacker2delta = DELTA_ARRAY[w_king.pieces[0] - Move.fromIndex(tempArray[1]) + 128];
			
			// Add king moves
			for (int i = 0; i < 8; i++) {
				//if(king_delta[i] == attacker1delta || king_delta[i] == -1*attacker1delta) continue;
				int deltaIndex = w_king.pieces[0];
				deltaIndex += king_delta[i];

				// Don't add if outside board, own piece occupying the square or it's attacked by opponent
				if((deltaIndex & 0x88) == 0 && (boardArray[w_king.pieces[0]] * boardArray[deltaIndex]) <= 0) {
					moves[nMoves++].move = Move.createMove(
							W_KING, w_king.pieces[0], deltaIndex, boardArray[deltaIndex],
							ORDINARY_MOVE, 0);
				}
			}
			
			// Double check so done (only king moving evasions possible)
			if(attackers >=2) return nMoves;
			
			// Capture the attacking piece
			nMoves = getAttackingMoves(moves, attacker1, WHITE, nMoves);
			
			// If attacker is sliding it's possible to intervene as well, so get those
			if(!(boardArray[attacker1] == B_PAWN) && !(boardArray[attacker1] == B_KNIGHT)) {
				int deltaIndex = attacker1 + attacker1delta;
				while((deltaIndex & 0x88) == 0 && boardArray[deltaIndex] != W_KING) {
					nMoves = getInterveneMoves(moves, deltaIndex, WHITE, nMoves);
					deltaIndex += attacker1delta;
				}
			}			
		} else {
			int attackers = getAttackers(tempArray, b_king.pieces[0], WHITE);
			if(attackers == 0) return 0; // Not in check
			
			int attacker1 = tempArray[0];
			//int attacker2 = tempArray[1];
			int attacker1delta = DELTA_ARRAY[b_king.pieces[0] - Move.fromIndex(tempArray[0]) + 128];
			//int attacker2delta = DELTA_ARRAY[w_king.pieces[0] - Move.fromIndex(tempArray[1]) + 128];
			
			// Add king moves
			for (int i = 0; i < 8; i++) {
				//if(king_delta[i] == attacker1delta || king_delta[i] == -1*attacker1delta) continue;
				int deltaIndex = b_king.pieces[0];
				deltaIndex += king_delta[i];

				// Don't add if outside board, own piece occupying the square or it's attacked by opponent
				if((deltaIndex & 0x88) == 0 && (boardArray[b_king.pieces[0]] * boardArray[deltaIndex]) <= 0) {
					moves[nMoves++].move = Move.createMove(
							B_KING, b_king.pieces[0], deltaIndex, boardArray[deltaIndex],
							ORDINARY_MOVE, 0);
				}
			}
			
			// Double check so done (only king moving evasions possible)
			if(attackers >=2) return nMoves;
			
			// Capture the attacking piece
			nMoves = getAttackingMoves(moves, attacker1, BLACK, nMoves);
			
			// If attacker is sliding it's possible to intervene as well, so get those
			if(!(boardArray[attacker1] == W_PAWN) && !(boardArray[attacker1] == W_KNIGHT)) {
				int deltaIndex = attacker1 + attacker1delta;
				while((deltaIndex & 0x88) == 0 && boardArray[deltaIndex] != B_KING) {
					nMoves = getInterveneMoves(moves, deltaIndex, BLACK, nMoves);
					deltaIndex += attacker1delta;
				}
			}	
		}
		
		return nMoves;
	}
	
	/**
	 * Takes an index and checks if the index can be attacked by 'side'
	 * 
	 * @param attacked
	 *            The attacked index
	 * @param side
	 *            The side that is attacking (white: 1, black: -1)
	 * @return boolean True it can be attacked, false it can't
	 */
	public final boolean isAttacked(int attacked, int side) // add side here
	{
		int pieceAttack;

		if (side == WHITE) // White is attacking
		{
			// Pawns, only two possible squares
			if (((attacked - 17) & 0x88) == 0
					&& boardArray[attacked - 17] == W_PAWN)
				return true;
			if (((attacked - 15) & 0x88) == 0
					&& boardArray[attacked - 15] == W_PAWN)
				return true;

			// Knights
			for (int i = 0; i < w_knights.count; i++) {
				if (ATTACK_ARRAY[attacked - w_knights.pieces[i] + 128] == ATTACK_N)
					return true;
			}

			// Bishops
			for (int i = 0; i < w_bishops.count; i++) {
				pieceAttack = ATTACK_ARRAY[attacked - w_bishops.pieces[i] + 128];
				if (pieceAttack == ATTACK_KQBwP || pieceAttack == ATTACK_KQBbP
						|| pieceAttack == ATTACK_QB) {
					if (traverseDelta(w_bishops.pieces[i], attacked))
						return true;
				}
			}
			// Rooks
			for (int i = 0; i < w_rooks.count; i++) {
				pieceAttack = ATTACK_ARRAY[attacked - w_rooks.pieces[i] + 128];
				if (pieceAttack == ATTACK_KQR || pieceAttack == ATTACK_QR) {
					if (traverseDelta(w_rooks.pieces[i], attacked))
						return true;
				}
			}
			// Queen
			for (int i = 0; i < w_queens.count; i++) {
				pieceAttack = ATTACK_ARRAY[attacked - w_queens.pieces[i] + 128];
				if (pieceAttack != ATTACK_NONE && pieceAttack != ATTACK_N) {
					if (traverseDelta(w_queens.pieces[i], attacked))
						return true;
				}
			}
			// King
			pieceAttack = ATTACK_ARRAY[attacked - w_king.pieces[0] + 128];
			if (pieceAttack == ATTACK_KQBwP || pieceAttack == ATTACK_KQBbP
					|| pieceAttack == ATTACK_KQR) {
				return true;
			}
		} else // Black is attacking
		{
			// Pawns, only two possible squares
			// No need for out of bounds checks here since we add to the index
			// (can never get below zero)
			if (((attacked + 17) & 0x88) == 0
					&& boardArray[attacked + 17] == B_PAWN)
				return true;
			if (((attacked + 15) & 0x88) == 0
					&& boardArray[attacked + 15] == B_PAWN)
				return true;

			// Knights
			for (int i = 0; i < b_knights.count; i++) {
				if (ATTACK_ARRAY[attacked - b_knights.pieces[i] + 128] == ATTACK_N)
					return true;
			}

			// Bishops
			for (int i = 0; i < b_bishops.count; i++) {
				pieceAttack = ATTACK_ARRAY[attacked - b_bishops.pieces[i] + 128];
				if (pieceAttack == ATTACK_KQBwP || pieceAttack == ATTACK_KQBbP
						|| pieceAttack == ATTACK_QB) {
					if (traverseDelta(b_bishops.pieces[i], attacked))
						return true;
				}
			}
			// Rooks
			for (int i = 0; i < b_rooks.count; i++) {
				pieceAttack = ATTACK_ARRAY[attacked - b_rooks.pieces[i] + 128];
				if (pieceAttack == ATTACK_KQR || pieceAttack == ATTACK_QR) {
					if (traverseDelta(b_rooks.pieces[i], attacked))
						return true;
				}
			}
			// Queen
			for (int i = 0; i < b_queens.count; i++) {
				pieceAttack = ATTACK_ARRAY[attacked - b_queens.pieces[i] + 128];
				if (pieceAttack != ATTACK_NONE && pieceAttack != ATTACK_N) {
					if (traverseDelta(b_queens.pieces[i], attacked))
						return true;
				}
			}
			// King
			pieceAttack = ATTACK_ARRAY[attacked - b_king.pieces[0] + 128];
			if (pieceAttack == ATTACK_KQBwP || pieceAttack == ATTACK_KQBbP
					|| pieceAttack == ATTACK_KQR) {
				return true;
			}
		}
		return false; // If the loops didn't return true, no piece can attack
						// the square
	} // END isAttacked()
	

	/**
	 * Used by isAttacked() to traverse a piece's delta to see if it runs in to
	 * any pieces on the way to the attacked square
	 * 
	 * Important: May not be called with an attacker that can't reach the
	 * attacked square by its delta. Will cause endless loop. The safety
	 * measures are commented out below for a small gain in time.
	 * 
	 * @param attacker
	 *            The attacking square
	 * @param attacked
	 *            The attacked square
	 * @return boolean True if the piece can reach the attacked square, false if
	 *         not
	 */
	public final boolean traverseDelta(int attacker, int attacked) {
		int deltaIndex = attacker; // Initialize from first square
		int delta = DELTA_ARRAY[attacked - attacker + 128]; // Find the delta
															// needed

		// while((deltaIndex & 0x88) == 0) // Traverse until off the board
		while (true) {
			deltaIndex += delta; // Add the delta to move to the next square

			// We reached the attacked square, so we return true
			if (deltaIndex == attacked)
				return true;

			// A piece was found on the way, so return false
			if (boardArray[deltaIndex] != EMPTY_SQUARE)
				return false;
		}
	} // END traverseDelta()

	/**
	 * @param index
	 *            The index to check
	 * @return The rank the index is located on (index 18 gives (18-(18%16))/16 =
	 *         rank 1)
	 */
	public static final int rank(int index) { return (index - (index % 16)) / 16; }

	/**
	 * @param index
	 *            The index to check
	 * @return The row (file) the index is located on (index 18 gives 18%16 =
	 *         row 2)
	 */
	public static final int file(int index) { return index % 16; }

	/**
	 * Returns the shortest distance between two squares
	 * 
	 * @param squareA
	 * @param squareB
	 * @return distance The distance between the squares
	 */
	public static final int distance(int squareA, int squareB) {
		return Math.max(Math.abs(file(squareA) - file(squareB)), Math.abs(rank(squareA) - rank(squareB)));
	} // END distance()

	public final int gen_noncaps(Move[] moves, int startIndex) {
		int moveIndex = startIndex;
		int from, to;
		int pieceType;

		if (toMove == WHITE_TO_MOVE) {
			// Castling

			// Short, we can assume king and rook are in the right places or
			// else there wouldn't be castling rights
			if (white_castle == CASTLE_SHORT || white_castle == CASTLE_BOTH) {
				// Squares between king and rook needs to be empty
				if ((boardArray[F1] == EMPTY_SQUARE)
						&& (boardArray[G1] == EMPTY_SQUARE)) {
					// King and the square that is castled over can't be
					// attacked, castling into check is handled like an ordinary
					// move into check move
					if (!isAttacked(E1, BLACK) && !isAttacked(F1, BLACK)) {
						moves[moveIndex++].move = Move.createMove(
								W_KING, E1, G1, 0, SHORT_CASTLE, 0);
					}
				}
			}

			// Long
			if (white_castle == CASTLE_LONG || white_castle == CASTLE_BOTH) {
				if ((boardArray[D1] == EMPTY_SQUARE)
						&& (boardArray[C1] == EMPTY_SQUARE)
						&& (boardArray[B1]) == EMPTY_SQUARE) {
					if (!isAttacked(E1, BLACK) && !isAttacked(D1, BLACK)) {
						moves[moveIndex++].move = Move.createMove(
								W_KING, E1, C1, 0, LONG_CASTLE, 0);
					}
				}
			}

			// Pawns
			for (int i = 0; i < w_pawns.count; i++) {
				from = w_pawns.pieces[i]; // Index the current pawn is on

				// TODO: Queen promotions should perhaps be in gen_caps instead

				to = from + 16; // Up
				pieceType = boardArray[to];
				if (pieceType == EMPTY_SQUARE) // Pawn can move forward
				{
					if (rank(to) == 7) // Reached the last rank add promotions
					{
						moves[moveIndex++].move = Move.createMove(
								W_PAWN, from, to, 0, PROMOTION_QUEEN, 0);
						moves[moveIndex++].move = Move.createMove(
								W_PAWN, from, to, 0, PROMOTION_ROOK, 0);
						moves[moveIndex++].move = Move.createMove(
								W_PAWN, from, to, 0, PROMOTION_BISHOP, 0);
						moves[moveIndex++].move = Move.createMove(
								W_PAWN, from, to, 0, PROMOTION_KNIGHT, 0);
					} else // Ordinary
					{
						moves[moveIndex++].move = Move.createMove(
								W_PAWN, from, to, 0, ORDINARY_MOVE, 0);

						if (rank(from) == 1) // First move by the pawn so it
												// can move two squares as well
						{
							to += 16; // Move another square
							if (boardArray[to] == EMPTY_SQUARE) // The square is
																// also empty so
																// we can add it
							{
								moves[moveIndex++].move = Move
										.createMove(W_PAWN, from, to, 0,
												ORDINARY_MOVE, 0);
							}
						}
					}

				}
			}

			// Knights
			for (int i = 0; i < w_knights.count; i++) {
				moveIndex += gen_noncaps_delta(w_knights.pieces[i],
						knight_delta,8, false, moves,moveIndex);
			}
			// Bishops
			for (int i = 0; i < w_bishops.count; i++) {
				moveIndex += gen_noncaps_delta(w_bishops.pieces[i],
						bishop_delta,4, true, moves,moveIndex);
			}
			// Rooks
			for (int i = 0; i < w_rooks.count; i++) {
				moveIndex += gen_noncaps_delta(w_rooks.pieces[i],
						rook_delta,4, true, moves, moveIndex);
			}
			// Queen
			for (int i = 0; i < w_queens.count; i++) {
				moveIndex += gen_noncaps_delta(w_queens.pieces[i],
						queen_delta,8, true, moves,moveIndex);
			}
			// King
			moveIndex += gen_noncaps_delta(w_king.pieces[0], king_delta,8,
					false, moves,moveIndex);

		} else // Black to move
		{
			// Castling

			// Short, we can assume king and rook are in the right places or
			// else there wouldn't be castling rights
			if (black_castle == CASTLE_SHORT || black_castle == CASTLE_BOTH) {
				// Squares between king and rook needs to be empty
				if ((boardArray[F8] == EMPTY_SQUARE)
						&& (boardArray[G8] == EMPTY_SQUARE)) {
					// King and the square that is castled over can't be
					// attacked, castling into check is handled like an ordinary
					// move into check move
					if (!isAttacked(E8, WHITE) && !isAttacked(F8, WHITE)) {
						moves[moveIndex++].move = Move.createMove(
								B_KING, E8, G8, 0, SHORT_CASTLE, 0);
					}
				}
			}

			// Long
			if (black_castle == CASTLE_LONG || black_castle == CASTLE_BOTH) {
				if ((boardArray[D8] == EMPTY_SQUARE)
						&& (boardArray[C8] == EMPTY_SQUARE)
						&& (boardArray[B8]) == EMPTY_SQUARE) {
					if (!isAttacked(E8, WHITE) && !isAttacked(D8, WHITE)) {
						moves[moveIndex++].move = Move.createMove(
								B_KING, E8, C8, 0, LONG_CASTLE, 0);
					}
				}
			}

			for (int i = 0; i < b_pawns.count; i++) {
				from = b_pawns.pieces[i]; // Index the current pawn is on

				to = from - 16; // Down
				pieceType = boardArray[to];
				if (pieceType == EMPTY_SQUARE) {
					if (rank(to) == 0) {
						moves[moveIndex++].move = Move.createMove(
								B_PAWN, from, to, 0, PROMOTION_QUEEN, 0);
						moves[moveIndex++].move = Move.createMove(
								B_PAWN, from, to, 0, PROMOTION_ROOK, 0);
						moves[moveIndex++].move = Move.createMove(
								B_PAWN, from, to, 0, PROMOTION_BISHOP, 0);
						moves[moveIndex++].move = Move.createMove(
								B_PAWN, from, to, 0, PROMOTION_KNIGHT, 0);
					} else // Ordinary capture
					{
						moves[moveIndex++].move = Move.createMove(
								B_PAWN, from, to, 0, ORDINARY_MOVE, 0);

						if (rank(from) == 6) // First move by the pawn so it
												// can move two squares as well
						{
							to -= 16; // Move another square
							if (boardArray[to] == EMPTY_SQUARE) // The square is
																// also empty so
																// we can add it
							{
								moves[moveIndex++].move = Move
										.createMove(B_PAWN, from, to, 0,
												ORDINARY_MOVE, 0);
							}
						}
					}
				}

			}
			// Knights
			for (int i = 0; i < b_knights.count; i++) {
				moveIndex += gen_noncaps_delta(b_knights.pieces[i],
						knight_delta,8, false, moves,moveIndex);
			}
			// Bishops
			for (int i = 0; i < b_bishops.count; i++) {
				moveIndex += gen_noncaps_delta(b_bishops.pieces[i],
						bishop_delta,4, true, moves,moveIndex);
			}
			// Rooks
			for (int i = 0; i < b_rooks.count; i++) {
				moveIndex += gen_noncaps_delta(b_rooks.pieces[i],
						rook_delta,4, true, moves,moveIndex);
			}
			// Queen
			for (int i = 0; i < b_queens.count; i++) {
				moveIndex += gen_noncaps_delta(b_queens.pieces[i],
						queen_delta,8, true, moves,moveIndex);
			}
			// King
			moveIndex += gen_noncaps_delta(b_king.pieces[0], king_delta,8,
					false, moves,moveIndex);
		}

		return moveIndex - startIndex;
	} // END gen_noncaps()

	/**
	 * Fills the moves array with pseudo legal captures and queen promotions
	 * (used for quiescence search)
	 * @param moves
	 * @param startIndex
	 * @return
	 */
	public final int gen_caps_and_promotions(Move[] moves, int startIndex) {
		int moveIndex = gen_caps(moves, startIndex);
		int from,to,pieceType;
		if(toMove == WHITE_TO_MOVE) {
			for (int i = 0; i < w_pawns.count; i++) {
				from = w_pawns.pieces[i]; // Index the current pawn is on
				to = from + 16; // Up
				pieceType = boardArray[to];

				// Pawn can move forward
				if (pieceType == EMPTY_SQUARE) 	{
					// Reached the last rank add promotion
					if (rank(to) == 7) 	{
						moves[moveIndex++].move = Move.createMove(W_PAWN, from, to, 0, PROMOTION_QUEEN, 0);
					}
				}
			}
		} else { // Black to move
			for (int i = 0; i < b_pawns.count; i++) {
				from = b_pawns.pieces[i]; // Index the current pawn is on
				to = from - 16; // Down
				pieceType = boardArray[to];
				if (pieceType == EMPTY_SQUARE) {
					if (rank(to) == 0) {
						moves[moveIndex++].move = Move.createMove(B_PAWN, from, to, 0, PROMOTION_QUEEN, 0);
					}
				}
			}
		}
		return moveIndex;
	} // END gen_caps_and_promotions
	
	/**
	 * Fill the moves array from startIndex with pseudo legal captures
	 * 
	 * @param moves
	 *            The array to fill
	 * @param startIndex
	 *            Where to start filling
	 * @return totalMovesAdded The number of captures added
	 */
	public final int gen_caps(Move[] moves, int startIndex) {
		int moveIndex = startIndex;
		int from, to;
		int pieceType; // Holds the piece type of the index

		if (toMove == WHITE_TO_MOVE) {

			// Loop through the pawn indexes, if the index does not contain a
			// pawn, the pawn was
			// promoted and we add the moves for that piece instead
			for (int i = 0; i < w_pawns.count; i++) {
				from = w_pawns.pieces[i]; // Index the current pawn is on

				// Generate moves for the pawn in the two different capture
				// directions
				// Note: we do not have to check for out of bounds since there
				// will never be
				// anything but 0 (empty square) outside of the board

				// TODO: Queen promotions without capture might belong here so
				// we check them in quiescent search and early in ordinary
				// search

				to = from + 17; // Up right
				if ((to & 0x88) == 0) {
					pieceType = boardArray[to];
					if (pieceType < 0) // Black piece
					{
						if (rank(to) == 7) // Reached the last rank with the
											// capture so add promotions
						{
							moves[moveIndex++].move = Move
									.createMove(W_PAWN, from, to, pieceType,
											PROMOTION_QUEEN, 0);
							
							moves[moveIndex++].move = Move
									.createMove(W_PAWN, from, to, pieceType,
											PROMOTION_ROOK, 0);
							
							moves[moveIndex++].move = Move
									.createMove(W_PAWN, from, to, pieceType,
											PROMOTION_BISHOP, 0);
							
							moves[moveIndex++].move = Move
									.createMove(W_PAWN, from, to, pieceType,
											PROMOTION_KNIGHT, 0);
							
						} else // Ordinary capture
						{
							moves[moveIndex++].move = Move
									.createMove(W_PAWN, from, to, pieceType,
											ORDINARY_MOVE, 0);
							
						}
					}
				}

				to = from + 15; // Up left
				if ((to & 0x88) == 0) {
					pieceType = boardArray[to];
					if (pieceType < 0) // Black piece
					{
						if (rank(to) == 7) // Reached the last rank with the
											// capture so add promotions
						{
							moves[moveIndex++].move = Move
									.createMove(W_PAWN, from, to, pieceType,
											PROMOTION_QUEEN, 0);
							
							moves[moveIndex++].move = Move
									.createMove(W_PAWN, from, to, pieceType,
											PROMOTION_ROOK, 0);
							
							moves[moveIndex++].move = Move
									.createMove(W_PAWN, from, to, pieceType,
											PROMOTION_BISHOP, 0);
							
							moves[moveIndex++].move = Move
									.createMove(W_PAWN, from, to, pieceType,
											PROMOTION_KNIGHT, 0);
							
						} else // Ordinary capture
						{
							moves[moveIndex++].move = Move
									.createMove(W_PAWN, from, to, pieceType,
											ORDINARY_MOVE, 0);
							
						}
					}
				}
			}

			// Now add any possible en passant
			if (enPassant != -1 && rank(enPassant) == 5) {
				// Check the both squares where an en passant capture is
				// possible from
				from = enPassant - 17;
				if ((from & 0x88) == 0) {
					if (boardArray[from] == W_PAWN) {
						moves[moveIndex++].move = Move.createMove(
								W_PAWN, from, enPassant, B_PAWN, EN_PASSANT, 0);
						
					}
				}

				from = enPassant - 15;
				if ((from & 0x88) == 0) {
					if (boardArray[from] == W_PAWN) {
						moves[moveIndex++].move = Move.createMove(
								W_PAWN, from, enPassant, B_PAWN, EN_PASSANT, 0);
						
					}
				}
			}

			// Knights
			for (int i = 0; i < w_knights.count; i++) {
				moveIndex += gen_caps_delta(w_knights.pieces[i],
						knight_delta,8, false, moves,
						moveIndex);
			}
			// Bishops
			for (int i = 0; i < w_bishops.count; i++) {
				moveIndex += gen_caps_delta(w_bishops.pieces[i],
						bishop_delta,4, true, moves,
						moveIndex);
			}
			// Rooks
			for (int i = 0; i < w_rooks.count; i++) {
				moveIndex += gen_caps_delta(w_rooks.pieces[i],
						rook_delta,4, true, moves, moveIndex);
			}
			// Queen
			for (int i = 0; i < w_queens.count; i++) {
				moveIndex += gen_caps_delta(w_queens.pieces[i],
						queen_delta,8, true, moves,
						moveIndex);
			}
			// King
			moveIndex += gen_caps_delta(w_king.pieces[0], king_delta,8,
					false, moves, moveIndex);
		} else // Black to move
		{

			// Loop through the pawn indexes, if the index does not contain a
			// pawn, the pawn was
			// promoted and we add the moves for that piece instead
			for (int i = 0; i < b_pawns.count; i++) {
				from = b_pawns.pieces[i]; // Index the current pawn is on

				// Generate moves for the pawn in the two different capture
				// directions
				// Note: we do not have to check for out of bounds since there
				// will never be
				// anything but 0 (empty square) outside of the board

				// TODO: Queen promotions without capture might belong here so
				// we check them in quiescent search and early in ordinary
				// search

				to = from - 17; // Down right
				if ((to & 0x88) == 0) {
					pieceType = boardArray[to];
					if (pieceType > 0) // White piece
					{
						if (rank(to) == 0) // Reached the last rank with the
											// capture so add promotions
						{
							moves[moveIndex++].move = Move
									.createMove(B_PAWN, from, to, pieceType,
											PROMOTION_QUEEN, 0);
							
							moves[moveIndex++].move = Move
									.createMove(B_PAWN, from, to, pieceType,
											PROMOTION_ROOK, 0);
							
							moves[moveIndex++].move = Move
									.createMove(B_PAWN, from, to, pieceType,
											PROMOTION_BISHOP, 0);
							
							moves[moveIndex++].move = Move
									.createMove(B_PAWN, from, to, pieceType,
											PROMOTION_KNIGHT, 0);
							
						} else // Ordinary capture
						{
							moves[moveIndex++].move = Move
									.createMove(B_PAWN, from, to, pieceType,
											ORDINARY_MOVE, 0);
							
						}
					}
				}

				to = from - 15; // Down left
				if ((to & 0x88) == 0) {
					pieceType = boardArray[to];
					if (pieceType > 0) // White piece
					{
						if (rank(to) == 0) // Reached the last rank with the
											// capture so add promotions
						{
							moves[moveIndex++].move = Move
									.createMove(B_PAWN, from, to, pieceType,
											PROMOTION_QUEEN, 0);
							
							moves[moveIndex++].move = Move
									.createMove(B_PAWN, from, to, pieceType,
											PROMOTION_ROOK, 0);
							
							moves[moveIndex++].move = Move
									.createMove(B_PAWN, from, to, pieceType,
											PROMOTION_BISHOP, 0);
							
							moves[moveIndex++].move = Move
									.createMove(B_PAWN, from, to, pieceType,
											PROMOTION_KNIGHT, 0);
							
						} else // Ordinary capture
						{
							moves[moveIndex++].move = Move
									.createMove(B_PAWN, from, to, pieceType,
											ORDINARY_MOVE, 0);
							
						}
					}
				}

			}

			// Now add any possible en passant
			if (enPassant != -1 && rank(enPassant) == 2) {
				// Check the both squares where an en passant capture is
				// possible from
				from = enPassant + 17;
				if ((from & 0x88) == 0) {
					if (boardArray[from] == B_PAWN) {
						moves[moveIndex++].move = Move.createMove(
								B_PAWN, from, enPassant, W_PAWN, EN_PASSANT, 0);
						
					}
				}

				from = enPassant + 15;
				if ((from & 0x88) == 0) {
					if (boardArray[from] == B_PAWN) {
						moves[moveIndex++].move = Move.createMove(
								B_PAWN, from, enPassant, W_PAWN, EN_PASSANT, 0);
						
					}
				}
			}
			// Knights
			for (int i = 0; i < b_knights.count; i++) {
				moveIndex += gen_caps_delta(b_knights.pieces[i],
						knight_delta,8, false, moves,
						moveIndex);
			}
			// Bishops
			for (int i = 0; i < b_bishops.count; i++) {
				moveIndex += gen_caps_delta(b_bishops.pieces[i],
						bishop_delta,4, true, moves,
						moveIndex);
			}
			// Rooks
			for (int i = 0; i < b_rooks.count; i++) {
				moveIndex += gen_caps_delta(b_rooks.pieces[i],
						rook_delta,4, true, moves, moveIndex);
			}
			// Queen
			for (int i = 0; i < b_queens.count; i++) {
				moveIndex += gen_caps_delta(b_queens.pieces[i],
						queen_delta,8, true, moves,
						moveIndex);
			}
			// King
			moveIndex += gen_caps_delta(b_king.pieces[0], king_delta,8,
					false, moves, moveIndex);
		}

		return moveIndex - startIndex;
	} // END gen_caps()

	/**
	 * Takes an index, a delta, sliding/non-sliding boolean and the an array and
	 * fills the array with all possible non captures for the piece
	 * 
	 * @param index
	 *            The index the piece is on
	 * @param delta
	 *            The piece's delta
	 * @param sliding
	 *            Sliding/non-sliding piece
	 * @param moves
	 *            The array to be filled
	 * @param startIndex
	 *            The index where to start filling the array
	 * @return totalMovesAdded The number of moves that were added to the array
	 */
	private final int gen_noncaps_delta(int index, int[] delta, int nDelta, boolean sliding,
			Move[] moves, int startIndex) {
		int moveIndex = startIndex;
		// Record the board's en passant square, white/black castling rights and
		// half-moves
		for (int i = 0; i < nDelta; i++) // Loop through the 8 possible deltas
		{
			// Get the index of a square one step away from the orignal square
			// by using the current delta
			int deltaIndex = index;
			deltaIndex += delta[i];

			/* Loop until out of moves, off the board or run into a piece */
			while((deltaIndex & 0x88) == 0 && boardArray[deltaIndex] == EMPTY_SQUARE) {
				moves[moveIndex++].move = Move.createMove(
						boardArray[index], index, deltaIndex, 0,
						ORDINARY_MOVE, 0);

				if(!sliding) break;

				deltaIndex += delta[i];
			}
		}
		return moveIndex - startIndex;
	} // END gen_noncaps_delta()

	/**
	 * Takes an index, a delta, sliding/non-sliding boolean and the an array and
	 * fills the array with all possible captures for the piece
	 * 
	 * @param index
	 *            The index the piece is on
	 * @param delta
	 *            The piece's delta
	 * @param sliding
	 *            Sliding/non-sliding piece
	 * @param moves
	 *            The array to be filled
	 * @param startIndex
	 *            The index where to start filling the array
	 * @return totalMovesAdded The number of moves that were added to the array
	 */
	private final int gen_caps_delta(int index, int[] delta, int nDelta, boolean sliding,
			Move[] moves, int startIndex) {
		int moveIndex = startIndex;
		// Record the board's en passant square, white/black castling rights and
		// half-moves
		for (int i = 0; i < nDelta; i++) // Loop through the 8 possible deltas
		{
			// Get the index of a square one step away from the orignal square
			// by using the current delta
			int deltaIndex = index;
			deltaIndex += delta[i];

			/* Loop until out of moves, off the board or run into a piece */
			while((deltaIndex & 0x88) == 0) {
				if(boardArray[deltaIndex] == EMPTY_SQUARE) {
					if(!sliding) break;
				} else if ((boardArray[deltaIndex] * boardArray[index]) < 0){
					moves[moveIndex++].move = Move.createMove(
							boardArray[index], index, deltaIndex, boardArray[deltaIndex],
							ORDINARY_MOVE, 0);

					break;
				} else {
					break;
				}
				
				deltaIndex += delta[i];
			}
		}
		return moveIndex - startIndex;
	} // END gen_caps_delta()
	
	/**
	 *  Takes a board and checks for mate/stalemate, this method assumes
	 *  that there are no legal moves for the side on the move
	 *
	 *  @param board The board to check
	 *  @param ply What ply the mate is found on
	 *  @return int The evaluation
	 */
	public static int mateCheck(Board board, int ply)
	{
		int king_square;		
		if(board.toMove == WHITE_TO_MOVE) king_square = board.w_king.pieces[0];
		else king_square = board.b_king.pieces[0];

		board.toMove *= -1; // Switch side to move on the board to get the other side's pieces to be the attacker
		if(board.isAttacked(king_square, board.toMove))
		{
			board.toMove *= -1; // Switch back side to move	
			return (MATE_VALUE + ply); 
		}
		else
		{
			board.toMove *= -1; // Switch back side to move
			return -DRAW_VALUE;
		}	



	}
	//END mateCheck()

	/**
	 *  Similar to mateCheck but only reports if the king of the side moving
	 *  is in check or not
	 *
	 *  @param board The board to check
	 *  @return boolean true if the king of the side moving is in check, false if is not
	 */
	public boolean isInCheck()
	{
		int king_square;		
		if(toMove == WHITE_TO_MOVE) king_square = w_king.pieces[0];
		else king_square = b_king.pieces[0];

		toMove *= -1; // Switch side to move on the board to get the other side's pieces to be the attacker
		if(isAttacked(king_square, toMove))
		{
			toMove *= -1; // Switch back side to move	
			return true; // The king is in check
		}		
		toMove *= -1; // Switch back side to move	
		return false; // The king is not in check
	}
	//END isInCheck()
}
