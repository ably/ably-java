package io.ably.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public abstract class Multicaster<T> {

	protected final List<T> members = new ArrayList<T>();

	public Multicaster(T... members) { for(T m : members) this.members.add(m); }
	
	public void add(T member) { members.add(member); }
	public void remove(T member) { members.remove(member); }
	public void clear() { members.clear(); }
	public boolean isEmpty() { return members.isEmpty(); }
	public int size() { return members.size(); }
	public Iterator<T>  iterator() { return members.iterator(); }
}
