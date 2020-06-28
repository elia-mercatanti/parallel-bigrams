import java.io.IOException;
import java.lang.Thread;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Alternative_bigrams_parallel {
	private ConcurrentHashMap<String, Integer> globalDict;
	private ConcurrentLinkedQueue<String> notBlockingqueue;
	private LinkedList<String> txtQueue;
	private final String NO_MORE_MESSAGES = UUID.randomUUID().toString();
	private Pattern searchToken;
	
	public Alternative_bigrams_parallel(String regEx) {
		this.globalDict = new ConcurrentHashMap<String, Integer>();
		this.txtQueue = new LinkedList<String>();
		this.notBlockingqueue = new ConcurrentLinkedQueue<String>();
		this.searchToken = Pattern.compile(regEx);
	}
	
	public class producer extends Thread {
		String txt;
		String line;
		String lastWord;
		String mergedLine;
		String lastLine;
		
		public producer() {
			this.lastWord = "";
		}
		
		public void run() {
			//long prodStartTime = System.nanoTime();
			
			while ((this.txt = txtQueue.poll()) != null) {
			
				String txt;
				try {
					txt = Files.readString(Paths.get("texts/" + this.txt));
					notBlockingqueue.add(txt);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			//double elapsedTime = (double) ((System.nanoTime() - prodStartTime) / 1000000000.0);
			//System.out.println("Finish time producer " + elapsedTime);
		}
	}
	
	public class consumer extends Thread {
		private HashMap<String, Integer> dict;
		Matcher m;
		String textLine;
		String nGram;
		String prevWord;
		String nextWord;
		
		public consumer() {
			this.dict = new HashMap<String, Integer>();
		}

		public void run() {
			//long consStartTime = System.nanoTime();
			while (true) {
				this.textLine = notBlockingqueue.poll();
				if (this.textLine == null) {
					continue;
				}
				if (this.textLine == NO_MORE_MESSAGES) {
					break;
				}
				
				this.m = searchToken.matcher(this.textLine);
				if(this.m.find()) {
					this.prevWord = this.m.group();
				} 
				while(this.m.find()) {
					this.nextWord = this.m.group();
					this.nGram = (this.prevWord + this.nextWord);
					dict.merge(this.nGram, 1, (prev, one) -> prev + one);
					this.prevWord = this.nextWord;
				}
			}
			//Merge step
			for (String name : this.dict.keySet()) {
				String key = name;
				Integer value = this.dict.get(name);
				globalDict.merge(key, value, (prev, update) -> prev + update);
			}
			
			//double elapsedTime = (double) ((System.nanoTime() - consStartTime) / 1000000000.0);
			//System.out.println("Finish time consumer " + elapsedTime);		
			
			/*	
			// ######Print thread's dictionary######
			for (String name: this.dict.keySet()){
				String key = name;
				Integer value = this.dict.get(name);
				System.out.println(key + " " + value); 
			   }
			*/
		}
	}

	public static void main(String[] args) {
		String regEx = "";
		if(args.length != 1) {
			System.out.println("Passare un parametro: \"words\" oppure \"letters\"");
			System.exit(0);
		}
		else if(args[0].equals("letters")) {
			regEx = "[a-zA-Z]";
			System.out.println("Calcolo bigrammi di lettere");
		}
		else if(args[0].equals("words")) {
			regEx = "[a-zA-Z]+";
			System.out.println("Calcolo bigrammi di parole");
		} else {
			System.out.println("L'argomento passato al programma deve essere \"words\" oppure \"letters\"");
			System.exit(0);
		}
		int numIter = 1;
		double sumElapsedTime = 0.0;
		
		
		Alternative_bigrams_parallel parNgrams = new Alternative_bigrams_parallel(regEx);
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get("texts"))) {
			for (Path path : stream) {
				if (!Files.isDirectory(path)) {
					parNgrams.txtQueue.add(path.getFileName().toString());
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		System.out.println("Input txt files: " + parNgrams.txtQueue + "\n");
		LinkedList<String> txtQueueMain = new LinkedList<String>(parNgrams.txtQueue);

		for (int j = 0; j < numIter; j++) {
			parNgrams.globalDict = new ConcurrentHashMap<String, Integer>();
			parNgrams.txtQueue = new LinkedList<String>(txtQueueMain);

			long startTime = System.nanoTime();


			ExecutorService poolCons = Executors.newFixedThreadPool(4);
			for (int i = 0; i < 4; i++) {
				poolCons.execute(parNgrams.new consumer());
			}
			
			Thread producer = parNgrams.new producer();
			producer.start();
			try {
				producer.join();
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}
			

			for (int i = 0; i < 4; i++) {
				parNgrams.notBlockingqueue.add(parNgrams.NO_MORE_MESSAGES);
			}
			poolCons.shutdown();
			try {
				poolCons.awaitTermination(5L, TimeUnit.MINUTES);
			} catch (InterruptedException e) {
				System.out.println("An error occurred.");
				System.out.println(e.getMessage());
			}
			long finishTime = System.nanoTime();
			double elapsedTime = (double) ((finishTime - startTime) / 1000000000.0);
			sumElapsedTime += elapsedTime;
			
			/*
			// ######Print HasMaps of Bigrams######
	    	for (String name: parNgrams.globalDict.keySet()){
	            String key = name;
	            Integer value = parNgrams.globalDict.get(name);
	            System.out.println(key + " " + value);  
	        } 
	        */
	        
			System.out.println("PARALLEL ELAPSED TIME: " + elapsedTime + " sec");
		}
		System.out.println("MEAN OF TIMES IN " + numIter + " ITERATIONS: " + sumElapsedTime / numIter + " sec");
	}

}
