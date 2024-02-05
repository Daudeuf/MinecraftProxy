package fr.clem76;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class PlayerChannelHandler extends ByteToMessageDecoder
{
	private final String serverHost;
	private final int     serverPort;
	private       Channel serverChannel;


	public PlayerChannelHandler(String serverHost, int serverPort) {
		this.serverHost = serverHost;
		this.serverPort = serverPort;
	}


	@Override
	protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf, List<Object> list) throws Exception {

		short first = byteBuf.readUnsignedByte();

		System.out.println(first);
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) {
		EventLoopGroup group = new NioEventLoopGroup();

		Bootstrap bootstrap = new Bootstrap();
		bootstrap.group(group)
				.channel(NioSocketChannel.class)
				.handler(new ChannelInitializer<SocketChannel>() {
					@Override
					public void initChannel(SocketChannel ch) {
						ChannelPipeline p = ch.pipeline();

						// Gestionnaire de canaux pour le serveur
						p.addLast(new Main.ServerChannelHandler(ctx.channel()));
					}
				});

		bootstrap.connect(serverHost, serverPort).addListener((ChannelFutureListener) future -> {
			if (future.isSuccess()) {
				serverChannel = future.channel();
			} else {
				ctx.close();
			}
		});
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) {
		if (serverChannel != null && serverChannel.isActive()) {

			if (msg instanceof ByteBuf) {
				ByteBuf byteBuf = (ByteBuf) msg;
				int readerIndex = byteBuf.readerIndex();

				int size = Utils.readVarInt(byteBuf);
				int packetId = Utils.readVarInt(byteBuf);

				if (packetId == 0) {
					String data = Utils.readString(byteBuf, size);

					System.out.println("Client -> Serveur (" + size + ", " + packetId + ") : " + data);
				} else {
					System.out.println("Client -> Serveur (" + size + ", " + packetId + ")");
				}

				byteBuf.readerIndex(readerIndex);

				//byteBuf.release();
			}
		}
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) {
		if (serverChannel != null && serverChannel.isActive()) {
			serverChannel.close();
		}
	}
}
