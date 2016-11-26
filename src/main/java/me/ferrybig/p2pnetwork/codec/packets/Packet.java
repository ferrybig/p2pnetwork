/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package me.ferrybig.p2pnetwork.codec.packets;

import io.netty.buffer.ByteBuf;
import io.netty.util.AbstractReferenceCounted;
import java.util.logging.Logger;

/**
 *
 * @author Fernando
 */
public abstract class Packet extends AbstractReferenceCounted {

	private static final Logger LOG = Logger.getLogger(Packet.class.getName());
	
	public abstract void write(ByteBuf buf);
	
	@Override
	public Packet touch(Object hint) {
		//LOG.log(Level.INFO, "[{0}] {1} {2}", new Object[]{this.getClass().getCanonicalName(), hint, this.toString()});
		return this;
	}

	@Override
	protected void deallocate() {
		// Empty for overriding purposes
	}
}
