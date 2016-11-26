/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package me.ferrybig.p2pnetwork.codec.packets;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import me.ferrybig.p2pnetwork.Address;

/**
 *
 * @author Fernando
 */
public class ProcessedPacket {

	private final Address from;
	private final Channel channel;
	private final Packet packet;
	private final Function<Packet, ChannelFuture> reply;

	public ProcessedPacket(Address from, Channel channel, Packet packet, Function<Packet, ChannelFuture> reply) {
		this.from = from;
		this.channel = channel;
		this.packet = packet;
		this.reply = reply;
	}

	public Address getFrom() {
		return from;
	}

	public Packet getPacket() {
		return packet;
	}

	public Function<Packet, ChannelFuture> getReply() {
		return reply;
	}

	public Channel getChannel() {
		return channel;
	}
	
	public ChannelFuture respond(Packet response) {
		return this.reply.apply(response);
	} 

	@Override
	public String toString() {
		return "ProcessedPacket{" + "from=" + from + ", packet=" + packet + '}';
	}
	
	
}
