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
public class PingPacket extends Packet {
	
	private final byte[] data;
	
	public PingPacket(ByteBuf buf) {
		data = new byte[buf.readShort()];
		buf.readBytes(data);
	}
	
	public PingPacket(byte[] data) {
		this.data = data;
	}

	

	@Override
	public void write(ByteBuf buf) {
		buf.writeShort(data.length);
		buf.writeBytes(data);
	}

	public byte[] getData() {
		return data;
	}

	@Override
	public String toString() {
		return "PingPacket{" + Arrays.toString(data) + '}';
	}
	
	
	
}
