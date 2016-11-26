/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package me.ferrybig.p2pnetwork;

import java.net.SocketAddress;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 * @author Fernando van Loenhout
 */
public class PeerMap {

	private static final byte UNSIGNED_BYTE_MAX = (byte) 255;
	private final int maxSize = 255;
	private final int minSize = 128;
	private Map<SocketAddress, Byte> map = Collections.emptyMap();
	private volatile Map<SocketAddress, Byte> unmodMap = Collections.emptyMap();

	public Map<SocketAddress, Byte> getMap() {
		return unmodMap;
	}

	private void syncMap() {
		assert Thread.holdsLock(this);
		unmodMap = Collections.unmodifiableMap(new LinkedHashMap<>(map));
	}

	public void incrementCounters(Set<SocketAddress> connected) {
		synchronized (this) {
			int size = map.size();
			for (Iterator<Map.Entry<SocketAddress, Byte>> it = map.entrySet().iterator(); it.hasNext();) {
				Map.Entry<SocketAddress, Byte> b = it.next();
				if (b.getValue() == UNSIGNED_BYTE_MAX) {
					if (size > minSize) { // Don't go on rampage mode if this network loses all internet communications
						it.remove();
					}
				} else {
					b.setValue((byte) (b.getValue() + 1));
				}
			}
			syncMap();
		}
	}

	public void merge(Map<SocketAddress, Byte> other) {
		synchronized (this) {
			Stream<Map.Entry<SocketAddress, Byte>> baseStream = Stream.concat(
				map.entrySet().stream(),
				other.entrySet().stream().filter(e -> e.getValue() != -1).peek(e -> e.setValue((byte) (e.getValue() + 1))));
			if (map.size() + other.size() > maxSize) {
				baseStream = baseStream.sorted(Comparator.comparing(Map.Entry::getValue)).limit(maxSize);
			}
			map = baseStream.collect(Collectors.toMap(
				Map.Entry::getKey,
				Map.Entry::getValue,
				(a, b) -> a > b ? b : a,
				LinkedHashMap::new));
			syncMap();
		}
	}
}
