/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package me.ferrybig.p2pnetwork;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.ScheduledFuture;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import me.ferrybig.p2pnetwork.codec.PacketDecoder;
import me.ferrybig.p2pnetwork.codec.PacketEncoder;
import me.ferrybig.p2pnetwork.codec.PacketHandler;
import me.ferrybig.p2pnetwork.codec.PacketMap;
import me.ferrybig.p2pnetwork.codec.PacketPreProcessor;
import me.ferrybig.p2pnetwork.codec.PacketRoutingHandler;
import me.ferrybig.p2pnetwork.codec.ServerBootstrapConnector;
import me.ferrybig.p2pnetwork.codec.ServerBootstrapConnector.ConnectionListener;
import me.ferrybig.p2pnetwork.codec.packets.Packet;
import me.ferrybig.p2pnetwork.codec.packets.PingPacket;
import me.ferrybig.p2pnetwork.codec.packets.PongPacket;
import me.ferrybig.p2pnetwork.codec.packets.ProcessedPacket;
import me.ferrybig.p2pnetwork.codec.packets.RelayPacket;
import me.ferrybig.p2pnetwork.codec.packets.RoutingUpdatePacket;

/**
 *
 * @author Fernando
 */
public class Peer {

	private static final Logger LOG = Logger.getLogger(Peer.class.getName());
	private final Address address = Address.random();
	private final ConcurrentMap<InetAddress, Boolean> blocked = new ConcurrentHashMap<>();
	final List<Channel> clientsIn = Collections.synchronizedList(new ArrayList<>());
	final List<Channel> clientsOut = Collections.synchronizedList(new ArrayList<>());
	private final ConcurrentMap<Channel, LocalConnection> connections = new ConcurrentHashMap<>();
	private final EventLoopGroup group;
	private final EventExecutor events;
	private final ConnectionListener incomingListener = new ConnectionListenerImpl(true);
	final ConcurrentMap<Address, MultiConnection> localConnectionMap = new ConcurrentHashMap<>();
	private final ConnectionListener outgoingListener = new ConnectionListenerImpl(false);
	final RoutingMap routingTable = new RoutingMap(address);
	final List<ServerChannel> servers = Collections.synchronizedList(new ArrayList<>());
	private final ConcurrentMap<Integer, Promise<PongPacket>> pingListeners = new ConcurrentHashMap<>();
	private final AtomicInteger pingPacketCounter = new AtomicInteger();
	private final List<SocketAddress> knownAddresses = new CopyOnWriteArrayList<>();
	
	private ScheduledFuture<?> sendRoutingUpdate;
	//private final Map<Packet, Promise<Packet>> packetListeners ;
	private final boolean peerExchange;

	{
		routingTable.addFullUpdateListener(() -> this.scheduleSendRoutingTable(true));
		routingTable.addPartialUpdateListener(() -> this.scheduleSendRoutingTable(false));
	}

	public Peer(EventLoopGroup group, boolean peerExchange) {
		this.group = group;
		this.events = group.next();
		this.peerExchange = peerExchange;
	}

	public Address getAddress() {
		return address;
	}

	public MultiConnection getByAddress(Address addr) {
		return localConnectionMap.get(addr);
	}

	public boolean routePacket(RelayPacket packet) {
		LocalConnection router = routingTable.tryRoute(packet.getAddress(), this::getByAddress);
		if (router != null) {
			router.sendPacket((RelayPacket)packet.retain()).addListener(ErrorLoggingFuture.SINGLETON);
			return true;
		}
		return false;
	}

	public boolean routePacket(Address to, Packet packet, byte ttl) {
		LocalConnection router = routingTable.tryRoute(to, this::getByAddress);

		if (router != null) {
			if (!(packet instanceof RelayPacket) && !router.getDirectNode().equals(to)) {
				Packet unwrapped = packet;
				ByteBuf buf = PooledByteBufAllocator.DEFAULT.buffer();
				try {
					buf.writeInt(PacketMap.getPacketId(unwrapped));
					unwrapped.write(buf);
					packet = new RelayPacket(buf.retain(), address, to, ttl);
				} finally {
					unwrapped.release();
					buf.release();
				}

			}
			router.sendPacket(packet).addListener(ErrorLoggingFuture.SINGLETON);
			return true;
		}
		return false;
	}
	
	private void receivedPacket(ProcessedPacket msg) {
		LOG.log(Level.INFO, "Received a {0}", msg);
		if(msg.getPacket() instanceof PongPacket) {
			// TODO
		}
	}
	
	public boolean routePacket(Address to, Packet packet) {
		return routePacket(to, packet, (byte)127);
	}

	public ChannelFuture startIncomingConnectionThread(int port) {
		ServerBootstrap server = new ServerBootstrap();
		server.group(group);
		server.channel(NioServerSocketChannel.class);
		server.option(ChannelOption.SO_BACKLOG, 128);
		server.childOption(ChannelOption.SO_KEEPALIVE, true);
		server.childHandler(new ChannelConstructor(incomingListener, clientsIn));
		ChannelFuture f = server.bind(port);
		return f.addListener(e -> {
			if (e.isSuccess()) {
				this.servers.add((ServerChannel) f.channel());
				f.channel().closeFuture().addListener(e1 -> {
					this.servers.remove((ServerChannel) f.channel());
				});
			}
		});
	}

	private synchronized void rebuildRouting() {
		// Use linked here for quick speed while iteration
		Map<Address, Map<Address, Byte>> routing = new LinkedHashMap<>();
		for (MultiConnection c : this.localConnectionMap.values()) {
			routing.put(c.getDirectNode(), c.getKnownDestinations());
		}
		routingTable.setTempRoutingList(routing);
		this.routingTable.rebuildRoutingMap();
	}

