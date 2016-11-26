/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package me.ferrybig.p2pnetwork;

import io.netty.buffer.ByteBuf;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

/**
 *
 * @author Fernando
 */
public class Address {

	public static Address random() {
		byte[] data = new byte[128 / 8];
		ThreadLocalRandom.current().nextBytes(data);
		return new Address(data);
	}
	private final byte[] data;
	private final int hashCode;

	public Address(ByteBuf buf) {
		data = new byte[128 / 8];
		buf.readBytes(data);
		hashCode = Arrays.hashCode(this.data);
	}

	public Address(byte[] data) {
		if (data.length != 128 / 8) {
			throw new IllegalArgumentException("Invalid address");
		}
		this.data = data;
		hashCode = Arrays.hashCode(this.data);
	}

	public void write(ByteBuf buf) {
		buf.writeBytes(data);
	}

	@Override
	public int hashCode() {
		return hashCode;
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
		final Address other = (Address) obj;
		if (!Arrays.equals(this.data, other.data)) {
			return false;
		}
		return true;
	}
	
	@Override
	public String toString() {
		return bytesToHex(data);
	}

	final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();

	private static String bytesToHex(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		for (int j = 0; j < bytes.length; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}
}
