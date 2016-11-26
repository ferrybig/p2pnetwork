/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package me.ferrybig.p2pnetwork.codec;

import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import me.ferrybig.p2pnetwork.codec.packets.DestinationUnreachablePacket;
import me.ferrybig.p2pnetwork.codec.packets.ExitPacket;
import me.ferrybig.p2pnetwork.codec.packets.InitialPacket;
import me.ferrybig.p2pnetwork.codec.packets.Packet;
import me.ferrybig.p2pnetwork.codec.packets.PingPacket;
import me.ferrybig.p2pnetwork.codec.packets.PongPacket;
import me.ferrybig.p2pnetwork.codec.packets.RelayPacket;
import me.ferrybig.p2pnetwork.codec.packets.RoutingUpdatePacket;

/**
 *
 * @author Fernando
 */
public class PacketMap {
	private static final Map<Integer, Function<ByteBuf, Packet>> packets = new HashMap<>(); 
	private static final Map<Class<? extends Packet>, Integer> reversePackets = new HashMap<>(); 
	static {
		registerPacket(0, InitialPacket.class, InitialPacket::new);
		registerPacket(1, PingPacket.class, PingPacket::new);
		registerPacket(2, PongPacket.class, PongPacket::new);
		registerPacket(3, RelayPacket.class, RelayPacket::new);
		registerPacket(4, DestinationUnreachablePacket.class, DestinationUnreachablePacket::new);
		registerPacket(5, ExitPacket.class, ExitPacket::new);
		registerPacket(6, RoutingUpdatePacket.class, RoutingUpdatePacket::new);
	}
	
	private static void registerPacket(Integer number, Class<? extends Packet> clazz, Function<ByteBuf, Packet> constructor) {
		packets.put(number, constructor);
		reversePackets.put(clazz, number);
	}
	
	public static int getPacketId(Packet packet) {
		return reversePackets.get(packet.getClass());
	}
	
	public static Packet readPacket(int id, ByteBuf buf) {
		return packets.get(id).apply(buf);
	}
}
