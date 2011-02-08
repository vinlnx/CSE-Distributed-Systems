package edu.washington.cs.cse490h.lib;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * File reader abstraction. This is basically a wrapped BufferedReader except it
 * will store things in the correct place. Students should only use the provided
 * classes to access the disc.
 */
public class PersistentStorageInputStream extends FileInputStream {
	private Node n;
	
	PersistentStorageInputStream(Node n, String filename)
			throws FileNotFoundException {
		super(Utility.realFilename(n.addr, filename));
		this.n = n;
	}
	
	@Override
	public int read() throws IOException {
		n.handleDiskReadEvent("");
		
		return super.read();
	}
	
	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		n.handleDiskReadEvent("b:" + new String(b) + " offset:"+ off + " len:" + len);

		return super.read(b, off, len);
	}

	@Override
	public int read(byte[] b) throws IOException {
		n.handleDiskReadEvent("b:" + new String(b));

		return super.read(b);
	}
	
	@Override
	public long skip(long skipN) throws IOException {
		n.handleDiskReadEvent("skipN:" + skipN);
		
		return super.skip(skipN);
	}
}
