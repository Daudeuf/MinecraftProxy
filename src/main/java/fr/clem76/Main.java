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

import java.util.Arrays;

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
							//p.addLast();
							//p.addLast(new PlayerChannelHandler("localhost", 25565));
							p.addLast(new ClientChannelHandler("localhost", 25565)); // Adresse et port du serveur Minecraft
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


	/*public static void decodeMinecraftPacket(ByteBuf byteBuf) {
		// Lire la taille du paquet (VarInt)
		int packetSize = 0;//readVarInt(byteBuf);

		// Lire l'identifiant du paquet (VarInt)
		int packetId = Utils.readVarInt(byteBuf);

		// Traiter les données spécifiques au paquet en fonction de son identifiant
		switch (packetId) {
			case 0x01:  // Exemple : Paquet de Ping
				long pingTime = byteBuf.readLong();
				System.out.println("Paquet de Ping - Temps : " + pingTime);
				break;

			case 0x0F:  // Exemple : Paquet de Chat
				String chatMessage = Utils.readString(byteBuf);
				System.out.println("Paquet de Chat - Message : " + chatMessage);
				break;

			case 0x12:  // Exemple : Paquet de Mouvement du Joueur
				double deltaX = byteBuf.readDouble();
				double deltaY = byteBuf.readDouble();
				double deltaZ = byteBuf.readDouble();
				float deltaYaw = byteBuf.readFloat();
				float deltaPitch = byteBuf.readFloat();
				boolean onGround = byteBuf.readBoolean();

				System.out.println("Paquet de Mouvement du Joueur - DeltaX : " + deltaX +
						", DeltaY : " + deltaY +
						", DeltaZ : " + deltaZ +
						", DeltaYaw : " + deltaYaw +
						", DeltaPitch : " + deltaPitch +
						", On Ground : " + onGround);
				break;

			// Ajoutez des cas pour d'autres types de paquets selon votre besoin
			case 48:
				System.out.println("Open book : ");
				break;

			default:
				System.out.println("Paquet non pris en charge avec l'identifiant : " + packetId + " taille : " + packetSize);
		}
	}*/

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

				if (msg instanceof ByteBuf) {
					ByteBuf byteBuf = (ByteBuf) msg;
					int readerIndex = byteBuf.readerIndex();

					int packetId = Utils.readVarInt(byteBuf);

					//byteBuf.readerIndex(readerIndex);

					switch (packetId) {
						case 16 -> {
							int protocol = Utils.readVarInt(byteBuf);
							String data = Utils.readString(byteBuf, packetId);
							System.out.println(packetId + "---" + protocol + "---" + data);
						}
						default -> {
							String data = Utils.readString(byteBuf, packetId);

							System.out.println("Client -> Serveur (" + packetId + ") : " + data);
						}
					}

					byteBuf.readerIndex(readerIndex);
				}

				// Envoyer le paquet au serveur Minecraft sans modification
				serverChannel.writeAndFlush(msg);
			}
		}

		@Override
		public void channelInactive(ChannelHandlerContext ctx) {
			if (serverChannel != null && serverChannel.isActive()) {
				serverChannel.close();
			}
		}
	}

	public static class ServerChannelHandler extends ChannelHandlerAdapter {

		private final Channel clientChannel;

		public ServerChannelHandler(Channel clientChannel) {
			this.clientChannel = clientChannel;
		}

		@Override
		public void channelRead(ChannelHandlerContext ctx, Object msg) {

			if (msg instanceof ByteBuf) {
				ByteBuf byteBuf = (ByteBuf) msg;
				int readerIndex = byteBuf.readerIndex();

				//short first = byteBuf.readUnsignedByte();

				//byteBuf.readerIndex(readerIndex);

				int size = Utils.readVarInt(byteBuf);
				int packetId = 0;//Utils.readVarInt(byteBuf);

				//byteBuf.readerIndex(readerIndex);

				if (packetId == 0) {
					String data = Utils.readString(byteBuf, size);

					System.out.println("Serveur -> Client (" + size + ", " + packetId + /*", " + first + */") : " + data);
				} else {
					System.out.println("Serveur -> Client (" + size + ", " + packetId + /*", " + first + */")");
				}

				/*byteBuf.readerIndex(readerIndex);

				if (size == 119)
				{
					System.out.println(Utils.readString(byteBuf));

				}*/

				byteBuf.readerIndex(readerIndex);

				//byteBuf.release();
			}

			// Envoyer la réponse du serveur Minecraft au client
			clientChannel.writeAndFlush(msg);
		}

		@Override
		public void channelInactive(ChannelHandlerContext ctx) {
			clientChannel.close();
		}
	}
}