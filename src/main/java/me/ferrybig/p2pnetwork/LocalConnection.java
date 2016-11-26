/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package me.ferrybig.p2pnetwork;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.concurrent.Promise;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import me.ferrybig.p2pnetwork.codec.PacketMap;
import me.ferrybig.p2pnetwork.codec.packets.ExitPacket;
import me.ferrybig.p2pnetwork.codec.packets.Packet;
import me.ferrybig.p2pnetwork.codec.packets.RelayPacket;

/**
 *
 * @author Fernando
 */
public class LocalConnection  {
	private final Address directNode;
	private final Address ourAddress;
	private volatile Map<Address, Byte> knownDestinations = new HashMap<>();
	private final Channel channel;
	private final boolean outgoing;
	private final List<Consumer<Map<Address, Byte>>> routingChangedListeners = new CopyOnWriteArrayList<>();
	private final Promise<Void> closePromise;

	public LocalConnection(boolean outgoing, Address directNode, Address ourAddress, Channel channel, List<Address> known) {
		this.outgoing = outgoing;
		this.directNode = directNode;
		this.ourAddress = ourAddress;
		this.channel = channel;
		this.closePromise = channel.eventLoop().newPromise();
		channel.closeFuture().addListener(e -> closePromise.trySuccess(null));
		for(Address a : known) {
			knownDestinations.put(a, (byte)255);
		}
	}
	
	public void close() {
		this.channel.close();
	}
	
	public void close(CloseReason reason, boolean prepare) {
		ChannelFuture f = this.channel.write(new ExitPacket(reason, prepare));
		if(!prepare)
			f.addListener(ChannelFutureListener.CLOSE);
	}
	
	public ChannelFuture sendPacket(Address destination, Packet packet) {
		if(!destination.equals(directNode)) {
			RelayPacket r;
			ByteBuf buf = this.channel.alloc().buffer();
			try {
				buf.writeInt(PacketMap.getPacketId(packet));
				packet.write(buf);
				r = new RelayPacket(buf.retain(), ourAddress, ourAddress, 255);
			} finally {
				packet.release();
				buf.release();
			}
			packet = r;
		}
		return sendPacket(packet);
	}

	public boolean addRoutingChangedListener(Consumer<Map<Address, Byte>> e) {
		return routingChangedListeners.add(e);
	}

	public boolean removeRoutingChangedListener(Consumer<Map<Address, Byte>> o) {
		return routingChangedListeners.remove(o);
	}
	
	public ChannelFuture sendPacket(Packet packet) {
		return this.channel.writeAndFlush(packet);
	}

	public Address getDirectNode() {
		return directNode;
	}

	public Channel getChannel() {
		return channel;
	}

	public boolean isOutgoing() {
		return outgoing;
	}

	public Map<Address, Byte> getKnownDestinations() {
		return knownDestinations;
	}
	
	public void updateKnownDestinations(Map<Address, Byte> routingMap) {
		// routingMap.keySet().remove(directNode);
		// TODO: only fire event after actual changes
		for(Consumer<Map<Address, Byte>> r : this.routingChangedListeners) {
			r.accept(routingMap);
		}
	}
	
	public enum CloseReason {
		NOT_READY,
		SEE_OTHER_CONNECTION,
		ADDRESS_CONFLICT,
	}
	
}
