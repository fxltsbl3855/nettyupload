package com.sinoservices.nettyupload;

import static org.jboss.netty.channel.Channels.pipeline;

import java.io.File;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpClientCodec;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpRequestEncoder;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseDecoder;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import org.jboss.netty.handler.codec.http.multipart.HttpDataFactory;
import org.jboss.netty.handler.codec.http.multipart.HttpPostRequestEncoder;
import org.jboss.netty.handler.codec.http.multipart.InterfaceHttpData;
import org.jboss.netty.handler.stream.ChunkedWriteHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UploadFileClient {
	private static final Logger logger = LoggerFactory.getLogger(UploadFileClient.class);
	
	private ClientBootstrap bootstrap = null;
	private ChannelFuture future = null;
	private HttpDataFactory factory = null;
	private boolean isStarted = false;
	
	private String ip;
	private int port;

	public UploadFileClient(String ip,int port) {
		this.ip = ip;
		this.port = port;
		bootstrap = new ClientBootstrap(new NioClientSocketChannelFactory(
				Executors.newCachedThreadPool(),
				Executors.newCachedThreadPool()));
		bootstrap.setPipelineFactory(new UploadChannelFactory());
		bootstrap.setOption("connectTimeoutMillis", 3000);
		future = bootstrap.connect(new InetSocketAddress(ip, port));
		future.awaitUninterruptibly();
		if(future.isSuccess()){
			logger.info("netty client connect success !!!");
			isStarted = true;
			// 获得一个阈值，它是来控制上传文件时内存/硬盘的比值，防止出现内存溢出
			factory = new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE);
			logger.info("netty client started !!!");
		}else{
			logger.info("netty client start fail !!!"+future.getCause().getMessage());
		}
	}

	public static int uploadFile(File file,String ip,int port){
		UploadFileClient ss = new UploadFileClient(ip,port);
		try{
			if(!ss.isStarted){
				logger.error("Netty Client is not establish connection to Server. Please check log to solve problem");
				return -10;
			}
			int res = ss.uploadFile(file);
			return res;
		}finally{
			ss.shutdownClient();
		}
		
	}
	
	/**
	 * 方法描述：关闭文件发送通道（为阻塞式）
	 */
	public void shutdownClient() {
		// 等待数据的传输通道关闭
		future.getChannel().getCloseFuture().awaitUninterruptibly(5,TimeUnit.SECONDS);
		try{
			bootstrap.releaseExternalResources();
		}catch(Exception e){
			logger.error("shutdownClient exception,e="+e.getMessage());
		}
		// Really clean all temporary files if they still exist
		if(factory!=null){
			factory.cleanAllHttpDatas();
		}
		logger.info("netty client shudown!!!,ip = {} , port = {}",ip,port);
	}

	/**
	 * 方法描述：将文件上传到服务端
	 * 
	 * @param file
	 *            待上传的文件
	 */
	public int uploadFile(File file) {
		logger.info("uploadFile invoked...");
		if (!file.canRead()) {
			logger.error("file can not read...file="+file.getName());
			return -1;
		}

		// Simple Post form: factory used for big attributes
		List<InterfaceHttpData> bodylist = formpost(file);
		if (bodylist == null) {
			logger.error("bodylist is null");
			return -2;
		}

		// Multipart Post form: factory used
		long startTime = System.currentTimeMillis();
		logger.info("client send file to server start");
		uploadFileToServer(file.getName(), factory, bodylist);
		long endTime = System.currentTimeMillis();
		logger.info("client send file to server end, time={} ms",(endTime-startTime));
		return 1;
	}

	/**
	 * @param file
	 * @return
	 */
	private List<InterfaceHttpData> formpost(File file) {
		HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1,
				HttpMethod.POST, "");
		// Use the PostBody encoder
		HttpPostRequestEncoder bodyRequestEncoder = null;
		try {
			bodyRequestEncoder = new HttpPostRequestEncoder(factory, request,
					false);
			bodyRequestEncoder.addBodyAttribute("getform", "POST");
			bodyRequestEncoder.addBodyFileUpload("myfile", file,
					"application/x-zip-compressed", false);
		} catch (Exception e) {
			logger.error("HttpPostRequestEncoder create error",e);
			return null;
		}

		// Create the bodylist to be reused on the last version with Multipart support
		List<InterfaceHttpData> bodylist = bodyRequestEncoder.getBodyListAttributes();
		return bodylist;
	}

	/**
	 * Multipart example
	 */
	private void uploadFileToServer(String fileName, HttpDataFactory factory,
			List<InterfaceHttpData> bodylist) {
		// Wait until the connection attempt succeeds or fails.
		Channel channel = future.awaitUninterruptibly().getChannel();
		if (!future.isSuccess()) {
			future.getCause().printStackTrace();
			bootstrap.releaseExternalResources();
			return;
		}

		// Prepare the HTTP request.
		HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1,
				HttpMethod.POST, fileName);
		
		// 设置该属性表示服务端文件接收完毕后会关闭发送通道
