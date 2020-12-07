package mediocrechess.mediocre.main;


import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;

import mediocrechess.mediocre.def.Definitions;
import mediocrechess.mediocre.transtable.*;

public class Settings implements Definitions {
	private static final String OWN_BOOK_PATH = "performance.bin";
	public static final boolean DEFAULT_USE_OWN_BOOK = true;
	public static final int DEFAULT_HASH_SIZE = 16;
	public static final int DEFAULT_EVAL_HASH_SIZE = 8;
	public static final int DEFAULT_PAWN_HASH_SIZE = 8;
	public static final boolean DEFAULT_PONDER = false;
	
	/* Transposition tables */
	private TranspositionTable transpositionTable;
	private RepTable repTable;
	private EvalTable evalHash;
	private PawnTable pawnHash;
	private int tt_size;
	private static final int REP_SIZE = 1;
	private int eval_size;
	private int pawn_size;
	private boolean ponder;

	/* Book */
	private Book book;
	private boolean useOwnBook;
	
	
	private static Logger logger = (Logger)LoggerFactory.getLogger(Mediocre.class);
	
	
	private static Settings instance = null;
	
	/**
	 * Singleton (with private constructor and the getInstance method,
	 * only one instance of the class can ever be created)
	 */
	 private Settings() {
		 logger.debug("Initializing settings");
		 
		setTranspositionTableSize(DEFAULT_HASH_SIZE);
		setEvalTableSize(DEFAULT_EVAL_HASH_SIZE);
		setPawnTableSize(DEFAULT_PAWN_HASH_SIZE);
		setUseOwnBook(DEFAULT_USE_OWN_BOOK);
		repTable = new RepTable(REP_SIZE);
	 }
	 
	 public void setUseOwnBook(boolean setOwnBook) {
		 if(setOwnBook) {
			 useOwnBook = true;
			 book = Book.getInstance(OWN_BOOK_PATH);
			 if(book == null) {
				 logger.warn("Book " + OWN_BOOK_PATH + " not found, turning it off");
				 useOwnBook = false; 
			 }
		 } else {
			 useOwnBook = false;
			 book = null;
		 }
	 }
	 
	 public void setPonder(boolean doPonder) {
		 ponder = doPonder;
	 }
	 
	 public boolean getPonder() {
		 return ponder;
	 }

	 public void setTranspositionTableSize(int size) {
		 tt_size = size;
		 transpositionTable = new TranspositionTable(tt_size);
	 }
	 
	 public void setEvalTableSize(int size) {
		 eval_size = size;
		 evalHash = new EvalTable(eval_size);
	 }
	 
	 public void setPawnTableSize(int size) {
		 pawn_size = size;
		 pawnHash = new PawnTable(pawn_size);
	 }
	
	/**
	 * Creates the singleton of the Book and returns it
	 * 
	 * @return The instance of the settings
	 */
	public static Settings getInstance() {
		if(instance == null) {
			instance = new Settings();
		}
		return instance;
	}
	
	public TranspositionTable getTranspositionTable() {
		return transpositionTable;
	}

	public RepTable getRepTable() {
		return repTable;
	}

	public EvalTable getEvalHash() {
		return evalHash;
	}

	public PawnTable getPawnHash() {
		return pawnHash;
	}

	public int getTt_size() {
		return tt_size;
	}

	public static int getREP_SIZE() {
		return REP_SIZE;
	}

	public int getEval_size() {
		return eval_size;
	}

	public int getPawn_size() {
		return pawn_size;
	}

	public Book getBook() {
		return book;
	}

	public boolean isUseOwnBook() {
		return useOwnBook;
	}
}
