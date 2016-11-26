/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package me.ferrybig.p2pnetwork.codec.packets;

import io.netty.buffer.ByteBuf;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author admin
 */
public class PeerExchangePacket extends Packet {

	private final Map<SocketAddress, Byte> addresses;
	private static final Logger LOG = Logger.getLogger(PeerExchangePacket.class.getName());

	public PeerExchangePacket(ByteBuf buf) {
		addresses = new LinkedHashMap<>();
		int size = buf.readByte();
		for (int i = 0; i < size; i++) {
			try {
				byte aType = buf.readByte();
				switch (aType) {
					case 1: {
						int addrLength = buf.readByte();
						byte[] addr = new byte[addrLength];
						buf.readBytes(addr);
						addresses.put(new InetSocketAddress(InetAddress.getByAddress(addr), buf.readShort()), buf.readByte());
					}
					break;
				}
			} catch (UnknownHostException ex) {
				LOG.log(Level.WARNING, "Peer exchange packet contained invalid ip address", ex);
			}
		}
	}

	public PeerExchangePacket(Map<SocketAddress, Byte> addresses) {
		this.addresses = addresses;
	}

	@Override
	public void write(ByteBuf buf) {
		buf.writeByte(addresses.size());
		for (Map.Entry<SocketAddress, Byte> entry : addresses.entrySet()) {
			if (entry.getKey() instanceof InetSocketAddress) {
				InetSocketAddress inetSocketAddress = (InetSocketAddress) entry.getKey();
				buf.writeByte(1); // Inet address
				byte[] address = inetSocketAddress.getAddress().getAddress();
				buf.writeInt(address.length);
				buf.writeBytes(address);
				buf.writeShort(inetSocketAddress.getPort());
			} else {
				buf.writeByte(0); // Unknown address
			}
			buf.writeByte(entry.getValue());
		}
	}

}
