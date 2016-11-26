/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package me.ferrybig.p2pnetwork.codec;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.util.List;
import me.ferrybig.p2pnetwork.Address;
import me.ferrybig.p2pnetwork.codec.packets.InitialPacket;

/**
 *
 * @author Fernando
 */
public class ServerBootstrapConnector extends SimpleChannelInboundHandler<InitialPacket> {

	private final Address ourself;
	private final ConnectionListener listener;
	private State state = State.INITIAL;

	public ServerBootstrapConnector(Address ourself, ConnectionListener listener) {
		this.ourself = ourself;
		this.listener = listener;
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		//super.exceptionCaught(ctx, cause);
		ctx.close();
		if(state != State.FAILED) {
			listener.onConnectionException(ctx.channel(), cause);
			state = State.FAILED;
			listener.onConnectionFail(ctx.channel());
		}
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		super.channelInactive(ctx);
		if(state != State.FAILED) {
			state = State.FAILED;
			listener.onConnectionFail(ctx.channel());
		}
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		super.channelActive(ctx);
		if(state == State.CONNECTED) {
			ctx.writeAndFlush(new InitialPacket(ourself, (byte)0, listener.getKnownAddresses()));
			listener.onConnectionOpen(ctx.channel());
			state = State.OPENED;
		}
	}

	@Override
	public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
		super.channelRegistered(ctx);
		if(state == State.INITIAL) {
			listener.onConnectionPrepare(ctx.channel());
			state = State.CONNECTED;
		}
		if(ctx.channel().isActive()) {
			if(state == State.CONNECTED) {
				ctx.writeAndFlush(new InitialPacket(ourself, (byte)0, listener.getKnownAddresses()));
				listener.onConnectionOpen(ctx.channel());
				state = State.OPENED;
			}
		}
	}
	
	
	
	@Override
	protected void channelRead0(ChannelHandlerContext ctx, InitialPacket msg) throws Exception {
		if(state == State.OPENED) {
			if(msg.getAddress().equals(this.ourself)) {
				state = State.FAILED;
				listener.onConnectionReject(ctx.channel());
				ctx.close();
				return;
			}
			listener.onConnectionReady(ctx.channel(), msg.getAddress(), msg.getLinkQuality(), msg.getKnownAddresses());
			state = State.READY;
		} else if(state != State.FAILED) { 
			// Dublicate Inital packets not accepted
			state = State.FAILED;
			listener.onConnectionReject(ctx.channel());
			ctx.close();
		}
	}
	
	public interface ConnectionListener {
		public List<Address> getKnownAddresses();
		
		public void onConnectionPrepare(Channel chn); // Connection is object is created
		
		public void onConnectionOpen(Channel chn); // Bootstrap packets send
		
		public void onConnectionReady(Channel chn, Address remote, int linkQuality, List<Address> knownRemote);
		
		public void onConnectionException(Channel chn, Throwable ex);
		
		public void onConnectionFail(Channel chn); // fail is usually timeout
		
		public void onConnectionReject(Channel chn); // Reject is address mismatch
	}
	
	public enum State {
		INITIAL,
		CONNECTED,
		OPENED,
		READY,
		FAILED,
	}
}
