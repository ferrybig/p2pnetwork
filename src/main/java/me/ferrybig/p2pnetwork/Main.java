/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package me.ferrybig.p2pnetwork;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import me.ferrybig.p2pnetwork.codec.PacketDecoder;
import me.ferrybig.p2pnetwork.codec.PacketEncoder;
import me.ferrybig.p2pnetwork.codec.PacketPreProcessor;
import me.ferrybig.p2pnetwork.codec.PacketRoutingHandler;
import me.ferrybig.p2pnetwork.codec.ServerBootstrapConnector;
import me.ferrybig.p2pnetwork.codec.ServerBootstrapConnector.ConnectionListener;
import me.ferrybig.p2pnetwork.codec.packets.ProcessedPacket;
import me.ferrybig.p2pnetwork.codec.packets.RelayPacket;

/**
 *
 * @author Fernando
 */
public class Main {

	public final List<ServerChannel> servers = Collections.synchronizedList(new ArrayList<>());
	public final List<Channel> clientsOut = Collections.synchronizedList(new ArrayList<>());
	public final List<Channel> clientsIn = Collections.synchronizedList(new ArrayList<>());

	public final ConcurrentMap<InetAddress, Boolean> blocked = new ConcurrentHashMap<>();
	public final ConcurrentMap<Channel, LocalConnection> connections = new ConcurrentHashMap<>();
	public final ConcurrentMap<Address, LocalConnection> localConnectionMap = new ConcurrentHashMap<>();
	public final ConcurrentMap<Address, SortedMap<Byte, List<Address>>> routingTable = new ConcurrentHashMap<>();
	public final Address addr = Address.random();
	private EventLoopGroup group;
	private static final Logger LOG = Logger.getLogger(Main.class.getName());
	private final ConnectionListener incomingListener = new ConnectionListenerImpl(true);
	private final ConnectionListener outgoingListener = new ConnectionListenerImpl(false);

	public boolean routePacket(RelayPacket packet) {
		Address to = packet.getAddress();
		SortedMap<Byte, List<Address>> res = routingTable.get(to);
		if (res == null) {
			return false;
		}
		synchronized (res) {
			Iterator<List<Address>> fullAddrList = res.values().iterator();
			while (fullAddrList.hasNext()) {
				Iterator<Address> addrList = fullAddrList.next().iterator();
				while (addrList.hasNext()) {
					Address via = addrList.next();
					LocalConnection router = localConnectionMap.get(via);
					if (router != null) {
						LOG.log(Level.INFO, "Routing packet {0} to {1} via {2}", new Object[]{packet, to, via});
						router.sendPacket(packet);
						return true;
					}
					LOG.log(Level.INFO, "Failed to route packet {0} to {1} via {2}: no connection", new Object[]{packet, to, via});
					addrList.remove(); // Invalid router addr? REMOVE IT
				}
				fullAddrList.remove();
			}
			routingTable.remove(to, res);
		}
		return false;
	}

	public void addRoutingAddress(Address via, Address to, byte priority) {
		if (to.equals(addr) || via.equals(to)) {
			return;
		}
		SortedMap<Byte, List<Address>> res = routingTable.compute(to, (k, v) -> {
			if (v == null) {
				v = new TreeMap<>();
			}
			return v;
		});
		synchronized (res) {
			List<Address> addrList = res.get(priority);
			if (addrList == null) {
				res.put(priority, addrList = new ArrayList<>());
			}
			addrList.add(via);
		}
	}

	public void removeRoutingAddress(Address via, Address to, byte priority) {
		SortedMap<Byte, List<Address>> res = routingTable.get(to);
		if (res == null) {
			return;
		}
		synchronized (res) {
			List<Address> addrList = res.get(priority);
			if (addrList == null) {
				return;
			}
			if (addrList.size() == 1) {
				routingTable.remove(to, res);
			} else {
				addrList.remove(via);
			}
		}
	}

