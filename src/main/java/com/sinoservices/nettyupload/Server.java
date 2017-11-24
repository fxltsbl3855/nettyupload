package com.sinoservices.nettyupload;


public class Server {
	
	public static void main(String[] a){
		NettyUploadServer nettyUploadServer = new NettyUploadServer("127.0.0.1",2777,"E:\\");
		nettyUploadServer.startup();
		
		
		
		
		//nettyUploadServer.shutdownServer();
	}

}
