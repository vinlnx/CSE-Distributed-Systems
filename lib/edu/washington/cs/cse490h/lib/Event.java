package edu.washington.cs.cse490h.lib;

import edu.washington.cs.cse490h.lib.Manager.Timeout;

public class Event {
	public final int node;
	public final EventType t;

	public static enum EventType {
		FAILURE, START, EXIT, COMMAND, ECHO, TIME, DELIVERY, TIMEOUT
	}

	String command;
	String[] msg;
	
	Packet p;
	Timeout to;
	
	//TODO: clean this class up!
	// FAILURE, START, EXIT
	public Event(int node, EventType t) {
		this.node = node;
		this.t = t;
	}
	
	// COMMAND
	public Event(int node, String command) {
		this.node = node;
		t = EventType.COMMAND;
		this.command = command;
	}
	
	// ECHO, TIME
	public Event(EventType t, String[] msg){
		node = -1;
		this.t = t;
		this.msg = msg;
	}
	
	// DELIVERY
	public Event(Packet p) {
		this.p = p;
		t = EventType.DELIVERY;
		node = p.getDest();
	}
	
	// TIMEOUT
	public Event(Timeout to) {
		this.to = to;
		t = EventType.TIMEOUT;
		node = to.node.addr;
	}
	
	public String toString() {
		switch(t) {
		case FAILURE:
			return "FAILURE " + node;
		case START:
			return "START " + node;
		case EXIT:
			return "EXIT";
		case COMMAND:
			return "COMMAND " + node + " executes " + command;
		case ECHO:
			return "ECHO " + msg;
		case TIME:
			return "TIME " + msg;
		case DELIVERY:
			return "DELIVERY " + p;
		case TIMEOUT:
			return "TIMEOUT " + to;
		default:
			return "UNKNOWN EVENT TYPE " + t;
		}
	}
	
	public String toSynopticString() {
		switch(t) {
		
		
		case EXIT:
			return "EXIT";
		case COMMAND:
			return "COMMAND " + command;
		case ECHO:
			return "ECHO " + msg;
		case TIME:
			return "TIME " + msg;
		case DELIVERY:
			return "DELIVERY " + p.toSynopticString();
		case TIMEOUT:
			return "TIMEOUT " + to;
		default:
			return "UNKNOWN EVENT TYPE " + t;
		}
	}
	
}