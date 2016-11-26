/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package me.ferrybig.p2pnetwork.codec.packets;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.util.Collection;
import me.ferrybig.p2pnetwork.Address;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 *
 * @author admin
 */
@RunWith(Parameterized.class)
public class RelayPacketTest {

	private final static Address STATIC_ADDRESS_1 = Address.random();

	private final static Address STATIC_ADDRESS_2 = Address.random();

	@Parameterized.Parameters
	public static Collection<Object[]> data() {
		return Arrays.asList(new Object[][]{
			new Object[]{Unpooled.wrappedBuffer(new byte[0]), STATIC_ADDRESS_1, STATIC_ADDRESS_2, (byte) 1},
			new Object[]{Unpooled.wrappedBuffer(new byte[100]), STATIC_ADDRESS_1, STATIC_ADDRESS_2, (byte) 1},
			new Object[]{Unpooled.wrappedBuffer(new byte[]{1, 2, 3, 4, 5, 6, 7}), STATIC_ADDRESS_2, STATIC_ADDRESS_1, (byte) 1},});
	}
	private final ByteBuf contents;
	private final Address from;
	private final Address to;
	private final int ttl;

	public RelayPacketTest(ByteBuf contents, Address from, Address to, int ttl) {
		this.contents = contents;
		this.from = from;
		this.to = to;
		this.ttl = ttl;
	}

	@Test
	public void testSerialize() {
		ByteBuf buf = Unpooled.buffer();
		try {
			RelayPacket relay = new RelayPacket(contents, from, to, ttl);
			relay.write(buf);
			relay = new RelayPacket(buf);
			try {
				assertEquals(contents, relay.getData());
				assertEquals(from, relay.getFrom());
				assertEquals(to, relay.getAddress());
				assertEquals(ttl, relay.getTtl());
			} finally {
				relay.release();
			}
		} finally {
			buf.release();
		}
	}

}
