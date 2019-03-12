// Itay Zemah 312277007

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.ListIterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeSet;

public class IterButton extends CommandButton {
	private boolean clickedOnce;
	private Map<String, String> itemsMap = null;

	public IterButton(AddressBookPane pane, RandomAccessFile r) {
		super(pane, r);
		this.setText("Iter");
		clickedOnce = false;
	}

	public ListIterator<String> listIterator(int indexOfRecord) throws IOException {
		if (indexOfRecord <= 0 || indexOfRecord > raf.length() / RECORD_SIZE) {
			throw new ArrayIndexOutOfBoundsException("no record in this index");
		}
		return new MyIterBtnIterator(this.raf, (indexOfRecord - 1) * 2 * RECORD_SIZE);
	}

	public ListIterator<String> listIterator() {
		return new MyIterBtnIterator(raf, 1);
	}

	public boolean isclicked() {
		return clickedOnce;
	}

	private void setClicked(boolean bool) {
		this.clickedOnce = bool;
	}

	@Override
	public void Execute() {
		try {
			ListIterator<String> lit = listIterator(1);
			if (isclicked() == false) {

				itemsMap = getItemsToLinkedHashMap(lit); // interst the file to Map
				restartFile(lit); // restart the iterator + file
				pushItemsToFile(itemsMap, lit); // rewrite the addresses to file
				setClicked(true);

			} else { // second click
				if(itemsMap.size() * 2 * RECORD_SIZE != raf.length()) {
					itemsMap = getItemsToLinkedHashMap(lit);
				}
				TreeSet<String> treeSet = new TreeSet<>(new CompareStreets());
				moveFromMapToTree(treeSet, itemsMap, lit);
				restartFile(lit); // restart the iterator + file
				rewriteFileFromTree(treeSet, lit);

			}
		} catch (IOException ex) {
			ex.getStackTrace();
		}

	}

	private void restartFile(ListIterator<String> lit) {
		while (lit.hasNext()) {
			lit.next();
			lit.remove();
		}
		while (lit.hasPrevious()) {
			lit.previous();
			lit.remove();
		}

	}

	private void rewriteFileFromTree(TreeSet<String> set, ListIterator<String> lit) {
		((MyIterBtnIterator) lit).setCurrent(0);
		for (String string : set) {
			lit.add(string);
		}

	}

	private void moveFromMapToTree(TreeSet<String> set, Map<String, String> itemsMap, ListIterator<String> lit) {
		set.addAll(itemsMap.values());
	}

	private void pushItemsToFile(Map<String, String> itemsMap, ListIterator<String> iterator) {

		for (String s : itemsMap.values()) {
			iterator.add(s);
		}

	}

	public Map<String, String> getItemsToLinkedHashMap(ListIterator<String> iterator) throws IOException {
		LinkedHashMap<String, String> myHashMap = new LinkedHashMap<>();

		while (iterator.hasNext()) {
			String value = iterator.next();
			String key = value.substring(0, RECORD_SIZE - ZIP_SIZE);
			myHashMap.put(key, value);
		}
		return myHashMap;

	}

	class CompareStreets implements Comparator<String> {

		@Override
		public int compare(String o1, String o2) {
			String s1 = o1.substring(NAME_SIZE, NAME_SIZE + STREET_SIZE);
			String s2 = o2.substring(NAME_SIZE, NAME_SIZE + STREET_SIZE);
			return s1.compareToIgnoreCase(s2) == 0 ? -1 : s1.compareToIgnoreCase(s2);
		}

	}

	private class MyIterBtnIterator implements ListIterator<String> {
		private long current = 0;
		private RandomAccessFile raf;
		private long lastPos = -1;

		public MyIterBtnIterator(RandomAccessFile raf, long current) {
			setRAF(raf);
			setCurrent(current);
		}

		private void setRAF(RandomAccessFile raf) {
			this.raf = raf;
		}

		public void setCurrent(long current) {
			this.current = current;
		}

