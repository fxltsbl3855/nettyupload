# nettyupload

# �������������
NettyUploadServer nettyUploadServer = new NettyUploadServer("127.0.0.1",2777,"E:\\");
nettyUploadServer.startup();
		
//nettyUploadServer.shutdownServer();


# �ͻ����ϴ��ļ���
File file = new File("D:\\app.war");
int res = UploadFileClient.uploadFile(file, "127.0.0.1",2777);


# �ص㣺
1 Netty���ϴ��ļ�(�ļ���С������)
2 �ͻ��˿���ͬʱ�ϴ�����ļ�(ͬʱ��ε���UploadFileClient.uploadFile)
3 �ͻ����ϴ�һ���ļ���ɺ���Զ��ر�����