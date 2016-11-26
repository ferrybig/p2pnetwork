/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package me.ferrybig.p2pnetwork.codec.packets;

import io.netty.buffer.ByteBuf;
import java.util.Objects;
import me.ferrybig.p2pnetwork.LocalConnection.CloseReason;

/**
 *
 * @author Fernando
 */
public class ExitPacket extends Packet {

	private static final CloseReason[] CACHED_CLOSE_REASON_VALUES = CloseReason.values();
	
	private final CloseReason reason;
	
	private final boolean preparing;

	public ExitPacket(CloseReason reason, boolean preparing) {
		this.reason = reason;
		this.preparing = preparing;
	}
	
	public ExitPacket(ByteBuf buf) {
		final byte data = buf.readByte();
		this.reason = CACHED_CLOSE_REASON_VALUES[data & 0x7f];
		this.preparing = (data & 0x80) == 0x80;
	}
	
	@Override
	public void write(ByteBuf buf) {
		buf.writeByte(this.reason.ordinal() | (preparing ? 0x80 : 0));
	}

	public CloseReason getReason() {
		return reason;
	}

	public boolean isPreparing() {
		return preparing;
	}

	@Override
	public String toString() {
		return "ExitPacket{" + "reason=" + reason + '}';
	}

	@Override
	public int hashCode() {
		int hash = 7;
		hash = 29 * hash + Objects.hashCode(this.reason);
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final ExitPacket other = (ExitPacket) obj;
		if (this.reason != other.reason) {
			return false;
		}
		return true;
	}
	
	static {
		assert CACHED_CLOSE_REASON_VALUES.length < 128; // It should fit into 7 bits for this packet
	}
	
}
