# nettyupload


## 服务端启动服务

```
//第三个参数为文件保存路径
NettyUploadServer nettyUploadServer = new NettyUploadServer("127.0.0.1",2777,"E:\\");
nettyUploadServer.startup();
		
//nettyUploadServer.shutdownServer();
```

## 客户端上传文件
```
  File file = new File("D:\\app.war");
  int res = UploadFileClient.uploadFile(file, "127.0.0.1",2777);
```

## 特点：
* Netty简单上传文件(文件大小无限制)
* 客户端可以同时上传多个文件(同时多次调用UploadFileClient.uploadFile)
* 客户端上传一个文件完成后会自动关闭连接

##有问题反馈
在使用中有任何问题，欢迎反馈给我，可以用以下联系方式跟我交流
* 邮件(aa-aa537#163.com, 把#换成@)
* QQ: 79135754