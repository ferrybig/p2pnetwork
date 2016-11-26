/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package me.ferrybig.p2pnetwork.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.util.function.Function;
import me.ferrybig.p2pnetwork.Address;
import me.ferrybig.p2pnetwork.RoutingMap;
import me.ferrybig.p2pnetwork.codec.packets.DestinationUnreachablePacket;
import me.ferrybig.p2pnetwork.codec.packets.Packet;
import me.ferrybig.p2pnetwork.codec.packets.RelayPacket;

/**
 *
 * @author Fernando
 */
public class PacketRoutingHandler extends SimpleChannelInboundHandler<RelayPacket> {

	private final Address ourself;
	private final RoutingMap routing;
	private final Function<RelayPacket, Boolean> router;

	public PacketRoutingHandler(Address ourself, RoutingMap routing, Function<RelayPacket, Boolean> router) {
		this.ourself = ourself;
		this.routing = routing;
		this.router = router;
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, RelayPacket msg) throws Exception {
		if (msg.getAddress().equals(ourself)) {
			ctx.fireChannelRead(msg.retain());
			return;
		}
		if (msg.getTtl() == 0) {
			DestinationUnreachablePacket result = new DestinationUnreachablePacket(
					DestinationUnreachablePacket.Reason.TTL_EXPIRED, msg.getAddress());
			try {

				ByteBuf buf = ctx.alloc().buffer();
				buf.writeInt(PacketMap.getPacketId(result));
				result.write(buf);
				ctx.writeAndFlush(new RelayPacket(buf, ourself, msg.getFrom(), 127));
			} finally {
				result.release();
			}
			return;
		}
		msg.decrementTTL();
		if (!router.apply(msg)) {
			DestinationUnreachablePacket result = new DestinationUnreachablePacket(
					DestinationUnreachablePacket.Reason.DESTINATION_HOST_UNREACHABLE, msg.getAddress());
			try {
				ByteBuf buf = ctx.alloc().buffer();
				result.write(buf);
				ctx.write(buf);
				ctx.writeAndFlush(new RelayPacket(buf));
			} finally {
				result.release();
			}
		}
	}

}
