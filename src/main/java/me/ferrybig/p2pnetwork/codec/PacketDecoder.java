/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package me.ferrybig.p2pnetwork.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import me.ferrybig.p2pnetwork.codec.packets.Packet;
import me.ferrybig.p2pnetwork.codec.packets.PingPacket;
import me.ferrybig.p2pnetwork.codec.packets.PongPacket;

/**
 *
 * @author Fernando
 */
public class PacketDecoder extends ByteToMessageDecoder {

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
		int packetId = in.readInt();
		out.add(PacketMap.readPacket(packetId, in));
	}
	
}
