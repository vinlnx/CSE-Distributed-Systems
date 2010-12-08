package edu.washington.cs.cse490h.lib;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;

public class PersistentStorageReader extends BufferedReader{
	PersistentStorageReader(Node n, String filename) throws FileNotFoundException{
		super(new FileReader(Utility.realFilename(n.addr, filename)));
	}
}
