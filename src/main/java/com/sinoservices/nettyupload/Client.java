package com.sinoservices.nettyupload;

import java.io.File;

public class Client {
	
	public static void main(String[] a){
		
		
		File file = new File("D:\\moive\\��B��.HD1280���������Ӣ˫��.mp4");
		int res = UploadFileClient.uploadFile(file, "127.0.0.1",2777);
		System.out.println(res);
		
		
	}

}
