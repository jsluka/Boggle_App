The Boggle_App is an Android (4.1) application developed for Purdue's CS252 class.

The Boggle_App takes a boggle board as input (a 4x4 grid of letters) and determines what words, if any, can be formed using the letters on the board. 

Additionally, the Boggle_App stores the words in an SQL with the identity (the combination of the board's letters) as the primary key. When a new board is entered, the app contacts the SQL server and queries the identity table for the new identity. If the identity already exists, the app requests all of the words stored in the word identity candidate database and outputs them. If the identity does not exist, the app calculates which words are possible candidates and sends them to the SQL database for storage.
