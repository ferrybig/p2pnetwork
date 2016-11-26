/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package me.ferrybig.p2pnetwork.codec;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.ferrybig.p2pnetwork.LocalConnection;
import me.ferrybig.p2pnetwork.codec.packets.ExitPacket;
import me.ferrybig.p2pnetwork.codec.packets.PingPacket;
import me.ferrybig.p2pnetwork.codec.packets.PongPacket;
import me.ferrybig.p2pnetwork.codec.packets.ProcessedPacket;
import me.ferrybig.p2pnetwork.codec.packets.RoutingUpdatePacket;

/**
 *
 * @author Fernando
 */
public class PacketHandler extends SimpleChannelInboundHandler<ProcessedPacket> {

	private final LocalConnection con;

	private final Consumer<ProcessedPacket> packetHandler;
	private static final Logger LOG = Logger.getLogger(PacketHandler.class.getName());

	public PacketHandler(LocalConnection con, Consumer<ProcessedPacket> packetHandler) {
		super(false);
		this.con = con;
		this.packetHandler = packetHandler;
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, ProcessedPacket msg) throws Exception {
		try {
			if (msg.getPacket() instanceof PingPacket) {
				PingPacket pingPacket = (PingPacket) msg.getPacket();
				msg.respond(new PongPacket(pingPacket.getData()));
			} else if (msg.getPacket() instanceof RoutingUpdatePacket) {
				RoutingUpdatePacket routingUpdatePacket = (RoutingUpdatePacket) msg.getPacket();
				if (!msg.getFrom().equals(con.getDirectNode())) {
					LOG.log(Level.WARNING,
						"Dropped wrong routing packet coming from node {0} but packet states it comes from {1}",
						new Object[]{con.getDirectNode(), msg.getFrom()});
					return;
				}
				con.updateKnownDestinations(routingUpdatePacket.getRoutes());
			} else {
				if (msg.getPacket() instanceof ExitPacket) {
					ExitPacket exitPacket = (ExitPacket) msg.getPacket();
					if(exitPacket.isPreparing()) {
						msg.respond(new ExitPacket(exitPacket.getReason(), false))
							.addListener(ChannelFutureListener.CLOSE);
					} else {
						ctx.close(); 
					}
				}
				packetHandler.accept(msg);
			}
		} finally {
			msg.getPacket().release();
		}
	}

}