//		request.setHeader(HttpHeaders.Names.CONNECTION,
//				HttpHeaders.Values.CLOSE);
		request.headers().add(HttpHeaders.Names.CONNECTION,
				HttpHeaders.Values.CLOSE);

		// Use the PostBody encoder
		HttpPostRequestEncoder bodyRequestEncoder = null;
		try {
			bodyRequestEncoder = new HttpPostRequestEncoder(factory, request,
					true);
			bodyRequestEncoder.setBodyHttpDatas(bodylist);
			bodyRequestEncoder.finalizeRequest();
		} catch (Exception e) {
			logger.error("HttpPostRequestEncoder create error2",e);
		}
		
		// send request
		channel.write(request);

		// test if request was chunked and if so, finish the write
		if (bodyRequestEncoder.isChunked()) {
			channel.write(bodyRequestEncoder).awaitUninterruptibly();
		}
		// Now no more use of file representation (and list of HttpData)
		bodyRequestEncoder.cleanFiles();
	}

	private class UploadChannelFactory implements ChannelPipelineFactory {
		public ChannelPipeline getPipeline() throws Exception {
			ChannelPipeline pipeline = pipeline();
			pipeline.addLast("decoder", new HttpResponseDecoder());
			pipeline.addLast("encoder", new HttpRequestEncoder());
			pipeline.addLast("codec", new HttpClientCodec());
			pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());
			pipeline.addLast("handler", new ClientHandler());
			return pipeline;
		}
	}

private	class ClientHandler extends SimpleChannelUpstreamHandler {
		private boolean readingChunks;

		public void channelClosed(ChannelHandlerContext ctx, MessageEvent e){
			logger.info("channelClosed invoked...");
		}
		
		public void channelConnected(ChannelHandlerContext ctx, MessageEvent e){
			logger.info("channelConnected invoked...");
		}
		
		public void channelDisconnected(ChannelHandlerContext ctx, MessageEvent e){
			logger.info("channelDisconnected invoked...");
		}
		
		public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
				throws Exception {
			if (!readingChunks) {
				HttpResponse response = (HttpResponse) e.getMessage();
				// 收到服务端反馈的消息，并且链接正常、且还有后续消息
				if (response.getStatus().getCode() == 200
						&& response.isChunked()) {
					readingChunks = true;
				}
			} else {
				HttpChunk chunk = (HttpChunk) e.getMessage();
				if (chunk.isLast()) {
					// 服务端的消息接收完毕
					readingChunks = false;
				} 
			}
		}

		/**
		 * 方法描述：消息接收或发送过程中出现异常
		 * 
		 * @param ctx
		 *            发送消息的通道对象
		 * @param e
		 *            异常事件对象
		 */
		public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
				throws Exception {
			logger.error("exceptionCaught"+e.getCause().getMessage());
			e.getChannel().close();
		}
	}
	
}