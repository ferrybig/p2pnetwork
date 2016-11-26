/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package me.ferrybig.p2pnetwork.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.util.function.Function;
import java.util.logging.Logger;
import me.ferrybig.p2pnetwork.Address;
import me.ferrybig.p2pnetwork.codec.packets.Packet;
import me.ferrybig.p2pnetwork.codec.packets.ProcessedPacket;
import me.ferrybig.p2pnetwork.codec.packets.RelayPacket;

/**
 *
 * @author Fernando
 */
public class PacketPreProcessor extends SimpleChannelInboundHandler<Packet> {

	private final Address connection;
	
	public PacketPreProcessor(Address connection) {
		super(false);
		this.connection = connection;
	}
	 
	@Override
	protected void channelRead0(ChannelHandlerContext ctx, Packet msg) throws Exception {
		try {
			Address from;
			Function<Packet, ChannelFuture> reply;
			if(msg instanceof RelayPacket) {
				RelayPacket relayPacket = (RelayPacket) msg;
				int packetId = relayPacket.getData().readInt();
				from = relayPacket.getFrom();
				msg = PacketMap.readPacket(packetId, relayPacket.getData());
				relayPacket.release(); // This is a safe release
				reply = p -> {
					ByteBuf wrapped = ctx.alloc().buffer();
					try {
						wrapped.writeInt(PacketMap.getPacketId(p));
						p.write(wrapped);
						return ctx.writeAndFlush(new RelayPacket(wrapped.retain(), relayPacket.getAddress(), from, 255));
					} finally {
						p.release();
						wrapped.release();
					}
				};
			} else {
				from = connection;
				reply = ctx::writeAndFlush;
			}
			ctx.fireChannelRead(new ProcessedPacket(from, ctx.channel(), (Packet)msg.retain(), reply));
		} finally {
			msg.release();
		}
	}
	private static final Logger LOG = Logger.getLogger(PacketPreProcessor.class.getName());
	
}
