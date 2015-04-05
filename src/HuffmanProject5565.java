import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map.Entry;

/**
 * 
 * @author Shengkun Wei
 *
 */
public class HuffmanProject5565 {
	
	private static final String HUF_EXTENSION = ".huf";
	static final int KEY_BYTE_NUM = 2;
	private static final int BUFFER_NUM = 20;
	private static long startTime;
	
	public static void main(String[] args) {
		
//		String filename = "long.txt";
//		String filename = "test.txt";
//		String filename = "liberty.jpg";
//		String filename = "easy.txt";
		String filename = "project1 610s15.pdf";
		
		startTime = System.currentTimeMillis();
		encodeFile(filename);
		decodeFile(filename + HUF_EXTENSION);
		
		
	}

	private static void encodeFile(String filename){
		HashMap<Integer, Integer> freqMap = readFrequenciesFromFile(filename);
		MinHeap minHeap = new MinHeap(freqMap);
		
		HuffmanTree tree = new HuffmanTree(minHeap);
		HashMap<Integer, String> hCodesMap = tree.getHCodeMap();
		
		final int BYTE_NEEDED_FOR_HCODE = getByteNeededForHCodes(hCodesMap);
		
		System.out.println("~~~~~byte needed for hcode maps " + BYTE_NEEDED_FOR_HCODE);

		BufferedInputStream bis = null;
		BufferedOutputStream bos = null;
		try {
			bos = new BufferedOutputStream(new FileOutputStream(filename + ".huf"));

			//data structure of the part of hcode map is: {byte number of all hcode|1st code's key|1st code's bit number N|1st code's code[need ceil(N/8) bytes]|2nd code's key|...}
			//use the first 2 bytes to save the byte number of hcode
			bos.write(BYTE_NEEDED_FOR_HCODE>>8);
			bos.write(BYTE_NEEDED_FOR_HCODE);
			/* Suppose there are 256 (at most) huffman codes, each of which needs 1 byte for key, 
			 * 1 byte for bit count, and 2 bytes for code. Only 4*256=1024 bytes are needed.
			 * So 2 bytes(=65536) for storing this count is enough.*/
			
			String codeInStr = null;
			
			/*-----------write huffman code maps---------------*/
			//data structure of the part of encoded file is: 
			int byteUsed = 0;
			for(Entry<Integer, String> entry : hCodesMap.entrySet()){
				bos.write(entry.getKey());
				byteUsed++;
				codeInStr = entry.getValue();
				
				int codeInInt = getIntFromBinaryString(codeInStr, false);//code in int
				int codeBitsCount = codeInStr.length();
				final int BYTE_COUNT = (int) Math.ceil(codeBitsCount / 8f);
				
				bos.write(codeBitsCount);
				if(BYTE_COUNT == 1){
					//if only has 1 byte then write it immediately
					bos.write(codeInInt);
					byteUsed++;
				} else {
					for(int i = 0; i < BYTE_COUNT; i++){
						bos.write(codeInInt);
						byteUsed++;
						codeInInt >>= (8 * (i+1));//NOTE that I'm writing the bytes in reverse order, which means for code 1111111100000000 I first write 00000000 then 11111111
						
						/*if(codeInInt == 0){
							//if the prior bytes are 0, then we don't need to write it. Just go to write bit count.
							break;
						}
						System.out.println("2+>>>> " + codeInStr + "  " + (codeInInt));*/
					}
				}
				byteUsed++;
			}
			System.out.println("~~~~byte used for hcode maps " + byteUsed);
			
			/*---------------encode file---------------*/
			bis = new BufferedInputStream(new FileInputStream(filename));
			int b = 0;
			StringBuilder sb = new StringBuilder();
			while((b = bis.read()) != -1){
				sb.append(hCodesMap.get(b));//TODO need to be memory optimized
				
			}
			
			//first write total bit count, which needs 8 bytes. 4 bytes are probably not enough since 32 bits have an upper limit 512M.
			final long bitCount = tree.getBitCount();
			System.out.println("bitCount written: " + bitCount);
			for(int i = 0; i < 8; i++){
				bos.write((int) (bitCount >> (7 - i) * 8));
			}
			
			final int totalBits = sb.length();
			
			int pos = 0;
			String oneByteStr = null;
			while(pos < totalBits){
				oneByteStr = sb.substring(pos, pos + Math.min(totalBits - pos, 8));
				int oneByte = getIntFromBinaryString(oneByteStr, true);
				bos.write(oneByte);
				pos += 8;
			}
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if(bis != null){
				try {
					bis.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if(bos != null){
				try {
					bos.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	private static int getByteNeededForHCodes(HashMap<Integer, String> hCodes){
		int byteCount = 0;
		String code = null;
		for(Entry<Integer, String> e : hCodes.entrySet()){
			byteCount++;//1 byte for key
			byteCount++;//1 byte for bit count
			code = e.getValue();
			int count = (int) Math.ceil(code.length() / 8f);
			byteCount += count;
		}
		
		return byteCount;
	}
	
	private static void decodeFile(String filename){
		HashMap<Integer, String> hCodesMap = new HashMap<>();
		String decodedFilename = filename + "." + getOriginalFileExtension(filename);
		
		long bitCountOfFile = 0;
		
		BufferedInputStream bis = null;
		BufferedOutputStream bos = null;
		
		try {
			bis = new BufferedInputStream(new FileInputStream(filename));
			bos = new BufferedOutputStream(new FileOutputStream(decodedFilename));
			
			final int byteCountForHCodes = (bis.read() << 8) + bis.read();
			System.out.println("=== byte count for h code is " + byteCountForHCodes);
			
			int shortestCodeLen = Integer.MAX_VALUE;
			
			int byteRead = 0;//byte count that has already read
			int b;
			//----------start to read hcode map-------------
			while(byteRead < byteCountForHCodes){
				b = bis.read();
				byteRead++;
				final int KEY = b;
				String codeInStrTemp = null;
				final int CODE_BIT_COUNT = bis.read();
				byteRead++;
				
				int codeInInt = 0;
				final int CODE_BYTE_COUNT = (int)Math.ceil(CODE_BIT_COUNT / 8f);
				for(int i = 0; i < CODE_BYTE_COUNT; i++){
					codeInInt += (bis.read()<<(i*8)); //remember that I wrote the bytes of this code in reverse order. So read likewise.
					byteRead++;
				}
				
				codeInStrTemp = Integer.toBinaryString(codeInInt);
				StringBuilder codeInStr = new StringBuilder();
				for(int j = 0; j < CODE_BIT_COUNT - codeInStrTemp.length(); j++){
					codeInStr.append("0");
				}
				codeInStr.append(codeInStrTemp);
				
				shortestCodeLen = Math.min(shortestCodeLen, codeInStr.length());//in order to faster the later comparison when decoding
				
				hCodesMap.put(KEY, codeInStr.toString());
			}
			
			System.out.println("just read hcode map: " + hCodesMap);
			
			
			//-------------start to read 8 bytes for bit count-------------
			for(byteRead = 0; byteRead < 8; byteRead++){
				bitCountOfFile += (bis.read() << (7 - byteRead) * 8);
			}
			System.out.println("just read bit count: " + bitCountOfFile);
			
			
			//-------------start to decode file-------------
			StringBuilder bufferString = new StringBuilder();
			boolean bufferTooShort = false;
			int bitRead = 0;
			b = 0;
			while(bitRead < bitCountOfFile){
				if((bufferString.length() < BUFFER_NUM && bufferTooShort) && b != -1){
					b = bis.read();
//					bitRead += 8;
					if(b != -1){
						bufferString.append(get8BitsBinaryStrFromInt(b));
					}
					System.out.print("reading --- " + bufferString+"|" + bufferString.length() + "\n");
				} else {
					int startIndex = 0;
					int endIndex = shortestCodeLen;
//					System.out.print("else --- " + bufferString+"|" + bufferString.length() + "\n");
					bufferTooShort = false;
					while(bitRead < bitCountOfFile && endIndex <= bufferString.length() && !bufferTooShort){
						String strToCompare = bufferString.substring(startIndex, endIndex);
						boolean matched = false;
						for(Entry<Integer, String> entry : hCodesMap.entrySet()){
							if(strToCompare.equals(entry.getValue())){
								bos.write(entry.getKey());
								bitRead += entry.getValue().length();
								System.out.println("bitRead " + bitRead + "  " + strToCompare);
								startIndex = endIndex;
								endIndex = startIndex + shortestCodeLen;
								
								matched = true;
								break;
							}
						}
						if(!matched){
							endIndex++;
						}
						
						if(endIndex > bufferString.length()){
							bufferString = new StringBuilder(bufferString.substring(startIndex));//put the remaining string into new string
							bufferTooShort = true;
						}
					}
					bufferTooShort = true;
				}
				
			}
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if(bis != null){
				try {
					bis.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if(bos != null){
				try {
					bos.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		System.out.println("time spent: " + ((System.currentTimeMillis() - startTime)));
	}
	
	private static String getOriginalFileExtension(String encodedFilename){
		String[] ss = encodedFilename.split("\\.");
		if(ss.length >= 2){
			return ss[ss.length - 2];
		}
		return null;
	}
	
	private static String get8BitsBinaryStrFromInt(int integer){
		String tmp = Integer.toBinaryString(integer);
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < 8 - tmp.length(); i++){
			sb.append("0");
		}
		sb.append(tmp);
		
		return sb.toString();
	}
	
	/**
	 * 
	 * @param str
	 * @param moveBitsToLeft If true, binary string 111 will be converted to 11100000, which is useful when decoding the last byte of file.
	 * <br/>If false, binary string 111 will be converted to 00000111, which should be used when writing hcode maps.
	 * @return
	 */
	private static int getIntFromBinaryString(String str, boolean moveBitsToLeft){
		final int len = str.length();
		
		int i = 0;
		int result = 0;
		char c;
		while(i < len){
			c = str.charAt(i);
			result <<= 1;
			result += c - '0';
			i++;
		}
		
		if(moveBitsToLeft){
			result <<= 8 - len;//when encoding file, if len < 8, this byte must be the last one. We must put all the bits to the very left or it won't be decoded correctly.
		}
		
		return result;
	}
	
	private static HashMap<Integer, Integer> readFrequenciesFromFile(String filename){
		HashMap<Integer, Integer> freqMap = new HashMap<>();
		BufferedInputStream bis = null;
		try {
			bis = new BufferedInputStream(new FileInputStream(filename));
			int b = 0;
			while((b = bis.read()) != -1){
				Integer value = freqMap.get(b);
				if(value == null){
					freqMap.put(b, 1);
				} else {
					freqMap.put(b, value + 1);
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if(bis != null){
				try {
					bis.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return freqMap;
	}
	
}
