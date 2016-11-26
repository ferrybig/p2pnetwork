/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package me.ferrybig.p2pnetwork.codec.packets;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import me.ferrybig.p2pnetwork.Address;

/**
 *
 * @author Fernando
 */
public class InitialPacket extends Packet {

	private final Address address;
	private final byte linkQuality;
	private final List<Address> knownAddresses;

	public InitialPacket(ByteBuf buf) {
		address = new Address(buf);
		linkQuality = buf.readByte();
		int size = buf.readInt();
		knownAddresses = new ArrayList<>(size);
		for(int i = 0; i < size; i++) {
			knownAddresses.add(new Address(buf));
		}
	}
	
	public InitialPacket(Address address, byte linkQuality, List<Address> knownAddresses) {
		this.address = address;
		this.linkQuality = linkQuality;
		this.knownAddresses = knownAddresses;
	}
	
	@Override
	public void write(ByteBuf buf) {
		address.write(buf);
		buf.writeByte(linkQuality);
		buf.writeInt(knownAddresses.size());
		for(Address addr : knownAddresses) {
			addr.write(buf);
		}
	}

	public Address getAddress() {
		return address;
	}

	public byte getLinkQuality() {
		return linkQuality;
	}

	public List<Address> getKnownAddresses() {
		return knownAddresses;
	}

	@Override
	public String toString() {
		return "InitialPacket{" + "address=" + address + ", linkQuality=" + linkQuality + ", knownAddresses=" + knownAddresses + '}';
	}
	
}
