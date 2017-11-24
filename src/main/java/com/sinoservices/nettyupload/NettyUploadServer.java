package com.sinoservices.nettyupload;

import static org.jboss.netty.channel.Channels.pipeline;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.codec.http.multipart.DiskFileUpload;
import org.jboss.netty.handler.stream.ChunkedWriteHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyUploadServer {
	private static final Logger logger = LoggerFactory.getLogger(NettyUploadServer.class);
	
	private ServerBootstrap bootstrap = null;
	private static NettyUploadServer nettyUploadServer = null;
	
	private String ip;
	private int port;
	private String uploadFilePath;

	public NettyUploadServer(String ip,int port,String uploadFilePath) {
		this.ip = ip;
		this.port = port;
		this.uploadFilePath = uploadFilePath;
	}
	
	public void startup() {
		bootstrap = new ServerBootstrap(new NioServerSocketChannelFactory(
				Executors.newCachedThreadPool(),
				Executors.newCachedThreadPool()));

		bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
			public ChannelPipeline getPipeline() throws Exception {
				ChannelPipeline pipeline = pipeline();
				pipeline.addLast("decoder", new HttpRequestDecoder());
				pipeline.addLast("encoder", new HttpResponseEncoder());
				pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());
				pipeline.addLast("handler", new ServerHandler());

				return pipeline;
			}

		});

		DiskFileUpload.baseDirectory = uploadFilePath;
		
		bootstrap.bind(new InetSocketAddress(ip, port));
		logger.info("#############################################");
		logger.info("###                                       ###");
		logger.info("###        Netty Server Started !!!       ###");
		logger.info("###        IP:{}               ###",ip);
		logger.info("###        Port:{}                      ###",port);
		logger.info("###        Prefert Started !!!            ###");
		logger.info("###                                       ###");
		logger.info("#############################################");
	}

	public void shutdownServer() {
		bootstrap.releaseExternalResources();
		logger.info("#############################################");
		logger.info("###                                       ###");
		logger.info("###        Netty Server Shutdown !!!      ###");
		logger.info("###        IP:{}               ###",ip);
		logger.info("###        Port:{}                      ###",port);
		logger.info("###        Prefert Shutdown !!!           ###");
		logger.info("###                                       ###");
		logger.info("#############################################");
	}
}