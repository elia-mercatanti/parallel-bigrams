package bigrams;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
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

public class Bigrams_parallel {
	private ConcurrentHashMap<String, Integer> globalDict;
	private ConcurrentLinkedQueue<String> notBlockingqueue;
	private LinkedList<String> txtQueue;
	private final String NO_MORE_MESSAGES = UUID.randomUUID().toString();
	private Pattern validTxtLine;
	private Pattern lastTokenPattern;
	private Pattern searchToken;
	
	public Bigrams_parallel(String regEx, String regExLastToken) {
		this.globalDict = new ConcurrentHashMap<String, Integer>();
		this.txtQueue = new LinkedList<String>();
		this.notBlockingqueue = new ConcurrentLinkedQueue<String>();
		this.validTxtLine = Pattern.compile("[a-zA-Z]");
		this.lastTokenPattern = Pattern.compile(regExLastToken);
		this.searchToken = Pattern.compile(regEx);
	}
	
	public class producer extends Thread {
		String txt;
		Matcher m;
		String lastToken;
		String line;
		String mergedLine;
		String lastLine;
		
		public producer() {
			this.lastToken = "";
		}
		
		public void run() {
			//long prodStartTime = System.nanoTime();
			
			while ((this.txt = txtQueue.poll()) != null) {
				try {
					FileReader myObj1 = new FileReader("texts/" + txt);
					BufferedReader buffer = new BufferedReader(myObj1);

					
					while ((this.line = buffer.readLine()) != null) {
						this.m = validTxtLine.matcher(line);
						if(!this.m.find()) {
							continue;
						}
						
						this.lastLine = this.line;
						this.mergedLine = this.line;
						for (int i = 0; i < 20; i++) {
							if ((this.line = buffer.readLine()) == null) {
								break;
							}
							
							this.m = validTxtLine.matcher(this.line);
							if(!this.m.find()) {
								continue;
							}
							
							this.lastLine = this.line;
							this.mergedLine = this.mergedLine + " " + this.line;
						}		
						notBlockingqueue.add(this.lastToken + " " + this.mergedLine);
						this.m = lastTokenPattern.matcher(this.lastLine);						
					
						if(this.m.find()) {
							this.lastToken = this.m.group();
						}
						else {
							this.lastToken = "";
						}
						
					}
					buffer.close();
				} catch (FileNotFoundException e) {
					e.printStackTrace();
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
		String prevToken;
		String nextToken;
		
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
					this.prevToken = this.m.group();
				} 
				while(this.m.find()) {
					this.nextToken = this.m.group();
					this.nGram = (this.prevToken + this.nextToken);
					dict.merge(this.nGram, 1, (prev, one) -> prev + one);
					this.prevToken = this.nextToken;
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
		String regExLastToken = "";
		if(args.length != 1) {
			System.out.println("Passare un parametro: \"words\" oppure \"letters\"");
			System.exit(0);
		}
		else if(args[0].equals("letters")) {
			regEx = "[a-zA-Z]";
			regExLastToken = "[a-zA-Z][^a-zA-Z]*$";
			System.out.println("Calcolo bigrammi di lettere");
		}
		else if(args[0].equals("words")) {
			regEx = "[a-zA-Z]+";
			regExLastToken = "[a-zA-Z]+[^a-zA-Z]*$";
			System.out.println("Calcolo bigrammi di parole");
		} else {
			System.out.println("L'argomento passato al programma deve essere \"words\" oppure \"letters\"");
			System.exit(0);
		}
			
		int numIter = 1;
		double sumElapsedTime = 0.0;
		
		Bigrams_parallel parNgrams = new Bigrams_parallel(regEx, regExLastToken);
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
