package simpella;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;


public class download_accept implements Runnable {
	private Socket connection;
	private String filename;
	public download_accept(Socket s,String filename) {
	  this.connection = s;
	  this.filename = filename;
	}
	public void run() {
	    try {
	    	//while (true) {
	    		//every time there is a tcp connection comming, create a new thread to handkle it
	    		File file=new File(filename);
	    		InputStream in = connection.getInputStream();
                //BufferedReader readerFile = new BufferedReader(new InputStreamReader(in));
	    		BufferedInputStream reader = new BufferedInputStream(in);  
                //DataInputStream reader = new DataInputStream(in);
                FileOutputStream fos = new FileOutputStream(file);
                BufferedOutputStream fout =new BufferedOutputStream(fos);
                byte[] bs = new byte[4096];
                int length;
                System.out.println("\ndownloading '"+filename+"'");
                while( (length=reader.read(bs)) != -1){
                	fout.write(bs,0,length);
                	//Thread.sleep(200);
                }
                fout.flush();
                fout.close();
                System.out.print("File receiving finished, and saving in current directory\nsimpella >>");
		    //}
	    }
	    catch (IOException e){} 
	}
}
