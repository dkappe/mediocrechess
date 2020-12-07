package mediocrechess.mediocre;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mediocrechess.mediocre.board.Board;
import mediocrechess.mediocre.board.Move;
import mediocrechess.mediocre.engine.Engine;
import mediocrechess.mediocre.engine.Engine.LineEval;
import mediocrechess.mediocre.main.Settings;

import org.junit.Before;
import org.junit.Test;

public class SuiteTest {
	
	private static final String testSet = "/testsetfull";
	private List<Position> positions;
	private Integer TIME = 2000;
	
	@Before
	public void test() throws Exception{
		
		URL url = this.getClass().getResource(testSet);
		File testSetFile = new File(url.getFile());
		FileInputStream fstream = new FileInputStream(testSetFile);
		// Get the object of DataInputStream
		DataInputStream in = new DataInputStream(fstream);
		BufferedReader br = new BufferedReader(new InputStreamReader(in));
		String strLine;

		positions = new ArrayList<Position>();
		while ((strLine = br.readLine()) != null)   {
			positions.add(new Position(strLine));
		}
		in.close();
	}
	
	@Test
	public void runSet() throws Exception {
		Board board = new Board();
				
		
		int totalScore = 0;
		int correct = 0;
		int wrong = 0;
		for(Position p : positions) {
			if(!(p.getName().startsWith("YATS")/* || p.getName().startsWith("Uski")*/)) continue;
			Settings.getInstance().getTranspositionTable().clear();
			Settings.getInstance().getPawnHash().clear();
			Settings.getInstance().getEvalHash().clear();
			board.inputFen(p.getFen());

			LineEval result = Engine.search(board, 0, 0, 0, TIME, false);
			int score = p.getScore(result.line[0]);
			
			totalScore += score;
			
			if(score > 0) {
				correct++;
			} else {
				wrong++;
			}
			
			System.out.println(p.getName() + " Result: " + score);			
		}
		
		System.out.println("Total Score: " + totalScore + " Correct: " + correct + "/"+(correct+wrong));
	}
	
	private class Position {
		private String name;
		private String fen;
		private Map<Integer,Integer> answers;
		
		public Position(String positionLine) {
			this.name = positionLine.split("\\|")[0];
			this.fen = positionLine.split("\\|")[1];
			answers = new HashMap<Integer, Integer>();
			addAnswers(positionLine.split("\\|")[2]);
		}
		
		private void addAnswers(String answersString) {
			Board board = new Board();
			board.inputFen(fen);
			String[] moveBreak = answersString.split(" ");
			List<String> moveList = new ArrayList<String>();
			for(int i = 0; i < moveBreak.length; i++) {
				moveList.add(moveBreak[i].split("\\=")[0]);
			}
			List<String> moveScore = new ArrayList<String>();
			for(int i = 0; i < moveBreak.length; i++) {
				moveScore.add(moveBreak[i].split("\\=")[1]);
			}
			
			Move[] realMoves = new Move[100];
			for(int i = 0; i < 100; i++) {
				realMoves[i] = new Move();
			}
			int nMoves = board.gen_allLegalMoves(realMoves, 0);
			
			for(int kk = 0; kk< moveList.size(); kk++) {
				String s = moveList.get(kk);
				Move move = null;
				
				for(int i = 0; i < nMoves; i++) {
					if(Move.inputNotation(realMoves[i].move).equals(s)) {
						move = realMoves[i];
						break;
					} else if(Move.notation(realMoves[i].move).equals(s)) {
						move = realMoves[i];
						break;
					} else if(Move.inputNotation(realMoves[i].move).equals(s.toLowerCase())) {
						move = realMoves[i];
						break;
					}
				}
				if(move == null) System.out.println("Couldn't find: " + s + " in position " + name);
				else {
					answers.put(move.move,Integer.parseInt(moveScore.get(kk)));
				}
			}
		}
		
		public String getName() { return name; }
		public String getFen() { return fen; }
		
		public Integer getScore(Integer move) {
			return answers.get(move) == null ? 0 : answers.get(move);
		}
	}
}
