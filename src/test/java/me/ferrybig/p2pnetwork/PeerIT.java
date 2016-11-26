/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package me.ferrybig.p2pnetwork;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import me.ferrybig.p2pnetwork.codec.packets.PingPacket;
import org.junit.Assert;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author admin
 */
public class PeerIT {
	
	public PeerIT() {
	}

	@Test
	public void testNetwork() throws InterruptedException, UnknownHostException {
		EventLoopGroup group = new NioEventLoopGroup();
		try {
			Peer first = new Peer(group, false);
			Peer second = new Peer(group, false);
			Peer third = new Peer(group, false);
			first.startIncomingConnectionThread(5000).sync();
			second.startIncomingConnectionThread(5001).sync();
			second.startOutgomingConnectionThread(InetAddress.getLoopbackAddress(), 5000).sync();
			third.startOutgomingConnectionThread(InetAddress.getLoopbackAddress(), 5001).sync();
			
			Thread.sleep(1000);
			System.err.println("Routing map:");
			System.err.println("First: " + first.getAddress());
			first.routingTable.generateDelegatedRoutingMap().forEach((a,b) -> System.err.println(a + ": " + b));
			System.err.println("Second:" + second.getAddress());
			second.routingTable.generateDelegatedRoutingMap().forEach((a,b) -> System.err.println(a + ": " + b));
			System.err.println("Third:" + third.getAddress());
			third.routingTable.generateDelegatedRoutingMap().forEach((a,b) -> System.err.println(a + ": " + b));
			System.err.println();
			
			System.err.println("First: ");
			first.localConnectionMap.forEach((a,b) -> System.err.println(a + ": " + b.getKnownDestinations()));
			System.err.println("Second: ");
			second.localConnectionMap.forEach((a,b) -> System.err.println(a + ": " + b.getKnownDestinations()));
			System.err.println("Third: ");
			third.localConnectionMap.forEach((a,b) -> System.err.println(a + ": " + b.getKnownDestinations()));
			
			//Thread.sleep(1000);
			
			System.err.println("Sending to second!!");
			Assert.assertTrue(third.routePacket(second.getAddress(), new PingPacket(new byte[10])));
			Thread.sleep(1000);
			
			System.err.println("Sending to first!!");
			Assert.assertTrue(third.routePacket(first.getAddress(), new PingPacket(new byte[10])));
			Thread.sleep(10000);
			
		} finally {
			group.shutdownGracefully();
		}
	}
	
}
