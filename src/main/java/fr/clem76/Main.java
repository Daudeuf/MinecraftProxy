package fr.clem76;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

public class Main {
	public static void main(String[] args) {
		System.out.println("Hello world!");

		EventLoopGroup bossGroup   = new NioEventLoopGroup();
		EventLoopGroup workerGroup = new NioEventLoopGroup();

		try {
			ServerBootstrap serverBootstrap = new ServerBootstrap();
			serverBootstrap.group(bossGroup, workerGroup)
					.channel(NioServerSocketChannel.class)
					.childOption(ChannelOption.TCP_NODELAY, true)
					.childOption(ChannelOption.IP_TOS, 0x18)
					.childHandler(new ChannelInitializer<SocketChannel>() {
						@Override
						public void initChannel(SocketChannel ch) {
							ChannelPipeline p = ch.pipeline();

							// Gestionnaire de canaux pour le client
							p.addLast();
							p.addLast(new ClientChannelHandler("localhost", 25566)); // Adresse et port du serveur Minecraft
						}
					});

			ChannelFuture serverFuture = serverBootstrap.bind(25560).sync(); // Port du proxy

			serverFuture.channel().closeFuture().sync();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			bossGroup.shutdownGracefully();
			workerGroup.shutdownGracefully();
		}
	}

	private static class ClientChannelHandler extends ChannelHandlerAdapter {

		private final String serverHost;
		private final int serverPort;
		private Channel serverChannel;

		public ClientChannelHandler(String serverHost, int serverPort) {
			this.serverHost = serverHost;
			this.serverPort = serverPort;
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
							p.addLast(new ServerChannelHandler(ctx.channel()));
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
				// Intercepter le paquet de ping
				if (msg instanceof ByteBuf) {
					ByteBuf buffer = ( (ByteBuf) msg ).copy();

					// Lire l'ID du paquet
					//int packetId = buffer.readByte();
					String chaine = readString(buffer);


					// Votre logique en fonction de l'ID du paquet
					//System.out.printf("ID du paquet : %d (%s)\n", packetId, Integer.toHexString(packetId));
					System.out.println(chaine);

					// Le reste de votre logique pour traiter le paquet...

					buffer.release();
				}
				else {
					System.out.println("test");
				}

				// Envoyer le paquet au serveur Minecraft sans modification
				serverChannel.writeAndFlush(msg);
			}
		}

		// Fonction pour lire une chaîne depuis le buffer
		private String readString(ByteBuf buffer) {
			int length = buffer.readShort();
			byte[] bytes = new byte[length];
			buffer.readBytes(bytes);
			return new String(bytes);
		}

		// Fonction pour écrire une chaîne dans le buffer
		private void writeString(ByteBuf buffer, String string) {
			byte[] bytes = string.getBytes();
			buffer.writeShort(bytes.length);
			buffer.writeBytes(bytes);
		}

		@Override
		public void channelInactive(ChannelHandlerContext ctx) {
			if (serverChannel != null && serverChannel.isActive()) {
				serverChannel.close();
			}
		}
	}

	private static class ServerChannelHandler extends ChannelHandlerAdapter {

		private final Channel clientChannel;

		public ServerChannelHandler(Channel clientChannel) {
			this.clientChannel = clientChannel;
		}

		@Override
		public void channelRead(ChannelHandlerContext ctx, Object msg) {
			// Envoyer la réponse du serveur Minecraft au client
			clientChannel.writeAndFlush(msg);
		}

		// Fonction pour lire une chaîne depuis le buffer
		private String readString(ByteBuf buffer) {
			int length = buffer.readShort();
			byte[] bytes = new byte[length];
			buffer.readBytes(bytes);
			return new String(bytes);
		}

		// Fonction pour écrire une chaîne dans le buffer
		private void writeString(ByteBuf buffer, String string) {
			byte[] bytes = string.getBytes();
			buffer.writeShort(bytes.length);
			buffer.writeBytes(bytes);
		}

		@Override
		public void channelInactive(ChannelHandlerContext ctx) {
			clientChannel.close();
		}
	}
}