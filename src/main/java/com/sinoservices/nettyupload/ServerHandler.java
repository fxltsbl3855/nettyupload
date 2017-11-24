package com.sinoservices.nettyupload;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CACHE_CONTROL;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.DATE;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.EXPIRES;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.LAST_MODIFIED;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.channels.ClosedChannelException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

import javax.activation.MimetypesFileTypeMap;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.handler.codec.frame.TooLongFrameException;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import org.jboss.netty.handler.codec.http.multipart.DiskFileUpload;
import org.jboss.netty.handler.codec.http.multipart.FileUpload;
import org.jboss.netty.handler.codec.http.multipart.HttpDataFactory;
import org.jboss.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import org.jboss.netty.handler.codec.http.multipart.HttpPostRequestDecoder.EndOfDataDecoderException;
import org.jboss.netty.handler.codec.http.multipart.InterfaceHttpData;
import org.jboss.netty.handler.codec.http.multipart.InterfaceHttpData.HttpDataType;
import org.jboss.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ServerHandler extends SimpleChannelHandler {
	private static final Logger logger = LoggerFactory.getLogger(ServerHandler.class);
	
	public static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
	public static final String HTTP_DATE_GMT_TIMEZONE = "GMT";
	public static final int HTTP_CACHE_SECONDS = 60;
	public static final String FILE_TRANSFER_COMPLETED = "file_transfer_completed";

	private static final HttpDataFactory factory = new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE); 
	private HttpPostRequestDecoder decoder;
	private HttpRequest request;
	private String receiveFileName = "";

	public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) {
		logger.debug("channelOpen invoked...");
	}

	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
			throws Exception {
		if (e.getMessage() instanceof HttpRequest) {
			HttpRequest request = (DefaultHttpRequest) e.getMessage();
			String uri = sanitizeUri(request.getUri());
			if (request.getMethod() == HttpMethod.POST) {
				logger.info("start upload file time = {}, fileName = {}",new Date(),uri);
				receiveFileName = uri;
				this.request = request;

				if (decoder != null) {
					decoder.cleanFiles();
					decoder = null;
				}

				try {
					decoder = new HttpPostRequestDecoder(factory, request);
				} catch (Exception e1) {
					logger.error("create HttpPostRequestDecoder error",e1);
					writeResponse(e.getChannel(),
							"接收文件信息时出现异常：" + e1.toString());
					Channels.close(e.getChannel());
					logger.info("channel closed");
					return;
				}

				if (!request.isChunked()) {
					readHttpDataChunkByChunk();
					writeResponse(e.getChannel(), FILE_TRANSFER_COMPLETED);
				}
			}
		} else {
			HttpChunk chunk = (HttpChunk) e.getMessage();
			if (!chunk.isLast()) {
				try {
					decoder.offer(chunk);
				} catch (Exception e1) {
					logger.error("decoder offer error",e1);
					writeResponse(e.getChannel(),
							"接收文件数据时出现异常：" + e1.toString());
					Channels.close(e.getChannel());
					logger.info("channel closed");
					return;
				}
				readHttpDataChunkByChunk();

			} else {
				readHttpDataChunkByChunk();
				writeResponse(e.getChannel(), FILE_TRANSFER_COMPLETED);
				sendReturnMsg(ctx, HttpResponseStatus.OK, "服务端返回的消息！");
			}
		}
	}

	private void readHttpDataChunkByChunk() {
		try {
			while (decoder.hasNext()) {
				InterfaceHttpData data = decoder.next();
				if (data != null) {
					writeHttpData(data);
				}
			}
		} catch (EndOfDataDecoderException e1) {
			logger.error("writeHttpData error",e1);
		}
	}

	private void writeHttpData(InterfaceHttpData data) {
		if (data.getHttpDataType() == HttpDataType.FileUpload) {
			FileUpload fileUpload = (FileUpload) data;
			if (fileUpload.isCompleted()) {
				try {
					StringBuffer fileNameBuf = new StringBuffer();
					fileNameBuf.append(DiskFileUpload.baseDirectory)
							.append("U").append(System.currentTimeMillis());
					fileNameBuf.append("_").append(receiveFileName);
					logger.info("uploaded file is : " + fileNameBuf);
					fileUpload.renameTo(new File(fileNameBuf.toString()));
					logger.info("end upload file time = {}, fileName = {}",new Date(),fileNameBuf.toString());
				} catch (IOException e) {
					logger.error("rename file error",e);
				}
				
			}
		} 
	}

	private void writeResponse(Channel channel, String retMsg) {
		ChannelBuffer buf = ChannelBuffers.copiedBuffer(retMsg,CharsetUtil.UTF_8);
		boolean close = HttpHeaders.Values.CLOSE.equalsIgnoreCase(request
				.headers().get(HttpHeaders.Names.CONNECTION))|| request.getProtocolVersion().equals(HttpVersion.HTTP_1_0)
				&& !HttpHeaders.Values.KEEP_ALIVE.equalsIgnoreCase(request.headers().get(HttpHeaders.Names.CONNECTION));

		HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
				HttpResponseStatus.OK);
		response.setContent(buf);
		response.headers().add(HttpHeaders.Names.CONTENT_TYPE,"text/plain; charset=UTF-8");
		if (!close) {
			response.headers().add(HttpHeaders.Names.CONTENT_LENGTH,
					String.valueOf(buf.readableBytes()));
		}

		ChannelFuture future = channel.write(response);
		// Close the connection after the write operation is done if necessary.
		if (close) {
			future.addListener(ChannelFutureListener.CLOSE);
		}
	}

	private String sanitizeUri(String uri) {
		try {
			uri = URLDecoder.decode(uri, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			try {
				uri = URLDecoder.decode(uri, "ISO-8859-1");
			} catch (UnsupportedEncodingException e1) {
				throw new Error();
			}
		}
		return uri;
	}

	/**
	 * 方法描述：设置请求响应的header信息
	 * 
	 * @param response
	 *            请求响应对象
	 * @param fileToCache
	 *            下载文件
	 */
	private static void setContentTypeHeader(HttpResponse response,
			File fileToCache) {
		MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
		response.headers().add(CONTENT_TYPE,
				mimeTypesMap.getContentType(fileToCache.getPath()));
		SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT,
				Locale.US);
		dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));
		// Date header
		Calendar time = new GregorianCalendar();
		response.headers().add(DATE, dateFormatter.format(time.getTime()));
		// Add cache headers
		time.add(Calendar.SECOND, HTTP_CACHE_SECONDS);
		response.headers().add(EXPIRES, dateFormatter.format(time.getTime()));
		response.headers().add(CACHE_CONTROL,
				"private, max-age=" + HTTP_CACHE_SECONDS);
		response.headers().add(LAST_MODIFIED,
				dateFormatter.format(new Date(fileToCache.lastModified())));
	}

	/**
	 * 方法描述：给客户端发送反馈消息
	 * 
	 * @param ctx
	 *            发送消息的通道
	 * @param status
	 *            状态
	 * @param retMsg
	 *            反馈消息
	 */
	private static void sendReturnMsg(ChannelHandlerContext ctx,
			HttpResponseStatus status, String retMsg) {
		HttpResponse response = new DefaultHttpResponse(HTTP_1_1, status);
		response.headers().add(CONTENT_TYPE, "text/plain; charset=UTF-8");
		response.setContent(ChannelBuffers.copiedBuffer(retMsg,
				CharsetUtil.UTF_8));

		// 信息发送成功后，关闭连接通道
		ctx.getChannel().write(response)
				.addListener(ChannelFutureListener.CLOSE);
	}

	public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
			throws Exception {
		if (decoder != null) {
			decoder.cleanFiles();
		}
		logger.info("连接断开:"+ e.getChannel().getRemoteAddress().toString());
	}

	public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
			throws Exception {
		logger.info("收到连接:" + e.getChannel().getRemoteAddress().toString());
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
			throws Exception {
		Channel ch = e.getChannel();
		Throwable cause = e.getCause();
		if (cause instanceof TooLongFrameException) {
			return;
		} else if (cause instanceof ClosedChannelException) {
			logger.info("连接的通道已关闭");
			if (ch.isConnected()) {
				ch.close();
			}
			return;
		} else {
			logger.info("通道异常,e=" + cause.getMessage());
		}
	}

}
