package mediocrechess.mediocre;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import mediocrechess.mediocre.board.Board;
import mediocrechess.mediocre.engine.Engine;
import mediocrechess.mediocre.main.Settings;
import mediocrechess.mediocre.perft.Perft;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpeedTest {
	private static Logger logger = LoggerFactory.getLogger(SpeedTest.class);

	private final static int DEPTH = 8;
	private final static String TEST_SET_MIDDLE = "/speedtestmiddle";
	private final static String TEST_SET_ENDING = "/speedtestending";
	private List<String> positionsMiddle;
	private List<String> positionsEnding;
	
	@Before
	public void setUp() throws Exception {
		loadMiddle();
		loadEnding();
	}
	
	private void loadMiddle() throws Exception {
		positionsMiddle = new ArrayList<String>();
		URL url = this.getClass().getResource(TEST_SET_MIDDLE);
		File testSetFile = new File(url.getFile());
		FileInputStream fstream = new FileInputStream(testSetFile);
		// Get the object of DataInputStream
		DataInputStream in = new DataInputStream(fstream);
		BufferedReader br = new BufferedReader(new InputStreamReader(in));
		String strLine;
		//Read File Line By Line
		while ((strLine = br.readLine()) != null)   {
			positionsMiddle.add(strLine);
		}
		in.close();
	}
	
	private void loadEnding() throws Exception{
		positionsEnding = new ArrayList<String>();
		URL url = this.getClass().getResource(TEST_SET_ENDING);
		File testSetFile = new File(url.getFile());
		FileInputStream fstream = new FileInputStream(testSetFile);
		// Get the object of DataInputStream
		DataInputStream in = new DataInputStream(fstream);
		BufferedReader br = new BufferedReader(new InputStreamReader(in));
		String strLine;
		//Read File Line By Line
		while ((strLine = br.readLine()) != null)   {
			positionsEnding.add(strLine);
		}
		in.close();
	}

	@Test
	public void speedTestMiddle() throws IOException {
		Board board = new Board();

		long totalTime = System.currentTimeMillis();
		int count = 0;
		for(String pos : positionsMiddle) {
			Settings.getInstance().getTranspositionTable().clear();
			Settings.getInstance().getPawnHash().clear();
			Settings.getInstance().getEvalHash().clear();
						
			board.inputFen(pos);
			long thisTime = System.currentTimeMillis();
			Engine.search(board, DEPTH, 0, 0, 0, false);
			logger.debug("Position " + (++count) + "/" + positionsEnding.size() + " Time: " + Perft.convertMillis((System.currentTimeMillis() - thisTime)) + " Total time: " + Perft.convertMillis((System.currentTimeMillis() - totalTime)));
		}
		
		
		logger.debug("Time: " + Perft.convertMillis((System.currentTimeMillis() - totalTime)));
	}	
	
	@Test
	public void speedTestEnding() throws IOException {
		Board board = new Board();

		long totalTime = System.currentTimeMillis();
		int count = 0;
		for(String pos : positionsEnding) {
			Settings.getInstance().getTranspositionTable().clear();
			Settings.getInstance().getPawnHash().clear();
			Settings.getInstance().getEvalHash().clear();
						
			board.inputFen(pos);
			long thisTime = System.currentTimeMillis();
			Engine.search(board, DEPTH, 0, 0, 0, false);
			logger.debug("Position " + (++count) + "/" + positionsEnding.size() + " Time: " + Perft.convertMillis((System.currentTimeMillis() - thisTime)) + " Total time: " + Perft.convertMillis((System.currentTimeMillis() - totalTime)));
		}
		
		
		logger.debug("Time: " + Perft.convertMillis((System.currentTimeMillis() - totalTime)));
	}
}
