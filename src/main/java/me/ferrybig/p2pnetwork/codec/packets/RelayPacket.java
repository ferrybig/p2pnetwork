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
public class RelayPacket extends Packet {

	private final ByteBuf data;
	private final Address from;
	private final Address address;
	private int ttl;

	public RelayPacket(ByteBuf data, Address from, Address address, int ttl) {
		this.data = data;
		this.from = from;
		this.address = address;
		this.ttl = ttl;
	}

	public RelayPacket(ByteBuf buf) {
		from = new Address(buf);
		address = new Address(buf);
		ttl = buf.readByte();
		data = buf.readBytes(buf.readInt());
	}

	public void decrementTTL() {
		ttl--;
	}

	@Override
	public void write(ByteBuf buf) {
		from.write(buf);
		address.write(buf);
		buf.writeByte(ttl);
		buf.writeInt(data.readableBytes());
		buf.writeBytes(data, data.readerIndex(), data.readableBytes());
	}

	public ByteBuf getData() {
		return data;
	}

	public Address getFrom() {
		return from;
	}

	public Address getAddress() {
		return address;
	}

	public int getTtl() {
		return ttl;
	}

	public void setTtl(int ttl) {
		this.ttl = ttl;
	}

	@Override
	protected void deallocate() {
		super.deallocate();
		data.release();
	}

	@Override
	public String toString() {
		return "RelayPacket{" + "data=" + data + ", from=" + from + ", address=" + address + ", ttl=" + ttl + '}';
	}

}
