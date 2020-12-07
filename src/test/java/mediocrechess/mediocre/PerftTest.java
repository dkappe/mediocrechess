package mediocrechess.mediocre;

import static org.junit.Assert.*;

import mediocrechess.mediocre.board.Board;
import mediocrechess.mediocre.perft.Perft;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PerftTest {
	private static Logger logger = LoggerFactory.getLogger(PerftTest.class);
	
	private final static int maxPly = 40060325;
	private final static int maxDepth = 20;
	
	@Before
	public void setUp() throws Exception {

	}

	@Test
	public void testPerft1() {
		String name = "Pos 1";
		String fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
		long[] answers = {-1L, 20L, 400L, 8902L, 197281L, 4865609L, 119060324L, 3195901860L, 84998978956L, 2439530234167L, 69352859712417L};
		PerftTestPos position = new PerftTestPos(name, fen, answers); 

		boolean valid = validatePosition(position);
		
		assertTrue(valid);
	}
	
	@Test
	public void testPerft2() {
		String name = "Pos 2";
		String fen = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1";
		long[] answers = {-1L, 48L, 2039L, 97862L, 4085603L, 193690690L, 8031647685L};
		PerftTestPos position = new PerftTestPos(name, fen, answers); 

		boolean valid = validatePosition(position);
		
		assertTrue(valid);
	}
	
	@Test
	public void testPerft3() {
		String name = "Pos 3";
		String fen = "8/3K4/2p5/p2b2r1/5k2/8/8/1q6 b - - 1 67";
		long[] answers = {-1L, 50L, 279L};
		PerftTestPos position = new PerftTestPos(name, fen, answers); 

		boolean valid = validatePosition(position);
		
		assertTrue(valid);
	}
	
	@Test
	public void testPerft4() {
		String name = "Pos 4";
		String fen = "8/7p/p5pb/4k3/P1pPn3/8/P5PP/1rB2RK1 b - d3 0 28";
		long[] answers = {-1L, -1L, -1L, -1L, -1L, -1L, 38633283L};
		PerftTestPos position = new PerftTestPos(name, fen, answers); 

		boolean valid = validatePosition(position);
		
		assertTrue(valid);
	}
	
	@Test
	public void testPerft5() {
		String name = "Pos 5";
		String fen = "rnbqkb1r/ppppp1pp/7n/4Pp2/8/8/PPPP1PPP/RNBQKBNR w KQkq f6 0 3";
		long[] answers = {-1L, -1L, -1L, -1L, -1L, 11139762L};
		PerftTestPos position = new PerftTestPos(name, fen, answers); 

		boolean valid = validatePosition(position);
		
		assertTrue(valid);
	}
	
	@Test
	public void testPerft6() {
		String name = "Pos 6";
		String fen = "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1";
		long[] answers = {-1L, -1L, -1L, -1L, -1L, -1L, 11030083L, 178633661L};
		PerftTestPos position = new PerftTestPos(name, fen, answers); 

		boolean valid = validatePosition(position);
		
		assertTrue(valid);
	}
	
	private boolean validatePosition(PerftTestPos perftTestPos) {
		Board board = new Board();
		/* Check all the positions to the given depth */
		long startTot = System.currentTimeMillis();
		logger.debug(perftTestPos.getName());
		boolean allDepthsCorrect = true;
		for(int i = 1; i < maxDepth && i < perftTestPos.answerLength(); i++){
			if(perftTestPos.getAnswerAtDepth(i) != -1L && perftTestPos.getAnswerAtDepth(i) < maxPly) {
				board.inputFen(perftTestPos.getFen());
				long start = System.currentTimeMillis();
				long answer = Perft.perft(board, i, false);
				StringBuilder sb = new StringBuilder();
				sb.append("  Depth: " + i + " Answer: " + answer);
				if(answer == perftTestPos.getAnswerAtDepth(i)) {
					sb.append(" (Correct)");
				} else {
					sb.append(" (Incorrect)");
					allDepthsCorrect = false;
				}
				sb.append(" Time: " + Perft.convertMillis(System.currentTimeMillis()-start));
				logger.debug(sb.toString());
			}
		}

		logger.debug("Total time: " + Perft.convertMillis(System.currentTimeMillis()-startTot));
		
		return allDepthsCorrect;
	}
	
	private class PerftTestPos {
		private String name;
		private String fen;
		private long[] answers;

		public PerftTestPos(String name, String fen, long[] answers) {
			this.name = name;
			this.fen = fen;
			this.answers = answers;
		}
		
		public String getName() {
			return name;
		}

		public long getAnswerAtDepth(int depth) {
			if(depth > answers.length) {
				return -1;
			}

			return answers[depth];
		}

		public String getFen() {
			return fen;
		}
		
		public int answerLength() {
			return answers.length;
		}
	}	

}
