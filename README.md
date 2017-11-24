# nettyupload

# 服务端启动服务：
NettyUploadServer nettyUploadServer = new NettyUploadServer("127.0.0.1",2777,"E:\\");
nettyUploadServer.startup();
		
//nettyUploadServer.shutdownServer();


# 客户端上传文件：
File file = new File("D:\\app.war");
int res = UploadFileClient.uploadFile(file, "127.0.0.1",2777);


# 特点：
1 Netty简单上传文件(文件大小无限制)
2 客户端可以同时上传多个文件(同时多次调用UploadFileClient.uploadFile)
3 客户端上传一个文件完成后会自动关闭连接