	private synchronized void scheduleSendRoutingTable(boolean full) {
		if (sendRoutingUpdate != null && !sendRoutingUpdate.isDone()) {
			return;
		}
		LOG.info("Preparing to send routing table...");
		int m = !full ? 50 : 1;
		sendRoutingUpdate = group.schedule(this::sendRoutingTable,
			ThreadLocalRandom.current().nextInt(4 * m) + 3 * m, TimeUnit.MILLISECONDS);
	}
	
	public Future<?> pingAddress(Address addr) {
		int packetNumber = pingPacketCounter.getAndIncrement();
		byte[] data = new byte[4];
		ByteBuf wrappedBuffer = Unpooled.wrappedBuffer(data);
		wrappedBuffer.writerIndex(0);
		wrappedBuffer.writeInt(packetNumber);
		assert wrappedBuffer.array() == data;
		Promise<PongPacket> promise = events.newPromise();
		pingListeners.put(packetNumber, promise);
		promise.addListener(e -> pingListeners.remove(packetNumber));
		boolean send = routePacket(addr, new PingPacket(data));
		if(!send) {
			promise.setFailure(new IllegalArgumentException("Unknown address"));
		}
		return promise;
	}

	private synchronized void sendRoutingTable() {
		Packet packet = new RoutingUpdatePacket(this.routingTable.generateDelegatedRoutingMap());
		LOG.log(Level.INFO, "Broadcasting routing map!" + packet);
		try {
			for (LocalConnection c : this.localConnectionMap.values()) {
				c.sendPacket((Packet) packet.retain());
			}
		} finally {
			packet.release();
		}
	}

	private void removeLocalConnection(LocalConnection con) {
		if (con == null) {
			return;
		}
		localConnectionMap.remove(con.getDirectNode(), con);

	}

	public ChannelFuture startOutgomingConnectionThread(InetAddress address, int port) {
		Bootstrap client = new Bootstrap();
		client.group(group);
		client.channel(NioSocketChannel.class);
		client.option(ChannelOption.SO_KEEPALIVE, true);
		client.handler(new ChannelConstructor(outgoingListener, clientsOut));
		ChannelFuture f = client.connect(address, port);
		return f.addListener(e -> {
			this.clientsOut.add(f.channel());
			f.channel().closeFuture().addListener(e1 -> {
				this.clientsOut.remove(f.channel());
			});
		});
	}

	private static class ErrorLoggingFuture implements GenericFutureListener<Future<? super Void>> {

		public static final ErrorLoggingFuture SINGLETON = new ErrorLoggingFuture();

		private ErrorLoggingFuture() {
		}

		@Override
		public void operationComplete(Future<? super Void> e) throws Exception {
			if (e.cause() != null) {
				LOG.log(Level.WARNING, "Exception: {0}", e.cause());
			}
		}
	}

	private class ChannelConstructor extends ChannelInitializer<SocketChannel> {

		private final List<Channel> channelList;
		private final ConnectionListener listener;

		private ChannelConstructor(ConnectionListener listener, List<Channel> channelList) {
			this.listener = listener;
			this.channelList = channelList;
		}

		@Override
		protected void initChannel(SocketChannel ch) throws Exception {
			if (ch.remoteAddress() != null && blocked.containsKey(((InetSocketAddress) ch.remoteAddress()).getAddress())) {
				LOG.log(Level.INFO, "{0}: Rejected at socket level", ch);
				ch.close();
				return;
			}
			ch.pipeline().addLast(new LengthFieldPrepender(4));
			ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4));
			//ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
			ch.pipeline().addLast(new PacketEncoder());
			ch.pipeline().addLast(new PacketDecoder());
			//ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
			ch.pipeline().addLast(new ServerBootstrapConnector(address, listener));
			channelList.add(ch);
			ch.closeFuture().addListener(e1 -> {
				channelList.remove(ch);
			});
		}
	}

	private class ConnectionListenerImpl implements ConnectionListener {

		private final boolean incoming;

		private ConnectionListenerImpl(boolean incoming) {
			this.incoming = incoming;

		}

		@Override
		public List<Address> getKnownAddresses() {
			return new ArrayList<>(routingTable.getKnownAddresses());
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
			LocalConnection con = new LocalConnection(!incoming, remote, address, chn, knownRemote);
			connections.put(chn, con);
			LocalConnection old = localConnectionMap.put(remote, con);
			if (old != null) {
				old.close();
			}
			chn.pipeline().addLast(new PacketRoutingHandler(address, routingTable, Peer.this::routePacket));
			//chn.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
			chn.pipeline().addLast(new PacketPreProcessor(remote));
			chn.pipeline().addLast(new PacketHandler(con, Peer.this::receivedPacket));
			con.addRoutingChangedListener(Peer.this::rebuildRouting);
			Peer.this.rebuildRouting();
			// TODO: cache this
			con.sendPacket(new RoutingUpdatePacket(routingTable.generateDelegatedRoutingMap()))
				.addListener(ErrorLoggingFuture.SINGLETON); 
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
			removeLocalConnection(connections.remove(chn));
		}

		@Override
		public void onConnectionReject(Channel chn) {
			LOG.log(Level.INFO, "{0} rejected", chn);
			removeLocalConnection(connections.remove(chn));
			blocked.put(((InetSocketAddress) chn.remoteAddress()).getAddress(), true);
		}
	}

}
