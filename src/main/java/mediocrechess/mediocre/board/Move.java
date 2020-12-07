package mediocrechess.mediocre.board;

import mediocrechess.mediocre.def.Definitions;

/**
 * class Move
 * 
 * Contains static methods to analyze a move integer
 * 
 * First created: 2006-02-11
 * 
 * @author Jonatan Pettersson (mediocrechess@gmail.com)
 */
public class Move implements Definitions {
	public int move;
	public int score;
	
	// Number of bits to shift to get the to-index
	public static final int TO_SHIFT = 7;
	// To get the piece moving
	public static final int PIECE_SHIFT = 14;
	// To get the capture
	public static final int CAPTURE_SHIFT = 18;
	// To the move type
	public static final int TYPE_SHIFT = 22;
	// To get the ordering value
	public static final int ORDERING_SHIFT = 25;
	// 7 bits, masks out the rest of the int when it has been shifted so we only
	// get the information we need
	public static final int SQUARE_MASK = 127;
	// 4 bits
	public static final int PIECE_MASK = 15;
	// 3 bits
	public static final int TYPE_MASK = 7;
	// Equals 00000001111111111111111111111111 use with & which clears the
	// ordering value
	public static final int ORDERING_CLEAR = 0x1FFFFFF;

	/**
	 * @return int Piece moving
	 */
	public static int pieceMoving(int move) {
		// 7 is the offset, so we get negative values for black pieces
		return ((move >> PIECE_SHIFT) & PIECE_MASK) - 7;
	}

	/**
	 * @return int To-index
	 */
	public static int toIndex(int move) {
		return ((move >> TO_SHIFT) & SQUARE_MASK);
	}

	/**
	 * @return int From-index
	 */
	public static int fromIndex(int move) {
		// Since the from-index is first in the integer it doesn't need to be
		// shifted first
		return (move & SQUARE_MASK);
	}

	/**
	 * @return int Piece captured
	 */
	public static int capture(int move) {
		return ((move >> CAPTURE_SHIFT) & PIECE_MASK) - 7;
	}

	/**
	 * @return int Move type
	 */
	public static int moveType(int move) {
		return ((move >> TYPE_SHIFT) & TYPE_MASK);
	}

	/**
	 * @return int Ordering value
	 */
	public static int orderingValue(int move) {
		// Since the ordering value is last in the integer it doesn't need a
		// mask
		return (move >> ORDERING_SHIFT);
	}

	/**
	 * Clears the ordering value and sets it to the new number
	 * 
	 * Important: Ordering value in the move integer cannot be >127
	 * 
	 * @param move
	 *            The move to change
	 * @param value
	 *            The new ordering value
	 * @return move The changed moved integer
	 */
	public static int setOrderingValue(int move, int value) {
		// Clear the ordering value
		move = (move & ORDERING_CLEAR);
		// Change the ordering value and return the new move integer
		return (move | (value << ORDERING_SHIFT));
	}

	/**
	 * Creates a move integer from the gives values
	 * 
	 * @param pieceMoving
	 * @param fromIndex
	 * @param toIndex
	 * @param capture
	 * @param type
	 * @param ordering
	 *            If we want to assign an ordering value at creation time,
	 *            probably won't be used much for now
	 * @return move The finished move integer
	 */
	public static int createMove(int pieceMoving, int fromIndex, int toIndex,
			int capture, int type, int ordering) {

		// "or" every value into the move, start with empty then (in order)
		// from, to, piece moving (offset 7), piece captured (offset 7), move
		// type, ordering value
		return 0 | fromIndex | (toIndex << TO_SHIFT)
				| ((pieceMoving + 7) << PIECE_SHIFT)
				| ((capture + 7) << CAPTURE_SHIFT) | (type << TYPE_SHIFT)
				| (ordering << ORDERING_SHIFT);
	}

	/**
	 * Returns a string holding the short notation of the move
	 * 
	 * @return String Short notation
	 */
	public static String notation(int move) {
		// Get the values from the move
		int pieceMoving = pieceMoving(move);
		int fromIndex = fromIndex(move);
		int toIndex = toIndex(move);
		int capture = capture(move);
		int moveType = moveType(move);

		final StringBuilder notation = new StringBuilder();

		// Add the piece notation
		switch (pieceMoving) {
		case W_KING: {
			if (moveType == SHORT_CASTLE)
				return "0-0";
			if (moveType == LONG_CASTLE)
				return "0-0-0";
			notation.append("K");
			break;
		}
		case B_KING: {
			if (moveType == SHORT_CASTLE)
				return "0-0";
			if (moveType == LONG_CASTLE)
				return "0-0-0";
			notation.append("K");
			break;
		}
		case W_QUEEN:
		case B_QUEEN:
			notation.append("Q");
			break;
		case W_ROOK:
		case B_ROOK:
			notation.append("R");
			break;
		case W_BISHOP:
		case B_BISHOP:
			notation.append("B");
			break;
		case W_KNIGHT:
		case B_KNIGHT:
			notation.append("N");
			break;
		}

		// The move is a capture
		if (capture != 0) {
			// If the moving piece is a pawn we need to add the file it's moving
			// from
			if ((pieceMoving == W_PAWN) || (pieceMoving == B_PAWN)) {
				// Find the file
				notation.append("abcdefgh".charAt(fromIndex % 16));
			}
			notation.append("x");
		}

		// Find the file
		notation.append("abcdefgh".charAt(toIndex % 16));

		// Add the rank
		notation.append((toIndex - (toIndex % 16)) / 16 + 1);

		if (moveType == EN_PASSANT)
			notation.append(" e.p.");

		// Add promotion
		switch (moveType) {
		case PROMOTION_QUEEN:
			notation.append("=Q");
			break;
		case PROMOTION_ROOK:
			notation.append("=R");
			break;
		case PROMOTION_BISHOP:
			notation.append("=B");
			break;
		case PROMOTION_KNIGHT:
			notation.append("=N");
			break;
		}

		return notation.toString();
	}

	/**
	 * Returns the move on the form 'e2e4', that is only the from and to square
	 * 
	 * @return String The input notation
	 */
	public static String inputNotation(int move) {

		// Gather the information from the move int
		int fromIndex = fromIndex(move);
		int toIndex = toIndex(move);
		int moveType = moveType(move);

		StringBuilder inputNotation = new StringBuilder();
		positionToString(fromIndex, inputNotation);
		positionToString(toIndex, inputNotation);

		// Check for promotion
		switch (moveType) {
		case PROMOTION_QUEEN:
			inputNotation.append("q");
			break;
		case PROMOTION_ROOK:
			inputNotation.append("r");
			break;
		case PROMOTION_BISHOP:
			inputNotation.append("b");
			break;
		case PROMOTION_KNIGHT:
			inputNotation.append("n");
			break;
		}

		return inputNotation.toString();
	}

	private static void positionToString(final int position, final StringBuilder buffer) {
		buffer.append("abcdefgh".charAt(position % 16));
		buffer.append(((position - (position % 16)) / 16) + 1);
	}
}