		public long getCurrent() {
			return this.current;
		}

		@Override
		public void add(String e) {
			try {
				ArrayList<String> stringAL = new ArrayList<>();
				stringAL.add(e);
				moveItemsToAListAndWrite(stringAL, getCurrent());
				setCurrent(getCurrent() + RECORD_SIZE * 2);
				setLastpos(-1);
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}

		private void moveItemsToAListAndWrite(ArrayList<String> stringAL, long fromWhere) throws IOException {
			raf.seek(fromWhere);
			while (hasNext()) {
				stringAL.add(next());
				remove();
			}
			setCurrent(fromWhere);
			writeArrayListToFile(fromWhere, stringAL);
		}

		private void writeArrayListToFile(long fromWhere, ArrayList<String> List) throws IOException {
			raf.seek(fromWhere);
			for (String string : List) {
				FixedLengthStringIO.writeFixedLengthString(string, RECORD_SIZE, raf);
			}
		}

		@Override
		public void remove() {
			try {
				if (getLastPos() == -1) {
					throw new IllegalStateException();
				}
				long currentTosave = getCurrent();
				setCurrent(getLastPos() + 2 * RECORD_SIZE); // if previous  made  jump to the next record;
				raf.seek(getCurrent()); // bring the file pointer
				StringBuffer sb = new StringBuffer();
				while (hasNext()) { //read all record over the record to delete and save
					sb.append(FixedLengthStringIO.readFixedLengthString(RECORD_SIZE, raf));
					setCurrent(raf.getFilePointer());
				}
				 //paste on the record to remove
				raf.seek(getLastPos());
				setCurrent(currentTosave);
				if (sb.length() > 0) {
					FixedLengthStringIO.writeFixedLengthString(sb.toString(), sb.length(), raf);
				}
				raf.setLength(raf.length() - 2 * RECORD_SIZE);
				setLastpos(-1);

			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		public long max(long lastPos, long current) {

			return lastPos > current ? lastPos : current;
		}

		@Override
		public boolean hasNext() {
			try {
				return (getCurrent() < this.raf.length());
			} catch (IOException e) {
				return false;
			}

		}

		@Override
		public boolean hasPrevious() {
			return getCurrent() >= 2 * RECORD_SIZE;
		}

		@Override
		public String next() {
			try {
				if (hasNext()) {
					setLastpos(getCurrent());
					raf.seek(getCurrent());
					String record = FixedLengthStringIO.readFixedLengthString(RECORD_SIZE, raf);
					setCurrent(raf.getFilePointer());
					return record;
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			throw new NoSuchElementException();
		}

		@Override
		public int nextIndex() { // starting from 0
			return (int) (hasNext() ? ((int) getCurrent() / (2 * RECORD_SIZE)) : -1);
		}

		@Override
		public String previous() {
			if (hasPrevious()) {
				try {
					setCurrent(getCurrent() - 2 * RECORD_SIZE); // take RAF pointer 1 record back
					setLastpos(getCurrent()); // last = current
					raf.seek(getCurrent());
					return FixedLengthStringIO.readFixedLengthString(RECORD_SIZE, raf);
				} catch (IOException e) {
					e.printStackTrace();
				}
			} else
				throw new NoSuchElementException();
			return null;

		}

		@Override
		public int previousIndex() {

			return (hasNext() ? (int) getCurrent() / (2 * RECORD_SIZE) - 1 : -1);
		}

		@Override
		public void set(String str) {
			try {
				if (getLastPos() == -1) {
					throw new IllegalStateException();
				}
				raf.seek(max(lastPos, current) - 2 * RECORD_SIZE); // to know if previous() or next() made
				FixedLengthStringIO.writeFixedLengthString(str, RECORD_SIZE, raf);
			} catch (IOException e) {
				e.printStackTrace();

			}

		}

		public long getLastPos() {
			return lastPos;
		}

		public void setLastpos(long lastpos) {
			this.lastPos = lastpos;
		}
	}

}
