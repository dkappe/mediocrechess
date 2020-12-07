package mediocrechess.mediocre;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import mediocrechess.mediocre.board.Board;
import mediocrechess.mediocre.board.Evaluation;
import mediocrechess.mediocre.perft.Perft;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EvaluationSpeedTest {
	private static Logger logger = LoggerFactory.getLogger(EvaluationMirrorTest.class);

	private final static String testSet = "/evaltestpositions";
	private List<String> positions;
	private List<Board> setupBoards;
	
	@Before
	public void setUp() throws Exception {
		positions = new ArrayList<String>();
		setupBoards = new ArrayList<Board>();
		
		URL url = this.getClass().getResource(testSet);
		File testSetFile = new File(url.getFile());
		FileInputStream fstream = new FileInputStream(testSetFile);
		// Get the object of DataInputStream
		DataInputStream in = new DataInputStream(fstream);
		BufferedReader br = new BufferedReader(new InputStreamReader(in));
		String strLine;
		//Read File Line By Line
		while ((strLine = br.readLine()) != null)   {
			positions.add(strLine);
			Board board = new Board();
			board.inputFen(strLine);
			setupBoards.add(board);
		}
		in.close();
	}
	
	@Test
	public void testEvalSpeed() {
		long start = System.currentTimeMillis();
		for(int i = 0; i < 50000; i++) {
		for(Board b : setupBoards) {
			Evaluation.evaluate(b, false);
		}
		}
		logger.debug(Perft.convertMillis(System.currentTimeMillis() - start));
		
		assertTrue(System.currentTimeMillis() - start < 9000);
	}
}
