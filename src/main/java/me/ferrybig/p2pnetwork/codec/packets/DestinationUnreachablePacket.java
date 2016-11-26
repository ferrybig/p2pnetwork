/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package me.ferrybig.p2pnetwork.codec.packets;

import io.netty.buffer.ByteBuf;
import me.ferrybig.p2pnetwork.Address;

/**
 *
 * @author Fernando
 */
public class DestinationUnreachablePacket extends Packet {
	private final Reason reason;
	private final Address problemAddress;

	public DestinationUnreachablePacket(Reason reason, Address problemAddress) {
		this.reason = reason;
		this.problemAddress = problemAddress;
	}

	public DestinationUnreachablePacket(ByteBuf buf) {
		this.reason = CACHED_REASON_VALUES[buf.readByte()];
		this.problemAddress = new Address(buf);
	}
	
	@Override
	public void write(ByteBuf buf) {
		buf.writeByte(reason.ordinal());
		problemAddress.write(buf);
	}
	/**
	 * values() makes a new array every time, this speeds up the frequent access to this array
	 */
	private static final Reason[] CACHED_REASON_VALUES = Reason.values();
	
	public enum Reason {
		TTL_EXPIRED, DESTINATION_HOST_UNREACHABLE, PACKET_TYPE_UNKNOWN, ROUTING_LOOP_DETECTED
	}
}
