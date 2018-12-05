import java.util.PriorityQueue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 * @author Alex Guo CS201 Assignment
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){
		
		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);
		
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root,out);
		
		in.reset();
		writeCompressedBits(codings,in,out);
		out.close();
		
//		while (true){
//			int val = in.readBits(BITS_PER_WORD);
//			if (val == -1) break;
//			out.writeBits(BITS_PER_WORD, val);
//		}
	}
	
	
	private void writeCompressedBits(String[] encodings, BitInputStream in, BitOutputStream out) {
		
		while (true){
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) {
				String code = encodings[PSEUDO_EOF];
				out.writeBits(code.length(), Integer.parseInt(code,2));
				break;
			}
			String code = encodings[Integer.parseInt(Integer.toString(val),2)];
			out.writeBits(code.length(), Integer.parseInt(code,2));
		}
		
	}
	
	/**
	 * Writing the tree
	 * @param root root of the Huffman tree
	 * @param out output stream
	 */
	private void writeHeader(HuffNode root, BitOutputStream out) {
		if(root.myLeft != null || root.myRight !=null) {
			out.writeBits(1,0);
			writeHeader(root.myLeft,out);
			writeHeader(root.myRight,out);
		}
		else {
			out.writeBits(1,1);
			out.writeBits(BITS_PER_WORD + 1, root.myValue);
		}
	}
	
	/**
	 * Making codings from Tree
	 * @param root root of the Huffman tree
	 * @return A string[] that contains encodings for each character
	 */
	private String[] makeCodingsFromTree(HuffNode root) {
		String[] encodings = new String[ALPH_SIZE + 1];
		codingHelper(root,"",encodings);
		return encodings;
	}
	
	private void codingHelper(HuffNode root, String path, String[] encodings) {
		if(root.myLeft == null && root.myRight == null) {
			encodings[root.myValue] = path;
			return;
		}
		
		codingHelper(root.myLeft,path+"0",encodings);
		codingHelper(root.myRight,path+"1",encodings);
		
	}
	
	
	/**
	 * Making Huffman Trie/Tree
	 * @param counts frequency array
	 * @return root of the Huffman Tree
	 */
	private HuffNode makeTreeFromCounts(int[] counts) {
		
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		
		for(int i = 0;i < counts.length;i++) {
			if(counts[i] <= 0) continue;
			pq.add(new HuffNode(i,counts[i],null,null));
		}
		
		while (pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(0,left.myWeight + right.myWeight, left, right);//0??
			pq.add(t);
		}
		HuffNode root = pq.remove();
		return root;
	}
	
	/**
	 * Counting the frequency of the characters from the input
	 * @param in
	 * @return a frequency array for all characters including 1 PSEUDO_EOF
	 */
	private int[] readForCounts(BitInputStream in) {
		int[] freq = new int[ALPH_SIZE + 1];
		while (true){
			int val = in.readBits(BITS_PER_WORD);
			freq[Integer.parseInt(Integer.toString(val),2)]++;
			if (val == -1) {
				freq[PSEUDO_EOF] = 1;
				break;
			}
		}
		return freq;
	}

	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){
		
		int bits = in.readBits(BITS_PER_INT);
		if(bits != HUFF_TREE) {
			throw new HuffException("illegal header starts with " + bits);
		}
		
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root,in,out);
		out.close();

	}
	/**
	 * Reading the Huffman tree
	 * @param in input
	 * @return the root node of the completed Huffman tree
	 */
	private HuffNode readTreeHeader(BitInputStream in) {
		int bit = in.readBits(1);
		if(bit == -1) throw new HuffException("illegal bit value " + bit);
		if(bit == 0) {
			HuffNode left = readTreeHeader(in);
			HuffNode right = readTreeHeader(in);
			return new HuffNode(0,0,left,right);
		}
		else {
			int value = in.readBits(BITS_PER_WORD + 1);
			return new HuffNode (value,0,null,null);
		}
	}
	
	/**
	 * Read compressed bits using a Huffman tree
	 * @param root root node of the given Huffman tree
	 * @param in input
	 * @param out output
	 */
	private void readCompressedBits(HuffNode root,BitInputStream in,BitOutputStream out) {
		HuffNode current = root;
		while(true) {
			int bits = in.readBits(1);
			if(bits == -1) throw new HuffException("bad inpout, no PSEUDO_EOF");
			else {
				if(bits == 0) current = current.myLeft;
				else current = current.myRight;
				
				if(current.myLeft == null && current.myRight == null) {
					if(current.myValue == PSEUDO_EOF) break;
					else {
						out.writeBits(BITS_PER_WORD, current.myValue);
						current = root;
					}
				}
			}
		}
	}
}