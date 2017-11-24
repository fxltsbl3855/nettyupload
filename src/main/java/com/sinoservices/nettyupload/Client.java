package com.sinoservices.nettyupload;

import java.io.File;

public class Client {
	
	public static void main(String[] a){
		
		
		File file = new File("D:\\moive\\¼åBÏÀ.HD1280³¬Çå¹úÓïÖĞÓ¢Ë«×Ö.mp4");
		int res = UploadFileClient.uploadFile(file, "127.0.0.1",2777);
		System.out.println(res);
		
		
	}

}
