
package me.ferrybig.p2pnetwork.codec.packets;

import io.netty.buffer.ByteBuf;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import me.ferrybig.p2pnetwork.Address;

public class RoutingUpdatePacket extends Packet {
	private final Map<Address, Byte> routes;

	public RoutingUpdatePacket(Map<Address, Byte> routes) {
		this.routes = routes;
	}
	
	public RoutingUpdatePacket(ByteBuf buf) {
		this.routes = new LinkedHashMap<>();
		int size = buf.readInt();
		for(int i = 0; i < size; i++) {
			this.routes.put(new Address(buf), buf.readByte());
		}
	}
	
	@Override
	public void write(ByteBuf buf) {
		buf.writeInt(this.routes.size());
		for(Map.Entry<Address, Byte> route : this.routes.entrySet()) {
			route.getKey().write(buf);
			buf.writeByte(route.getValue());
		}
	}

	public Map<Address, Byte> getRoutes() {
		return routes;
	}

	@Override
	public String toString() {
		return "RoutingUpdatePacket{" + "routes=" + routes + '}';
	}

	@Override
	public int hashCode() {
		int hash = 7;
		hash = 59 * hash + Objects.hashCode(this.routes);
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final RoutingUpdatePacket other = (RoutingUpdatePacket) obj;
		if (!Objects.equals(this.routes, other.routes)) {
			return false;
		}
		return true;
	}
	
}
