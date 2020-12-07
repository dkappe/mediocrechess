package mediocrechess.mediocre.perft;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mediocrechess.mediocre.board.*;

/**
 * class Perft
 * 
 * This class runs has utility methods for perft tests
 * 
 * @author Jonatan Pettersson (mediocrechess@gmail.com)
 */
public class Perft {
	private static Logger logger = LoggerFactory.getLogger(Perft.class);
	
	/**
	 * Start the perft search
	 * 
	 * @param board
	 *            The board to search
	 * @param depth
	 *            The depth to search to
	 * @param divide
	 *            Should we divide the first moves or just return the total
	 *            value
	 * @return number of nodes
	 */
	public static long perft(Board board, int depth, boolean divide) {
		long nNodes;
		long zobrist = board.zobristKey;

		if (divide) {
			nNodes = divide(board, depth);
		} else {
			nNodes = miniMax(board, depth);
		}

		if (zobrist != board.zobristKey)
			logger.error("Error in zobrist update!");

		return nNodes;

	}

	/**
	 * Keeps track of every starting move and its number of child moves, and
	 * then prints it on the screen.
	 * 
	 * @param board
	 *            The position to search
	 * @param depth
	 *            The depth to search to
	 */
	private static long divide(Board board, int depth) {
		Move[] moves = new Move[128];

		for(int i = 0; i < 128; i++) moves[i] = new Move();
		int totalMoves = board.gen_allLegalMoves(moves, 0);
		Long[] children = new Long[128];

		for (int i = 0; i < totalMoves; i++) {

			board.makeMove(moves[i].move);
			children[i] = new Long(miniMax(board, depth - 1));
			board.unmakeMove(moves[i].move);
		}

		long nodes = 0;
		for (int i = 0; i < totalMoves; i++) {
			System.out.print(Move.inputNotation(moves[i].move) + " ");
			System.out.println(((Long) children[i]).longValue());
			nodes += ((Long) children[i]).longValue();
		}

		
		System.out.println("Moves: " + totalMoves);
		return nodes;
	}

	/**
	 * Generates every move from the position on board and returns the total
	 * number of moves found to the depth
	 * 
	 * @param board
	 *            The board used
	 * @param depth
	 *            The depth currently at
	 * @return int The number of moves found
	 */
	private static long miniMax(Board board, int depth) {
		long nodes = 0;

		if (depth == 0)
			return 1;

		Move[] moves = new Move[128];
		for(int i = 0; i < 128; i++) moves[i] = new Move();
		int totalMoves = board.gen_allLegalMoves(moves, 0);

		for (int i = 0; i < totalMoves; i++) {
			board.makeMove(moves[i].move);
			nodes += miniMax(board, depth - 1);
			board.unmakeMove(moves[i].move);
		}

		return nodes;
	}

	/**
	 * Takes number and converts it to minutes, seconds and fraction of a second
	 * also includes leading zeros
	 * 
	 * @param millis
	 *            the Milliseconds to convert
	 * @return String the conversion
	 */
	public static String convertMillis(long millis) {
		long minutes = millis / 60000;
		long seconds = (millis % 60000) / 1000;
		long fracSec = (millis % 60000) % 1000;

		String timeString = "";

		// Add minutes to the string, if no minutes this part will not add to
		// the string
		if (minutes < 10 && minutes != 0)
			timeString += "0" + Long.toString(minutes) + ":";
		else if (minutes >= 10)
			timeString += Long.toString(minutes) + ":";

		// Add seconds to the string
		if (seconds == 0)
			timeString += "0";
		else if (minutes != 0 && seconds < 10)
			timeString += "0" + Long.toString(seconds);
		else if (seconds < 10)
			timeString += Long.toString(seconds);
		else
			timeString += Long.toString(seconds);

		timeString += ".";

		// Add fractions of a second to the string
		if (fracSec == 0)
			timeString += "000";
		else if (fracSec < 10)
			timeString += "00" + Long.toString(fracSec);
		else if (fracSec < 100)
			timeString += "0" + Long.toString(fracSec);
		else
			timeString += Long.toString(fracSec);

		return timeString;
	}
}
