/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package me.ferrybig.p2pnetwork;

import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import me.ferrybig.p2pnetwork.codec.packets.PingPacket;

/**
 *
 * @author Fernando
 */
public class Test {

	private static final Logger LOG = Logger.getLogger(Test.class.getName());

	public static void main(String... arguments) throws InterruptedException {
		EventLoopGroup group = new NioEventLoopGroup();
		try {
			final Peer main = new Peer(group, true);
			//main.startIncomingConnectionThread(5000);
			Scanner in = new Scanner(System.in);
			mainLoop:
			while (in.hasNextLine()) {

				String[] args = in.nextLine().split(" ");
				try {
					switch (args[0]) {
						case "exit":
							break mainLoop;
						case "connect": {
							InetAddress to = InetAddress.getByName(args[1]);
							int port = Integer.parseInt(args[2]);
							main.startOutgomingConnectionThread(to, port);
						}
						break;
						case "listen": {
							int port = Integer.parseInt(args[1]);
							main.startIncomingConnectionThread(port);
						}
						break;
						case "myaddr": {
							LOG.log(Level.INFO, "My address: {0}", main.getAddress());
						}
						break;

						case "connections": {
							synchronized (main.servers) {
								for (ServerChannel s : main.servers) {
									LOG.log(Level.INFO, "Server channel: {0}", s);
								}
							}
							synchronized (main.clientsIn) {
								for (Channel s : main.clientsIn) {
									LOG.log(Level.INFO, "Incoming channel: {0}", s);
								}
							}
							synchronized (main.clientsOut) {
								for (Channel s : main.clientsOut) {
									LOG.log(Level.INFO, "Outgoing channel: {0}", s);
								}
							}
						}
						break;
						case "routing": {
							for (Map.Entry<Address, Byte> routes
									: main.routingTable.generateDelegatedRoutingMap().entrySet()) {
								LOG.log(Level.INFO, "Routes to {0}: {1}",
										new Object[]{routes.getKey(), routes.getValue()});
							}
						}
						break;
						case "send": {
							Address to = new Address(javax.xml.bind.DatatypeConverter.parseHexBinary(args[1]));
							LOG.log(Level.INFO, "Sending to: {0}", to);
							byte[] message = args[2].getBytes();
							if(!main.routePacket(to, new PingPacket(message))) {
								LOG.log(Level.INFO, "Failed sending to: {0}", to);
							}
						}
						break;
						case "trace": {
							Address to = new Address(javax.xml.bind.DatatypeConverter.parseHexBinary(args[1]));
							LOG.log(Level.INFO, "Sending to: {0}", to);
							for(int i = 0; i < 10; i++) {
								if(!main.routePacket(to, new PingPacket(new byte[10]), (byte)i)) {
									LOG.log(Level.INFO, "Failed sending to: {0}", to);
								}
							}
						}
						break;
						case "ip": {
							Set<Integer> ports;
							synchronized (main.servers) {
								ports = main.servers.stream()
										.map(s -> ((InetSocketAddress) s.localAddress()).getPort())
										.collect(Collectors.toSet());
							}
							Set<InetAddress> addresses = new HashSet<>();
							for (NetworkInterface intf : Collections.list(NetworkInterface.getNetworkInterfaces())) {
								for (InetAddress inetAddress : Collections.list(intf.getInetAddresses())) {
									if (inetAddress instanceof Inet6Address) {
										Inet6Address inet6Address = (Inet6Address) inetAddress;
										if (inet6Address.isMulticastAddress()) {
											continue;
										}
										if (inet6Address.isLinkLocalAddress()) {
											continue;
										}
										if (inet6Address.isIPv4CompatibleAddress()) {
											continue;
										}
									} else if (inetAddress instanceof Inet4Address) {

										Inet4Address inet4Address = (Inet4Address) inetAddress;
										if (inet4Address.isLoopbackAddress() || inet4Address.isLinkLocalAddress()) {
											continue;
										}

									} else {
										continue;
									}
									addresses.add(inetAddress);
								}
							}
							addresses.stream()
									.flatMap(a -> ports.stream().map(p -> new InetSocketAddress(a, p)))
									.forEach(s -> LOG.log(Level.INFO, "Known ip: {0}", s));
						}
						break;
						default: {
							LOG.log(Level.WARNING, "Unknown command: " + args[0] );
						}
						break;
					}
				} catch (Exception e) {
					LOG.log(Level.WARNING, "Exception caugth:", e);
				}
			}
		} finally {
			group.shutdownGracefully();
			LOG.info("Shutting down...");
			group.awaitTermination(1, TimeUnit.MINUTES);
			LOG.info("Shutdown!");
		}
	}
}
