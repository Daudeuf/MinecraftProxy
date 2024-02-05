package fr.clem76;

import io.netty.buffer.ByteBuf;

import java.util.Arrays;

public class Utils
{
	public static int readVarInt(ByteBuf byteBuf) {
		int i = 0;
		int maxRead = Math.min(5, byteBuf.readableBytes());
		for (int j = 0; j < maxRead; j++) {
			int k = byteBuf.readByte();
			i |= (k & 0x7F) << j * 7;
			if ((k & 0x80) != 128) {
				return i;
			}
		}

		return Integer.MIN_VALUE;
	}


	// Fonction pour lire une chaîne de caractères à partir du ByteBuf
	public static String readString(ByteBuf byteBuf, int length) {
		byte[] bytes = new byte[length];
		byteBuf.readBytes(bytes);
		System.out.println(Arrays.toString(bytes));
		return new String(bytes);
	}
}
