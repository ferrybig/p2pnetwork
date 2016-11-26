/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package me.ferrybig.p2pnetwork.codec.packets;

import io.netty.buffer.ByteBuf;
import java.util.Arrays;

/**
 *
 * @author Fernando
 */
public class PongPacket extends Packet {
	
	private final byte[] data;

	@Override
	public int hashCode() {
		int hash = 3;
		hash = 53 * hash + Arrays.hashCode(this.data);
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
		final PongPacket other = (PongPacket) obj;
		if (!Arrays.equals(this.data, other.data)) {
			return false;
		}
		return true;
	}
	
	public PongPacket(ByteBuf buf) {
		data = new byte[buf.readShort()];
		buf.readBytes(data);
	}
	
	public PongPacket(byte[] data) {
		this.data = data;
	}

	@Override
	public void write(ByteBuf buf) {
		buf.writeShort(data.length);
		buf.writeBytes(data);
	}

	@Override
	public String toString() {
		return "PongPacket{" + Arrays.toString(data) + '}';
	}
	
}
