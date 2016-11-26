/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package me.ferrybig.p2pnetwork;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import me.ferrybig.p2pnetwork.codec.packets.Packet;

/**
 *
 * @author Fernando van Loenhout
 */
public class MultiConnection {

	private final Address directNode;
	private final Address ourAddress;
	private final Set<Address> blocked = new HashSet<>();
	private final List<Runnable> routingChangedListeners = new CopyOnWriteArrayList<>();
	private volatile ConnectionList connections;
	private final EventExecutor events;
	private volatile Map<Address, Byte> knownDestinations = Collections.emptyMap();
	private final Consumer<Map<Address, Byte>> routingChangeListener = new Consumer<Map<Address, Byte>>() {
		@Override
		public void accept(Map<Address, Byte> e) {
			if(!e.equals(knownDestinations)) {
				knownDestinations = e;
				for(Runnable r : routingChangedListeners) {
					r.run();
				}
			}
		}
	};

	public MultiConnection(Address directNode, Address ourAddress, EventExecutor events, LocalConnection first) {
		this.directNode = directNode;
		this.ourAddress = ourAddress;
		this.connections = new ConnectionList(new LocalConnection[]{first}, new AtomicInteger());
		this.events = events;
		first.addRoutingChangedListener(routingChangeListener);
	}

	public Future<?> sendPacket(Address destination, Packet packet) {
		LocalConnection con = connections.getNext();
		if (con == null) {
			return events.newFailedFuture(new IllegalStateException("All connections closed"));
		}
		return con.sendPacket(destination, packet);
	}

	public Future<?> sendPacket(Packet packet) {
		LocalConnection con = connections.getNext();
		if (con == null) {
			return events.newFailedFuture(new IllegalStateException("All connections closed"));
		}
		return con.sendPacket(packet);
	}

	public void removeRedundency() {
		if (this.connections.size < 2) {
			return;
		}
		synchronized (this) {
			ConnectionList main = connections;
			if (main.size < 2) {
				return;
			}
			LocalConnection[] array = new LocalConnection[1];
			array[0] = main.array[main.size - 2];
			for (int i = 0; i < main.size - 1; i++) {
				main.array[i].close(LocalConnection.CloseReason.SEE_OTHER_CONNECTION, true);
			}
			assert Thread.holdsLock(this);
			assert main == connections; // This will fail randomly if we have a non synchronized update anywere
			connections = new ConnectionList(array, main.roundRobinCounter);
		}
	}

	public Address getDirectNode() {
		return directNode;
	}

	public Map<Address, Byte> getKnownDestinations() {
		return knownDestinations;
	}

	public Set<Address> getBlocked() {
		return blocked;
	}

	public List<Runnable> getRoutingChangedListeners() {
		return routingChangedListeners;
	}

	public ConnectionList getConnections() {
		return connections;
	}

	public synchronized void add(LocalConnection local) {
		add0(local);
	}

	private void add0(LocalConnection local) {
		ConnectionList main = connections;
		LocalConnection[] array = new LocalConnection[main.array.length + 1];
		System.arraycopy(main.array, 0, array, 0, main.array.length);
		array[main.array.length] = local;
		assert Thread.holdsLock(this);
		assert main == connections; // This will fail randomly if we have a non synchronized update anywere
		connections = new ConnectionList(array, main.roundRobinCounter);
		local.addRoutingChangedListener(routingChangeListener);
	}

	public synchronized void remove(LocalConnection local) {
		remove0(local);
	}

	private void remove0(LocalConnection local) {
		ConnectionList main = connections;
		int index = -1;
		final int prevSize = main.array.length;
		for (int i = 0; i < prevSize; i++) {
			if (main.array[i].equals(local)) {
				index = i;
			}
		}
		if (index < 0) {
			return;
		}
		assert index < prevSize;
		LocalConnection[] array = new LocalConnection[prevSize - 1];
		if (index > 0) {
			System.arraycopy(main.array, 0, array, 0, index);
		}
		int remaining = prevSize - index - 1;
		assert index + remaining + 1 == prevSize;
		if (remaining > 0) {
			System.arraycopy(main.array, index + 1, array, index, remaining);
		}
		assert Thread.holdsLock(this);
		assert main == connections; // This will fail randomly if we have a non synchronized update anywere
		connections = new ConnectionList(array, main.roundRobinCounter);
	}

	@Nullable
	public LocalConnection getAny() {
		return this.connections.getNext();
	}

	private static class ConnectionList {

		private final LocalConnection[] array;
		private final AtomicInteger roundRobinCounter;
		private final int size;

		public ConnectionList(LocalConnection[] array, AtomicInteger roundRobinCounter) {
			this.array = array;
			this.roundRobinCounter = roundRobinCounter;
			this.size = array.length;
		}

		@Nullable
		public LocalConnection getNext() {
			if (size == 0) {
				return null;
			}
			if (size == 1) {
				return array[0];
			}
			int con = roundRobinCounter.getAndUpdate(i -> ++i < size ? i : 0);
			return array[con];
		}

	}

}
