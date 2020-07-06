package bigrams;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Bigrams_sequential {
	
	public static void bigrams(LinkedList<String> txtList, HashMap<String, Integer> dict, String regEx) throws IOException {
		String txtName = txtList.poll();	
		FileReader myObj1 = new FileReader("texts/" + txtName);
		BufferedReader buffer = new BufferedReader(myObj1);
		String line;
		String prevToken = "" ;
		String nextToken = "";
		Pattern searchToken = Pattern.compile(regEx); 
		Matcher m = null;
		String nGram = null;
			
		while((line = buffer.readLine())!=null){
			m = searchToken.matcher(line);
			if(!m.find()) {
				continue;
			}
			break;
		}
		
		if(line != null) {
			m = searchToken.matcher(line);
			if(m.find()) {
				prevToken = m.group();
			}
			while(m.find()) {
				nextToken = m.group();
				nGram = (prevToken + nextToken);
				dict.merge(nGram, 1, (prev, one) -> prev + one);
				prevToken = nextToken;
			}
		}
		
		while ((line = buffer.readLine()) != null) {
			m = searchToken.matcher(line);
			if(!m.find()) {
				continue;
			}
			m = searchToken.matcher(line);
			
			while(m.find()) {
				nextToken = m.group();
				nGram = (prevToken +  nextToken);
				dict.merge(nGram, 1, (prev, one) -> prev + one);
				prevToken = nextToken;
			}
		}
		buffer.close();
	}
	
	public static HashMap<String, Integer> execution(LinkedList<String> txtList, String regEx) {
		HashMap<String, Integer> dict = new HashMap<String, Integer>();
		
		while(!txtList.isEmpty()) {
			try {
				bigrams(txtList, dict, regEx);
			} catch (IOException e) {
				e.printStackTrace();
			}	
		}
		return dict;
	}
	
	public static void loadDatasets(LinkedList<String> txtListMain) {
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get("texts"))){
			for (Path path : stream) {
				if (!Files.isDirectory(path)) {
					txtListMain.add(path.getFileName().toString());
	            }
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("Input txt files: " + txtListMain + "\n");
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
		LinkedList<String> txtListMain = new LinkedList<String>();
		loadDatasets(txtListMain);
	
		for (int j =0; j<numIter; j++) {
			LinkedList<String> txtList = new LinkedList<String>(txtListMain);
			//####START EXECUTION TIME #####
			long startTime = System.nanoTime();
			execution(txtList, regEx);
			
			long finishTime = System.nanoTime();
			double elapsedTime = (double) ((finishTime - startTime)/1000000000.0);
			System.out.println("Sequential elapsed time:: " + elapsedTime + " sec");
			sumElapsedTime += elapsedTime;
					
			/*
			//Print HasMaps of Bigrams
	    	for (String name: dict.keySet()){
	            String key = name;
	            Integer value = dict.get(name); 
	            System.out.println(key + " " + value);   
	        }
	        */   
	        
		}
		System.out.println("MEAN OF TIMES IN " + numIter + " ITERATIONS: " + sumElapsedTime/numIter + " sec"); 
	}
}

