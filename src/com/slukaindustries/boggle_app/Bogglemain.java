package com.slukaindustries.boggle_app;

import java.util.HashSet;
import java.util.Set;
import java.util.Locale;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import java.net.Socket;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.content.res.AssetManager;
import android.widget.ProgressBar;

public class Bogglemain extends Activity {

	private static Socket socket;
	private static PrintWriter pw;
	private static BufferedReader br; 
	private static final int port = 63400;
	private static final String ip = "50.141.229.223";
	
	public boolean foundIdentity;
	
	public final static String EXTRA_MESSAGE = "com.slukaindustries.boggle_app.MESSAGE";
	public char[][] board; //Representation of the boggle board
	public String activeIdentity; //The current identity of the board
	public int score; //The total possible score of the board
	public Set<String> posWords; //The set containing all possible words
	public Set<String> foundWords; //The set of all real words
	public Set<String> dict; //A dictionary. What more do you want?
	public String method; //The method of retrieving the possible words
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_boggle_main);
		board = new char[4][4];
		posWords = new HashSet<String>();
		foundWords = new HashSet<String>();
		dict = new HashSet<String>();
		foundIdentity = false;
		method = "";
		
		debug("Loading files...");
		loadDictionary();
		
		String s1 = "Number of dictionary entries: "+dict.size();
		debug(s1);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.bogglemain, menu);
		return true;
	}
	
	/* Loads the dictionary into the program (set<String> dict)
	 */
	public void loadDictionary(){
		AssetManager am = this.getAssets();
		InputStream is;
		
		try {
			is = am.open("dict.txt");
		} catch (IOException e) {
			debug("Failed Opening");
			return;
		}
		
		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		
		//Read file lines
		try {
			String line;
			while((line = br.readLine())!=null){
				dict.add(line);
			}
		} catch (IOException e) {
			debug("Failed Read");
			return;
		}
		
		//Close
		try {
			br.close();
		} catch (IOException e) {
			debug("Failed Close");
			return;
		}
	}
	
	/* Erases all public variables. Used for rerunning the app
	 */
	public void restore(){
		/*board = new char[4][4];
		for(int a=0;a<4;a++){
			for(int b=0;b<4;b++){
				board[a][b] = 0;
			}
		}*/
		posWords.clear();
		foundWords.clear();
		foundIdentity = false;
		score = 0;
		activeIdentity = "";
		method = "";
	}
	
	/* The main logic for the boggle word search tool
	 */
	public void findWordsMain(View view){
		
		parseBoard();
		
		boolean copyExists = lookupBoard();
		
		//TODO: Put into function
		boolean used[][] = new boolean[4][4];
		for(int x=0;x<4;x++){
			for(int y=0;y<4;y++){
				used[x][y] = false;
			}
		}
		
		if(copyExists){
			method = "Cloud";
			debug("Identity Exists...");
			RWThread();
			displayResults();
		} else {
			method = "Local";
			debug("New Identity...");
			//Manually look for all of the words
			for(int y=0;y<4;y++){
				for(int x=0;x<4;x++){
					char rootLetter = board[y][x];
					used[y][x]=true;
					
					int loX = x-1;
					int hiX = x+1;
					int loY = y-1;
					int hiY = y+1;
					
					String word = Character.toString(rootLetter);
					
					for(int j=loY;j<=hiY;j++){
						for(int i=loX;i<=hiX;i++){
							if(i==x && j==y){
							} else {
								if(i>=0 && i<=3 && j>=0 && j<=3){
									recurseGetLetters(j,i,used,word);
									for(int a=0;a<4;a++){
										for(int b=0;b<4;b++){
											used[a][b] = false;
										}
										used[y][x] = true;
									}
								}
							}
						}
					}
					used[y][x] = false;
				}
			}
			
			lookupWords(); //Looks up words from dictionary
			
			IIDThread(); //Insert identity into database
		}
		
		calcScore();
		displayResults();
		
		debug("Printing words...");
		debug("-----------------");
		debug("FOUND: "+posWords.size());
		debug("TOTAL: "+foundWords.size());
		
		restore();
	}
	
	/* Checks the dictionary for the list of possible words
	 */
	public void lookupWords(){
		debug("LOoking up...");
		for(String s : posWords){
			if(dict.contains(s)){
				foundWords.add(s);
			}
		}
		
		/*String out = "";
		for(String s : foundWords){
			debug(s);
			out = out + s+", ";
		} 
		
		EditText ed18 = (EditText)findViewById(R.id.editText18);
		ed18.setText(out);*/
		
		calcScore();
		}
	
	/* Calculates the total possible score of the words found
	 */
	public void calcScore(){
		for(String s : foundWords){
			if(s.length() == 3 || s.length() == 4){
				score = score + 1;
			} else if(s.length() == 5){
				score = score + 2;
			} else if(s.length() == 6){
				score = score + 3;
			} else if(s.length() == 7){
				score = score + 5;
			} else if(s.length() >= 8){
				score = score + 11;
			}
		}
	}
	
	//Calculates the score of an individual word
	public int wordScore(String word){
		int score = 0;
		if(word.length() == 3 || word.length() == 4){
			score = 1;
		} else if(word.length() == 5){
			score = 2;
		} else if(word.length() == 6){
			score = 3;
		} else if(word.length() == 7){
			score = 5;
		} else if(word.length() >= 8){
			score = 11;
		}
		return score;
	}
	
	/* The recursive component of the board traveler
	 */
	public void recurseGetLetters(int y, int x, boolean[][] used, String word){
		if(used[y][x] == true){
			//debug("Final: "+word);
			if(word.length() > 2){
				posWords.add(word.toUpperCase(Locale.getDefault()));
			}
			return;
		}
		
		word = word + board[y][x];
		//debug(word);
		used[y][x] = true;
		
		int loX = x-1;
		int hiX = x+1;
		int loY = y-1;
		int hiY = y+1;
		
		for(int j=loY;j<=hiY;j++){
			for(int i=loX;i<=hiX;i++){
				if(i==x && j==y){
				} else {
					if(i>=0 && i<=3 && j>=0 && j<=3){
						//String t3 = "Letter: ("+i+","+j+") "+board[j][i];
						//debug(t3);
						recurseGetLetters(j,i,used,word);
					}
				}
			}
		}
	}
	
	/* Checks the SQL server for previous instances of the given board
	 */
	public boolean lookupBoard(){
		System.out.println("LookupBoard...");
		Thread lt = new Thread(new lookupThread());
		lt.start();
		try{
			lt.join();	
		} catch(Exception e){
			debug(e.toString());
		}
		
		return foundIdentity;
	}

	/* Thread for checking if the identity already exists in the database
	 */
	class lookupThread implements Runnable {
		public void run(){
			try{
				debug("Looking up...");
				socket = new Socket(ip,port);
				pw = new PrintWriter(socket.getOutputStream(),true);
				pw.println("lookup1|"+activeIdentity);
				
				br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				
				String inputLine = br.readLine();
				if(Integer.parseInt(inputLine) == -1){
					debug("Doesn't exist...");
				}
				if(Integer.parseInt(inputLine) == 1){
					debug("Found identity");
					foundIdentity = true;
				}
				debug(inputLine);
				
				pw.close();
				br.close();
				socket.close();
			} catch(Exception e){
				debug(e.toString());
			}
		}
	}
	
	//Controller for retrieveWordsthread
	public void RWThread(){
		System.out.println("RWThread...");
		Thread rw = new Thread(new retrieveWordsThread());
		rw.start();
		try{
			rw.join();	
		} catch(Exception e){
			debug(e.toString());
		}
		System.out.println("RWThread closing...");
	}
	
	/* Thread for retrieving all words in the words database with ID "activeIdentity"
	 */
	class retrieveWordsThread implements Runnable {
		public void run(){
			try {
				debug("Retrieving words...");
				
				socket = new Socket(ip,port);
				pw = new PrintWriter(socket.getOutputStream(),true);
				br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				pw.println("retrieve2|"+activeIdentity);
				
				String inline;
                while(!(inline = br.readLine()).equals("##")){
                	System.out.println(inline);
                    foundWords.add(inline);
                }
				
				pw.close();
				br.close();
				socket.close();
			} catch(Exception e){
				debug(e.toString());
			}
		}
	}
	
	/* Inserts identity into database
	 */
	public void IIDThread(){
		System.out.println("IIDThread...");
		Thread iid = new Thread(new insertIDThread());
		iid.start();
		try{
			iid.join();
		} catch (Exception e){
			debug(e.toString());
		}
		System.out.println("Ending IIDThread...");
	}
	
	/* Thread for inserting activeIdentity into database
	 */
	class insertIDThread implements Runnable {
		public void run(){
			try {
				socket = new Socket(ip,port);
				pw = new PrintWriter(socket.getOutputStream(),true);
				br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				
				pw.println("insert3|"+activeIdentity+"|"+foundWords.size()+"|"+score);
				
				String returned = br.readLine();
				
				if(Integer.parseInt(returned) == 1){
					for(String s : foundWords){
						pw.println(s+"|"+activeIdentity+"|"+wordScore(s));
					}
				}
				
				pw.println("##");
				
				pw.close();
				br.close();
				socket.close();
			} catch (Exception e){
				debug(e.toString());
			}
		}
	}
	
	/* Reads the contents of the board on the screen, assigns an identity, 
	 * and stores a copy of the board in the board array
	 */
	public void parseBoard(){
		//Extract all of the editText contents
		EditText ed1 = (EditText)findViewById(R.id.editText1);
		EditText ed2 = (EditText)findViewById(R.id.editText2);
		EditText ed3 = (EditText)findViewById(R.id.editText3);
		EditText ed4 = (EditText)findViewById(R.id.editText4);
		EditText ed5 = (EditText)findViewById(R.id.editText5);
		EditText ed6 = (EditText)findViewById(R.id.editText6);
		EditText ed7 = (EditText)findViewById(R.id.editText7);
		EditText ed8 = (EditText)findViewById(R.id.editText8);
		EditText ed9 = (EditText)findViewById(R.id.editText9);
		EditText ed11 = (EditText)findViewById(R.id.editText11);
		EditText ed12 = (EditText)findViewById(R.id.editText12);
		EditText ed13 = (EditText)findViewById(R.id.editText13);
		EditText ed14 = (EditText)findViewById(R.id.editText14);
		EditText ed15 = (EditText)findViewById(R.id.editText15);
		EditText ed16 = (EditText)findViewById(R.id.editText16);
		EditText ed17 = (EditText)findViewById(R.id.editText17);
		
		//Loads the contents of the boxes into a 4x4 char array
		board[0][0] = ed1.getText().toString().charAt(0);
		board[0][1] = ed2.getText().toString().charAt(0);
		board[0][2] = ed3.getText().toString().charAt(0);
		board[0][3] = ed4.getText().toString().charAt(0);
		board[1][0] = ed5.getText().toString().charAt(0);
		board[1][1] = ed6.getText().toString().charAt(0);
		board[1][2] = ed7.getText().toString().charAt(0);
		board[1][3] = ed8.getText().toString().charAt(0);
		board[2][0] = ed9.getText().toString().charAt(0);
		board[2][1] = ed11.getText().toString().charAt(0);
		board[2][2] = ed12.getText().toString().charAt(0);
		board[2][3] = ed13.getText().toString().charAt(0);
		board[3][0] = ed14.getText().toString().charAt(0);
		board[3][1] = ed15.getText().toString().charAt(0);
		board[3][2] = ed16.getText().toString().charAt(0);
		board[3][3] = ed17.getText().toString().charAt(0);
		
		//Creates an identity for the board for future storage/searching
		activeIdentity = ed1.getText().toString() +
				ed2.getText().toString() +
				ed3.getText().toString() +
				ed4.getText().toString() +
				ed5.getText().toString() +
				ed6.getText().toString() +
				ed7.getText().toString() +
				ed8.getText().toString() +
				ed9.getText().toString() +
				ed11.getText().toString() +
				ed12.getText().toString() +
				ed13.getText().toString() +
				ed14.getText().toString() +
				ed15.getText().toString() +
				ed16.getText().toString() +
				ed17.getText().toString();
				
		//Displays the text
		EditText ed10 = (EditText)findViewById(R.id.editText10);
		ed10.setText(activeIdentity);	
	}

	//Displays the contents of foundWords in the found box
	public void displayResults(){
		String out1 = "";
		for(String s : foundWords){
			out1 = out1 + s + ",";
		}
		
		EditText ed18 = (EditText)findViewById(R.id.editText18);
		ed18.setText(out1);	
		
		EditText ed20 = (EditText)findViewById(R.id.editText20);
		ed20.setText(method);
		
		EditText ed19 = (EditText)findViewById(R.id.editText19);
		ed19.setText(Integer.toString(score));
	}
	
	/* Debugging output
	 */
	public void debug(String msg){
		Log.d("Debugging",msg);
	}
}