	public static void main(String... arguments) throws InterruptedException {

		Main main = new Main();
		main.startThreads();
		main.startIncomingConnectionThread(5000);
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
						LOG.info("My address: " + main.addr);
					}
					break;
				}
			} catch (Exception e) {
				LOG.log(Level.WARNING, "Exception caugth:", e);
			}
		}
		main.group.shutdownGracefully();
		LOG.info("Shutting down...");
		main.group.awaitTermination(1, TimeUnit.MINUTES);
		LOG.info("Shutdown!");
	}

	private void startIncomingConnectionThread(int port) {
		ServerBootstrap server = new ServerBootstrap();
		server.group(group);
		server.channel(NioServerSocketChannel.class);
		server.option(ChannelOption.SO_BACKLOG, 128);
		server.childOption(ChannelOption.SO_KEEPALIVE, true);
		server.childHandler(new ChannelInitializer<SocketChannel>() {
			@Override
			protected void initChannel(SocketChannel ch) throws Exception {
				if (blocked.containsKey(((InetSocketAddress) ch.remoteAddress()).getAddress())) {
					LOG.info(ch + "Rejected at socket level");
					ch.close();
					return;
				}
				ch.pipeline().addLast(new LengthFieldPrepender(4));
				ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4));
				ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
				ch.pipeline().addLast(new PacketEncoder());
				ch.pipeline().addLast(new PacketDecoder());
				ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
				ch.pipeline().addLast(new ServerBootstrapConnector(addr, incomingListener));
				clientsIn.add(ch);
				ch.closeFuture().addListener(e1 -> {
					clientsIn.remove(ch);
				});
			}
		});
		ChannelFuture f = server.bind(port);
		f.addListener(e -> {
			this.servers.add((ServerChannel) f.channel());
			f.channel().closeFuture().addListener(e1 -> {
				this.servers.remove((ServerChannel) f.channel());
			});
		});
	}

	private void startOutgomingConnectionThread(InetAddress address, int port) {
		Bootstrap client = new Bootstrap();
		client.group(group);
		client.channel(NioSocketChannel.class);
		client.option(ChannelOption.SO_KEEPALIVE, true);
		client.handler(new ChannelInitializer<SocketChannel>() {
			@Override
			protected void initChannel(SocketChannel ch) throws Exception {
				ch.pipeline().addLast(new LengthFieldPrepender(4));
				ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4));
				ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
				ch.pipeline().addLast(new PacketEncoder());
				ch.pipeline().addLast(new PacketDecoder());
				ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
				ch.pipeline().addLast(new ServerBootstrapConnector(addr, outgoingListener));
			}
		});
		ChannelFuture f = client.connect(address, port);
		f.addListener(e -> {
			this.clientsOut.add(f.channel());
			f.channel().closeFuture().addListener(e1 -> {
				this.clientsOut.remove(f.channel());
			});
		});
	}

	private void startThreads() {
		group = new NioEventLoopGroup();
	}

	private class ConnectionListenerImpl implements ConnectionListener {

		private final boolean incoming;

		private ConnectionListenerImpl(boolean incoming) {
			this.incoming = incoming;

		}

		@Override
		public List<Address> getKnownAddresses() {
			return new ArrayList<>(routingTable.keySet());
		}

		@Override
		public void onConnectionPrepare(Channel chn) {
			LOG.log(Level.INFO, "{0} Setting up new connection", chn);
		}

		@Override
		public void onConnectionOpen(Channel chn) {
			LOG.log(Level.INFO, "{0} Opening new connection", chn);
		}

		@Override
		public void onConnectionReady(Channel chn, Address remote, int linkQuality, List<Address> knownRemote) {

			LOG.log(Level.INFO, "{0} Opened connection to {1}", new Object[]{chn, remote});
			LocalConnection con = new LocalConnection(!incoming, remote, addr, chn, knownRemote);
			connections.put(chn, con);
			LocalConnection old = localConnectionMap.put(remote, con);
			if (old != null) {
				old.close();
			}
			for (Address learned : knownRemote) {
				addRoutingAddress(remote, learned, (byte)255);
			}
			chn.pipeline().addLast(new PacketRoutingHandler(addr, null, Main.this::routePacket));
			chn.pipeline().addLast(new PacketPreProcessor(addr));
			chn.pipeline().addLast(new SimpleChannelInboundHandler<ProcessedPacket>() {
				@Override
				protected void channelRead0(ChannelHandlerContext ctx, ProcessedPacket msg) throws Exception {
					try {
						System.out.println("Received a " + msg);
					} finally {
						msg.getPacket().release();
					}
				}
			});
		}

		@Override
		public void onConnectionException(Channel chn, Throwable ex) {
			LogRecord lr = new LogRecord(Level.WARNING, "{0} Error detected:");
			lr.setParameters(new Object[]{chn});
			lr.setThrown(ex);
			LOG.log(lr);
		}

		@Override
		public void onConnectionFail(Channel chn) {
			LOG.log(Level.INFO, "{0} failed", chn);
			LocalConnection con = connections.get(chn);
			localConnectionMap.remove(con.getDirectNode(), con);
		}

		@Override
		public void onConnectionReject(Channel chn) {
			LOG.log(Level.INFO, "{0} rejected", chn);
			LocalConnection con = connections.get(chn);
			localConnectionMap.remove(con.getDirectNode(), con);
			blocked.put(((InetSocketAddress) chn.remoteAddress()).getAddress(), true);
		}
	}

}
