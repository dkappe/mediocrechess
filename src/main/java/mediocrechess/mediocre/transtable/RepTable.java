package mediocrechess.mediocre.transtable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mediocrechess.mediocre.def.Definitions;

/**
 * class Transposition table
 * 
 * This class holds a hashtable and entrys
 * 
 * @author Jonatan Pettersson (mediocrechess@gmail.com)
 */
public class RepTable implements Definitions {
	public long[] hashtable; // Used for repetition detection table
	public int HASHSIZE; // Size of the hash table
	public static final int SLOTS = 1; // Number of slots one entry takes

	private static Logger logger = LoggerFactory.getLogger(RepTable.class);

	// Repetition table
	public RepTable(int sizeInMb) {
		this.HASHSIZE = sizeInMb * 1024 * 1024 * 8 / 64 / SLOTS;
		hashtable = new long[HASHSIZE * SLOTS];
	}

	/**
	 * Clears the table
	 */
	public void clear() {
		hashtable = new long[HASHSIZE * SLOTS];
	} // END clear
	
	/**
	 * Records a position the repetition table, will search through the table
	 * until it finds an empty slot
	 * 
	 * @param zobrist
	 *            The key to match
	 */
	public void recordRep(long zobrist) {
		// TODO: Make this smoother with a better looking for empty places
		int hashkey = (int) (zobrist % HASHSIZE);

		if (hashtable[hashkey] == 0 || hashtable[hashkey] == zobrist) {
			hashtable[hashkey] = zobrist;
			return;
		}

		for (int i = 1; i < HASHSIZE; i++) {
			if (hashtable[(hashkey + i) % HASHSIZE] == 0) {
				hashtable[(hashkey + i) % HASHSIZE] = zobrist;
				return;
			}
		}
		
		logger.error("Error: Repetition table is full");
	} // END recordRep

	/**
	 * Removes a repetition entry
	 * 
	 * @param zobrist
	 *            The key to match
	 */
	public void removeRep(long zobrist) {
		// TODO: Make this smoother with a better looking for empty places

		int hashkey = (int) (zobrist % HASHSIZE);

		if (hashtable[hashkey] == zobrist) {
			hashtable[hashkey] = 0;
			return;
		}

		for (int i = 1; i < HASHSIZE; i++) {
			if (hashtable[(hashkey + i) % HASHSIZE] == zobrist) {
				hashtable[(hashkey + i) % HASHSIZE] = 0;
				return;
			}
		}
		logger.error("Error: Repetition to be removed not found");

	} // END recordRep

	/**
	 * Checks if the zobrist key exists in the repetition table will search
	 * through the whole array to see if any spot matches
	 * 
	 * TODO: Make this smoother
	 * 
	 * @param zobrist
	 * @return
	 */
	public boolean repExists(long zobrist) {
		int hashkey = (int) (zobrist % HASHSIZE);

		if (hashtable[hashkey] == 0)
			return false;
		else if (hashtable[hashkey] == zobrist)
			return true;

		for (int i = 1; i < HASHSIZE; i++) {
			if (hashtable[(hashkey + i) % HASHSIZE] == 0) {
				return false;
			} else if (hashtable[(hashkey + i) % HASHSIZE] == zobrist) {
				return true;
			}
		}
		return false;
	} // END repExists
}
