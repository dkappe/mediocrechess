package mediocrechess.mediocre.transtable;

import mediocrechess.mediocre.def.Definitions;

/**
 * class Transposition table
 * 
 * This class holds a hashtable and entrys
 * 
 * @author Jonatan Pettersson (mediocrechess@gmail.com)
 */
public class EvalTable implements Definitions {
	public int[] hashtable; // Used for transposition table
	public int HASHSIZE; // The number of slots either table will have

	public static final int SLOTS = 2; // 3 for one 'table', 6 for two (two
										// tables means one for depth and one
										// for always replace)

	// Ordinary transposition table
	public EvalTable(int sizeInMb) {
		this.HASHSIZE = sizeInMb * 1024 * 1024 * 8 / 32 / SLOTS;
		hashtable = new int[HASHSIZE * SLOTS];
	}


	/**
	 * Clears the transposition table
	 */
	public void clear() {
		hashtable = new int[HASHSIZE * SLOTS];
	} // END clear

	public void recordEval(long zobrist, int eval) {
		int hashkey = (int) (zobrist % HASHSIZE) * SLOTS;

		hashtable[hashkey] = (eval + 0x1FFFF);
		hashtable[hashkey + 1] = (int) (zobrist >> 32);
	} // END recordEval

	public int probeEval(long zobrist) {
		int hashkey = (int) (zobrist % HASHSIZE) * SLOTS;
		if (hashtable[hashkey + 1] == ((int) (zobrist >> 32))) {
			return (hashtable[hashkey] - 0x1FFFF);
		}

		return EVALNOTFOUND;
	} // END probeEval
}
