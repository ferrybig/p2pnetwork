/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package me.ferrybig.p2pnetwork;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Fernando
 */
public class RoutingMap {

	private static final Logger LOG = Logger.getLogger(RoutingMap.class.getName());

	private volatile Map<Address, SortedMap<Byte, List<Address>>> routingTable = Collections.emptyMap();

	private volatile Map<Address, Map<Address, Byte>> tempRoutingList = Collections.emptyMap();

	private volatile Map<Address, Map<Address, Byte>> lastTempRoutingList = Collections.emptyMap();

	private final List<Runnable> fullUpdateListeners = new CopyOnWriteArrayList<>();

	private final List<Runnable> partialUpdateListeners = new CopyOnWriteArrayList<>();
	private final Address self;

	public RoutingMap(Address self) {
		this.self = self;
	}
	
	public void rebuildRoutingMap() {
		boolean shouldCallFullUpdateListeners = false;
		boolean shouldCallPartialUpdateListeners = false;
		synchronized (this) {
			LOG.info("Updating routing map...");
			@SuppressWarnings("LocalVariableHidesMemberVariable")
			Map<Address, Map<Address, Byte>> tempRoutingList = this.tempRoutingList;
			@SuppressWarnings("LocalVariableHidesMemberVariable")
			Map<Address, SortedMap<Byte, List<Address>>> routingTable = this.routingTable;
			if (tempRoutingList == null) {
				return;
			}
			if (lastTempRoutingList.equals(tempRoutingList)) { 
				return; // No update
			}
			Map<Address, SortedMap<Byte, List<Address>>> newRoutingTable = new HashMap<>();
			for (Map.Entry<Address, Map<Address, Byte>> submap : tempRoutingList.entrySet()) {
				Address via = submap.getKey();
				{
					assert !self.equals(via);
					final SortedMap<Byte, List<Address>> sorted = newRoutingTable.computeIfAbsent(via, k -> new TreeMap<>());
					final List<Address> list = sorted.computeIfAbsent((byte)(0), k -> new ArrayList<>());
					list.add(via);
				}
				for (Map.Entry<Address, Byte> entry : submap.getValue().entrySet()) {
					Address to = entry.getKey();
					Byte prio = entry.getValue();
					if(self.equals(to)) {
						continue;
					} else if(prio == -1) { //unsigned 255
						continue;
					}
					final SortedMap<Byte, List<Address>> sorted = newRoutingTable.computeIfAbsent(to, k -> new TreeMap<>());
					final List<Address> list = sorted.computeIfAbsent((byte)(prio + 1), k -> new ArrayList<>());
					list.add(via);
				}
			}
			this.routingTable = newRoutingTable;
			this.lastTempRoutingList = tempRoutingList;
			LOG.info("New routing table: " + newRoutingTable);
			if (routingTable.size() != newRoutingTable.size()) {
				shouldCallFullUpdateListeners = true;
			} else {
				for (Map.Entry<Address, SortedMap<Byte, List<Address>>> entry : routingTable.entrySet()) {
					SortedMap<Byte, List<Address>> other = newRoutingTable.get(entry.getKey());
					if (other == null) {
						// 
						shouldCallFullUpdateListeners = true;
						break;
					}
					if (!entry.getValue().firstKey().equals(other.firstKey())) {
						shouldCallPartialUpdateListeners = true;
						break;
					}
				}
			}
		}
		if (shouldCallFullUpdateListeners) {
			LOG.info("Running full update listeners");
			for (Runnable r : this.fullUpdateListeners) {
				r.run();
			}
		}
		if (shouldCallPartialUpdateListeners) {
			LOG.info("Running partial update listeners");
			for (Runnable r : this.partialUpdateListeners) {
				r.run();
			}
		}
	}

	public boolean addFullUpdateListener(Runnable e) {
		return fullUpdateListeners.add(e);
	}

	public boolean removeFullUpdateListener(Runnable o) {
		return fullUpdateListeners.remove(o);
	}

	public boolean addPartialUpdateListener(Runnable e) {
		return fullUpdateListeners.add(e);
	}

	public boolean removePartialUpdateListener(Runnable o) {
		return fullUpdateListeners.remove(o);
	}

	public void setTempRoutingList(Map<Address, Map<Address, Byte>> tempRoutingList) {
		this.tempRoutingList = tempRoutingList;
	}

	@Nullable
	public <T> T tryRoute(Address to, Function<Address, T> isValid) {
		T router = isValid.apply(to); // Try direct
		if (router != null) {
			return router;
		}
		assert router == null;
		SortedMap<Byte, List<Address>> res = routingTable.get(to);
		if (res == null) {
			return null;
		}
		Iterator<List<Address>> fullAddrList = res.values().iterator();
		while (fullAddrList.hasNext()) {
			Iterator<Address> addrList = fullAddrList.next().iterator();
			while (addrList.hasNext()) {
				Address via = addrList.next();
				router = isValid.apply(via);
				if (router != null) {
					LOG.log(Level.INFO, "Routing packet {0} via {1} ",
							new Object[]{to, via});
					return router;
				}
				LOG.log(Level.INFO, "Failed to route {0} via {1}: connection lost",
						new Object[]{to, via});
				//addrList.remove(); // Invalid router addr? REMOVE IT
			}
			//fullAddrList.remove();
		}
		//routingTable.remove(to, res);
		assert router == null;
		return null;
	}

	public Set<Address> getKnownAddresses() {
		return this.routingTable.keySet();
	}

	public Map<Address, Byte> generateDelegatedRoutingMap() {
		LinkedHashMap<Address, Byte> map = new LinkedHashMap<>();
		for (Map.Entry<Address, SortedMap<Byte, List<Address>>> entry : this.routingTable.entrySet()) {
			SortedMap<Byte, List<Address>> val = entry.getValue();
			map.put(entry.getKey(), (byte)(val.firstKey()));
		}
		return map;
	}
}
