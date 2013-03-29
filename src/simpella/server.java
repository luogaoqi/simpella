package simpella;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class server {
	private  int simpella_port;
	private  int download_port;
	private  String ip;
	private  String dir;
	private  int incom_num ;
	private  int outcom_num ;
	private  int ReceivedQuery;
	private  int RespondQuery;
	private  int hostNum;
	private  int peerFileNum;
	private  int shareFileNum;
	private  int peerFileSize;
	private  int shareFileSize;
	private  byte[] mesg_ID;
	private  byte[] ping_mesg;
	private  String[] fileName;
	
	private  Map<ByteArrayWrapper, Socket> map;
	private  Map<ByteArrayWrapper,Socket> GUID;
	private  Map<ByteArrayWrapper,Socket> query_set;

	private  ArrayList<ReturnFile> fileList;
	private  ArrayList<String> SearchQuery;
	public server(){
		this.simpella_port = 6345;
		this.download_port = 5635;
		incom_num = 0;
		outcom_num = 0;
		ReceivedQuery = 0;
		RespondQuery = 0;
		hostNum = 0;
		peerFileNum = 0;
		peerFileSize = 0;
		shareFileNum = 0;
		shareFileSize = 0;
		map = new HashMap<ByteArrayWrapper, Socket>();
		GUID = new HashMap<ByteArrayWrapper, Socket>();
		query_set = new HashMap<ByteArrayWrapper, Socket>();
		mesg_ID = new byte[16];
		fileList = new ArrayList<ReturnFile>();
		SearchQuery = new ArrayList<String>();
	}
		
	public class listener implements Runnable{
		private ServerSocket socket;
		private byte[] mesg_id;
		public listener(ServerSocket s,byte[] mesg_id) {
		  this.socket = s;
		  this.mesg_id = mesg_id;
		}
		public void run() {
		    try {
		    	while (true) {
		    		
		    		//every time there is a tcp connection comming, create a new thread to handkle it
		    		Socket connection = socket.accept();
		    		tcp_thread runnable = new tcp_thread(connection,mesg_id);
		    		Thread thread = new Thread(runnable);
		    		thread.start();
		    	}
		    }
		    catch (IOException e){
		    	// TODO Auto-generated catch block
				e.printStackTrace();
		    }
		}
	}
	public class selector implements Runnable {
		private Socket connection;
		public selector(Socket connection){
			this.connection = connection;
		}
		public void run(){
			while(true){
				try {
					BufferedInputStream	in = new BufferedInputStream(connection.getInputStream());
					byte[] b = new byte[4096];
					int count = in.read(b);
					String mesg = new String(b);
					if( b[16] == 0x00 ){//ping message
			      		byte[] ping_id = new byte[16];
			      		byte[] ping = new byte[23];
			      		System.arraycopy(b, 0, ping_id, 0, 16);
			      		System.arraycopy(b, 0, ping, 0, 23);
			      		ByteArrayWrapper ping_wrap = new ByteArrayWrapper(ping_id);
			      		byte[] pong = gen_pong(ping,ip, simpella_port);
			      		//if(!GUID.containsKey(ping_wrap)){
			      				GUID.put(ping_wrap,connection);
			      				if(ping[17] !=0){
				      				p2p_flush(ping,connection);
				      			}
			      		//}
			      		if(GUID.containsKey(ping_wrap)){
			      			
			      			System.out.print("got a ping mesg, genarate pong mesg and forward back\nsimpella >>");
			      			BufferedOutputStream out_pong=new BufferedOutputStream(GUID.get(ping_wrap).getOutputStream());
			      			out_pong.write(pong);
			      			out_pong.flush();
			      		}
			      		
			      	}
			      	else if( b[16] == 0x01 ){//pong message
			      		byte[] pong_id = new byte[16];
			      		byte[] pong = new byte[37];
			      		System.arraycopy(b, 0, pong_id, 0, 16);
			      		System.arraycopy(b, 0, pong, 0, 37);
			      		ByteArrayWrapper pong_wrap = new ByteArrayWrapper(pong_id);
			      		if(GUID.containsKey(pong_wrap)){
			      			System.out.print("got a pong mesg, and forward back\nsimpella >>");
			      			BufferedOutputStream out_pong=new BufferedOutputStream(GUID.get(pong_wrap).getOutputStream());
			      			out_pong.write(pong);
			      			out_pong.flush();
			      		}else{
			      			hostNum ++; 
			      			peerFileSize = (int)(ByteToInt(pong[33]) * Math.pow(2, 24)+ByteToInt(pong[34]) * Math.pow(2, 16)+ByteToInt(pong[35]) * Math.pow(2, 8)+ByteToInt(pong[36]));
			      			peerFileNum = (int)(ByteToInt(pong[29]) * Math.pow(2, 24)+ByteToInt(pong[30]) * Math.pow(2, 16)+ByteToInt(pong[31]) * Math.pow(2, 8)+ByteToInt(pong[32]));
			      			System.out.print("receive a pong message!\nsimpella >>");
			      		}
			      	}
			      	else if( b[16] == (byte)0x80 ){//query message
			      		ReceivedQuery ++;
			      		byte[] query = new byte[23];
			      		byte[] query_id = new byte[16];
			      		String query_str = new String(b,25,count-25);
			      		SearchQuery.add(query_str);
			      		System.out.print("query: "+query_str+"\nsimpella >>");
			      		System.arraycopy(b, 0, query_id, 0, 16);
			      		System.arraycopy(b, 0, query, 0, 23);
			      		
			      		byte[] flush_query = gen_query(query,query_str);
			      		ByteArrayWrapper query_wrap = new ByteArrayWrapper(query_id);
			      		String[] querys = query_str.split(" ");
			      		Map<String,byte[]> matchfile = new HashMap<String,byte[]>();
			      		for(int i=0;i<querys.length;i++){
			      			for(int j=0;j<fileName.length;j++){
			      				String filepath = dir+"/"+fileName[j];
			      				File file = new File(filepath);
			      				if(file.isFile()){
			      					if(fileName[j].indexOf(querys[i]) !=-1 && !matchfile.containsKey(fileName[j])){
			      						byte[] fname = fileName[j].getBytes();
			      						byte[] filebytes = new byte[9+fname.length];
			      						filebytes[3] = (byte)(j & 0x000000FF);
			      						filebytes[2] = (byte)((j & 0x0000FF00) >> 8);
			      						filebytes[1] = (byte)((j & 0x00FF0000) >> 16);
			      						filebytes[0] = (byte)((j & 0xFF000000) >> 24);
			      						FileInputStream fi = new FileInputStream(file);   
										int size = fi.available();
										fi.close();
										filebytes[7] = (byte)(size & 0x000000FF);
			      						filebytes[6] = (byte)((size & 0x0000FF00) >> 8);
			      						filebytes[5] = (byte)((size & 0x00FF0000) >> 16);
			      						filebytes[4] = (byte)((size & 0xFF000000) >> 24);
			      						filebytes[8] = (byte)fname.length;
			      						System.arraycopy(fname, 0, filebytes, 9, fname.length);
			      						matchfile.put(fileName[j],filebytes);
			      					}
			      				}
			      			}
			      		}
			      		byte[] query_hit = gen_queryhit(query,matchfile);
			      		//if(!query_set.containsKey(query_wrap)){
		      			query_set.put(query_wrap,connection);
		      			if(flush_query[17] !=0){
			      			p2p_flush(flush_query,connection);
			      		}
			      		if(query_set.containsKey(query_wrap)&& (query_hit != null)){
			      			RespondQuery ++;
			      			System.out.print("find query, forwards back!\nsimpella >>");
			      			BufferedOutputStream out_query=new BufferedOutputStream(query_set.get(query_wrap).getOutputStream());
			      			out_query.write(query_hit);
			      			out_query.flush();
			      		}
			      	}
			      	else if( b[16] == (byte)0x81 ){//queryhit message
			      		byte[] queryhit_id = new byte[16];
			      		byte[] queryhit = new byte[count];
			      		System.arraycopy(b, 0, queryhit_id, 0, 16);
			      		System.arraycopy(b, 0, queryhit, 0, count);
			      		ByteArrayWrapper queryhit_wrap = new ByteArrayWrapper(queryhit_id);
			      		if(GUID.containsKey(queryhit_wrap)){
			      			System.out.print("got a queryhit mesg, and forward back\nsimpella >>");
			      			BufferedOutputStream out_pong=new BufferedOutputStream(GUID.get(queryhit_wrap).getOutputStream());
			      			out_pong.write(queryhit);
			      			out_pong.flush();
			      		}else{
			      			byte[] ipByte = new byte[4];
			      			System.arraycopy(queryhit, 26, ipByte, 0, 4);
			      			String dest_ip = getIP(ipByte);
			      			int dest_port = (int)(ByteToInt(queryhit[24])*Math.pow(2, 8)+ByteToInt(queryhit[25]));
			      			int filenum = queryhit[23];
			      			//System.out.print("got a queryhit mesg,file num = "+filenum+"\n");

			      			byte[] filelist = new byte[count-34];
			      			System.arraycopy(queryhit, 34, filelist, 0, count-34);
			      			
			      			int index = 0;
			      			//System.out.print("\nquery results:\n");
			      			while(filenum!=0){
			      				int fileindex = (int)(ByteToInt(filelist[index]) * Math.pow(2, 24)+ByteToInt(filelist[index+1]) * Math.pow(2, 16)+ByteToInt(filelist[index+2]) * Math.pow(2, 8)+ByteToInt(filelist[index+3]));
			      				double fileSize = ByteToInt(filelist[index+4]) * Math.pow(2, 24)+ByteToInt(filelist[index+5]) * Math.pow(2, 16)+ByteToInt(filelist[index+6]) * Math.pow(2, 8)+ByteToInt(filelist[index+7]);
			      				int fileNameLength = filelist[index+8];
			      				byte[] nameByte = new byte[fileNameLength];
			      				System.arraycopy(filelist, index+9, nameByte,0, fileNameLength);
			      				String filename = new String(nameByte);
			      				ReturnFile file = new ReturnFile(filename, fileindex,fileSize,dest_ip,dest_port);
			      				fileList.add(file);
			      				index = index+9+fileNameLength;
			      				filenum--;
			      				System.out.print(file.getIp()+":"+file.getPort()+"     Size: "+file.getFileSize()+"Bytes\nName: "+file.getFileName()+"\n");
			      			}
			      		}
			      	}
			      	else if(mesg.indexOf("quit") !=-1){
			      		byte[] ping_id = new byte[16];
			      		System.arraycopy(b, 0, ping_id, 0, 16);
			      		ByteArrayWrapper ping_wrap = new ByteArrayWrapper(ping_id);
			      		BufferedOutputStream outc=new BufferedOutputStream(map.get(ping_wrap).getOutputStream());
						outc.write(ping_id);
						outc.write("quit\n".getBytes());
						outc.flush();
			      		map.get(ping_wrap).close();
			      		map.remove(ping_wrap);
			      		GUID.remove(ping_wrap);
			      		query_set.remove(ping_wrap);
			      		break;
			      	}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
			}
		}
		
	}
	public class tcp_thread implements Runnable {
		private Socket connection;
		private byte[] mesg_id;
		public tcp_thread(Socket connection,byte[] mesg_id){
			this.connection = connection;
			this.mesg_id = mesg_id;
		}
		public void run(){
			try {
				BufferedOutputStream out=new BufferedOutputStream(connection.getOutputStream());
				byte[] pass_mesg = new byte[16];
				System.arraycopy(mesg_id, 0, pass_mesg, 0, 16);
				out.write(pass_mesg);
				out.flush();
				while(true){
					BufferedInputStream in=new BufferedInputStream(connection.getInputStream());
					byte[] b = new byte[4096];
					int count = in.read(b);
					String mesg  = new String(b,0,count);//= b.toString();
					//wait for reading the message
			      	if(mesg.indexOf("CONNECT") >=0 ){
			      		System.out.print(mesg+"simpella >>");
			      		if(incom_num<3){
			      			BufferedWriter bwout=new BufferedWriter(new OutputStreamWriter(connection.getOutputStream()));
			      			bwout.write("SIMPELLA/0.6 200 <OK>\r\n");
			      			bwout.flush();
			      		}
			      		else{
			      			BufferedWriter bwout=new BufferedWriter(new OutputStreamWriter(connection.getOutputStream()));
			      			bwout.write("SIMPELLA/0.6 503 Maximum number of connections reached. Sorry!\r\n");
			      			bwout.flush();
			      			connection.close();
			      			break;
			      		}
			      	}
			      	else if(mesg.indexOf("200") >=0 ){
			      		System.out.print(mesg+"simpella >>");
			      		BufferedInputStream in_mesg=new BufferedInputStream(connection.getInputStream());
						byte[] ping = new byte[23];
						in_mesg.read(ping);
						byte[] ping_id = new byte[16];
						System.arraycopy(ping, 0, ping_id, 0, 16);
						ByteArrayWrapper ping_wrap = new ByteArrayWrapper(ping_id);
						incom_num++;
		    			map.put(ping_wrap,connection);
			      		if( ping[16] == 0x00 ){
			      			byte[] pong = gen_pong(ping,ip,simpella_port);
				      		//if(!GUID.containsKey(ping_wrap)){
				      			GUID.put(ping_wrap,connection);
				      			if(ping[17] !=0){
					      			p2p_flush(ping,connection);
					      		}
				      		//}
				      		if(GUID.containsKey(ping_wrap)){
				      			System.out.print("neighbor peer!forward back pong mesg\nsimpella >>");
				      			BufferedOutputStream out_pong=new BufferedOutputStream(GUID.get(ping_wrap).getOutputStream());
				      			out_pong.write(pong);
				      			out_pong.flush();
				      		}
				      		
			      		}
			      	}
			      	else if( b[16] == 0x00 ){//ping message
			      		byte[] ping_id = new byte[16];
			      		byte[] ping = new byte[23];
			      		System.arraycopy(b, 0, ping_id, 0, 16);
			      		System.arraycopy(b, 0, ping, 0, 23);
			      		ByteArrayWrapper ping_wrap = new ByteArrayWrapper(ping_id);
			      		//System.out.print(ping_id[8]);
			      		byte[] pong = gen_pong(ping,ip, simpella_port);
			      		//if(!GUID.containsKey(ping_wrap)){
			      				GUID.put(ping_wrap,connection);
			      				if(ping[17] !=0){
				      				p2p_flush(ping,connection);
				      			}
			      		//}
			      		if(GUID.containsKey(ping_wrap)){
			      			
			      			System.out.print("got a ping mesg, genarate pong mesg and forward back\nsimpella >>");
			      			BufferedOutputStream out_pong=new BufferedOutputStream(GUID.get(ping_wrap).getOutputStream());
			      			out_pong.write(pong);
			      			out_pong.flush();
			      		}
			      		
			      	}
			      	else if( b[16] == 0x01 ){//pong message
			      		byte[] pong_id = new byte[16];
			      		byte[] pong = new byte[37];
			      		System.arraycopy(b, 0, pong_id, 0, 16);
			      		System.arraycopy(b, 0, pong, 0, 37);
			      		ByteArrayWrapper pong_wrap = new ByteArrayWrapper(pong_id);
			      		if(GUID.containsKey(pong_wrap)){
			      			System.out.print("got a pong mesg, and forward back\nsimpella >>");
			      			BufferedOutputStream out_pong=new BufferedOutputStream(GUID.get(pong_wrap).getOutputStream());
			      			out_pong.write(pong);
			      			out_pong.flush();
			      		}else{
			      			hostNum ++; 
			      			peerFileSize = (int)(ByteToInt(pong[33]) * Math.pow(2, 24)+ByteToInt(pong[34]) * Math.pow(2, 16)+ByteToInt(pong[35]) * Math.pow(2, 8)+ByteToInt(pong[36]));
			      			peerFileNum = (int)(ByteToInt(pong[29]) * Math.pow(2, 24)+ByteToInt(pong[30]) * Math.pow(2, 16)+ByteToInt(pong[31]) * Math.pow(2, 8)+ByteToInt(pong[32]));
			      			System.out.print("receive a pong message!\nsimpella >>");
			      		}
			      	}
			      	else if( b[16] == (byte)0x80 ){//query message
			      		ReceivedQuery++;
			      		byte[] query = new byte[23];
			      		byte[] query_id = new byte[16];
			      		String query_str = new String(b,25,count-25);
			      		SearchQuery.add(query_str);
			      		System.out.print("query: "+query_str+"\nsimpella >>");
			      		System.arraycopy(b, 0, query_id, 0, 16);
			      		System.arraycopy(b, 0, query, 0, 23);
			      		
			      		byte[] flush_query = gen_query(query,query_str);
			      		ByteArrayWrapper query_wrap = new ByteArrayWrapper(query_id);
			      		String[] querys = query_str.split(" ");
			      		Map<String,byte[]> matchfile = new HashMap<String,byte[]>();
			      		for(int i=0;i<querys.length;i++){
			      			for(int j=0;j<fileName.length;j++){
			      				String filepath = dir+"/"+fileName[j];
			      				File file = new File(filepath);
			      				if(file.isFile()){
			      					if(fileName[j].indexOf(querys[i]) !=-1 && !matchfile.containsKey(fileName[j])){
			      						byte[] fname = fileName[j].getBytes();
			      						byte[] filebytes = new byte[9+fname.length];
			      						filebytes[3] = (byte)(j & 0x000000FF);
			      						filebytes[2] = (byte)((j & 0x0000FF00) >> 8);
			      						filebytes[1] = (byte)((j & 0x00FF0000) >> 16);
			      						filebytes[0] = (byte)((j & 0xFF000000) >> 24);
			      						FileInputStream fi = new FileInputStream(file);   
										int size = fi.available();
										fi.close();
										filebytes[7] = (byte)(size & 0x000000FF);
			      						filebytes[6] = (byte)((size & 0x0000FF00) >> 8);
			      						filebytes[5] = (byte)((size & 0x00FF0000) >> 16);
			      						filebytes[4] = (byte)((size & 0xFF000000) >> 24);
			      						filebytes[8] = (byte)fname.length;
			      						System.arraycopy(fname, 0, filebytes, 9, fname.length);
			      						matchfile.put(fileName[j],filebytes);
			      					}
			      				}
			      			}
			      		}
			      		byte[] query_hit = gen_queryhit(query,matchfile);
			      		//if(!query_set.containsKey(query_wrap)){
		      			query_set.put(query_wrap,connection);
		      			if(flush_query[17] !=0){
			      			p2p_flush(flush_query,connection);
			      		}
			      		if(query_set.containsKey(query_wrap)&& (query_hit != null)){	
			      			RespondQuery++;
			      			System.out.print("find query, forwards back!\nsimpella >>");
			      			BufferedOutputStream out_query=new BufferedOutputStream(query_set.get(query_wrap).getOutputStream());
			      			out_query.write(query_hit);
			      			out_query.flush();
			      		}
			      	}
			      	else if( b[16] == (byte)0x81 ){//queryhit message
			      		byte[] queryhit_id = new byte[16];
			      		byte[] queryhit = new byte[count];
			      		System.arraycopy(b, 0, queryhit_id, 0, 16);
			      		System.arraycopy(b, 0, queryhit, 0, count);
			      		ByteArrayWrapper queryhit_wrap = new ByteArrayWrapper(queryhit_id);
			      		if(GUID.containsKey(queryhit_wrap)){
			      			System.out.print("got a queryhit mesg, and forward back\nsimpella >>");
			      			BufferedOutputStream out_pong=new BufferedOutputStream(GUID.get(queryhit_wrap).getOutputStream());
			      			out_pong.write(queryhit);
			      			out_pong.flush();
			      		}else{
			      			byte[] ipByte = new byte[4];
			      			System.arraycopy(queryhit, 26, ipByte, 0, 4);
			      			String dest_ip = getIP(ipByte);
			      			int dest_port = (int)(ByteToInt(queryhit[24])*Math.pow(2, 8)+ByteToInt(queryhit[25]));
			      			int filenum = queryhit[23];
			      			byte[] filelist = new byte[count-34];
			      			System.arraycopy(queryhit, 34, filelist, 0, count-34);
			      			
			      			int index = 0;
			      			//System.out.print("\nquery results:\n");
			      			while(filenum!=0){
			      				int fileindex = (int)(ByteToInt(filelist[index]) * Math.pow(2, 24)+ByteToInt(filelist[index+1]) * Math.pow(2, 16)+ByteToInt(filelist[index+2]) * Math.pow(2, 8)+ByteToInt(filelist[index+3]));
			      				double fileSize = ByteToInt(filelist[index+4]) * Math.pow(2, 24)+ByteToInt(filelist[index+5]) * Math.pow(2, 16)+ByteToInt(filelist[index+6]) * Math.pow(2, 8)+ByteToInt(filelist[index+7]);
			      				int fileNameLength = filelist[index+8];
			      				byte[] nameByte = new byte[fileNameLength];
			      				System.arraycopy(filelist, index+9, nameByte,0, fileNameLength);
			      				String filename = new String(nameByte);
			      				ReturnFile file = new ReturnFile(filename, fileindex,fileSize,dest_ip,dest_port);
			      				fileList.add(file);
			      				index = index+9+fileNameLength;
			      				filenum--;
			      				System.out.print(file.getIp()+":"+file.getPort()+"     Size: "+file.getFileSize()+"Bytes\nName: "+file.getFileName()+"\n");
			      			}
			      			
			      		}
			      	}
			      	else if(mesg.indexOf("quit") !=-1){
			      		byte[] ping_id = new byte[16];
			      		System.arraycopy(b, 0, ping_id, 0, 16);
			      		ByteArrayWrapper ping_wrap = new ByteArrayWrapper(ping_id);
			      		BufferedOutputStream outc=new BufferedOutputStream(map.get(ping_wrap).getOutputStream());
						outc.write(ping_id);
						outc.write("quit\n".getBytes());
						outc.flush();
			      		map.get(ping_wrap).close();
			      		map.remove(ping_wrap);
			      		GUID.remove(ping_wrap);
			      		query_set.remove(ping_wrap);
			      		break;
			      	}
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} 
	    }
	}
	public class download_listen implements Runnable{
		private ServerSocket socket;
		public download_listen(ServerSocket s) {
		  this.socket = s;
		}
		public void run() {
		    try {
		    	while (true) {
		    		//every time there is a tcp connection comming, create a new thread to handkle it
		    		Socket connection = socket.accept();
		    		download_thread runnable = new download_thread(connection);
			        Thread thread = new Thread(runnable);
			        thread.start();
			    }
		    }
		    catch (IOException e){}
		}
	}
	public class download_thread implements Runnable {
		private int fileindex;
		private Socket connection;
		public download_thread(Socket connection){
			this.connection = connection;
		}
		public void run(){
			try {
				while(true){
					//wait for reading the message
					BufferedInputStream in=new BufferedInputStream(connection.getInputStream());
					byte[] b = new byte[4096];
					int count = in.read(b);
					String mesg = new String(b,0,count);
					if(mesg.indexOf("GET")!=-1){
						String[] str = mesg.split("/");
						fileindex = Integer.parseInt(str[2]);
						String filename = str[3].split(" ")[0];
						BufferedOutputStream out=new BufferedOutputStream(connection.getOutputStream());
						if(fileName[fileindex].equals(filename)){
							String filepath = dir+"/"+filename;
							File file = new File(filepath);
							double filesize = file.length();
							out.write(("HTTP/1.1 200 OK\r\n"+
									   "Server: Simpella0.6\r\n"+
									   "Content-type: application/binary\r\n"+
									   "Content-length: "+filesize+"\r\n"+
									   "\r\n").getBytes());
							out.flush();
							
						}
						else{
							out.write(("HTTP/1.1 503 File not found.\r\n"+
									   "\r\n").getBytes());
							out.flush();
						}	
					}
					else if(mesg.indexOf("download")!=-1){
						String filepath = dir+"/"+fileName[fileindex];
				        FileInputStream is = new FileInputStream(filepath);  
				        OutputStream os = connection.getOutputStream(); 
				        //File fileout = new File(filepath); 
				        byte[] buffer = new byte[4096];  
				        int size;  
				        while ((size = is.read(buffer)) != -1) {  
				            os.write(buffer, 0, size);
				        }  
				        os.flush();
				        os.close();
				        is.close();
				        break;
					}
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	      	
	    }
	}

	public String getIP(byte[] ip){
		String ipaddr = ByteToInt(ip[0])+"."+ByteToInt(ip[1])+"."+ByteToInt(ip[2])+"."+ByteToInt(ip[3]);
		return ipaddr;
	}
	public void p2p_flush(byte[] ping,Socket connection) throws IOException{
			ping[17] -= 1;
			ping[18] += 1;
			Set<ByteArrayWrapper> idset = map.keySet();
			byte[] id = new byte[16];
			System.arraycopy(ping, 0, id, 0, 16);
			for(ByteArrayWrapper i : idset){
				if(map.get(i) != connection && !Arrays.equals(i.getb(), id)){
					BufferedOutputStream out=new BufferedOutputStream(map.get(i).getOutputStream());
					out.write(ping);
					out.flush();
				}
			}	
	}
	public int ByteToInt(byte b){
		int val = b;
		if(val<0){
			val = 256+val;
		}
		return val;
	}
	
	public byte[] gen_ping(){
		byte[] ping = new byte[23];
		//first 16 byte, randomly generate message ID
		for(int i=0;i<16;i++){
			int rand = (int)(Math.random() * 127);
			ping[i] = (byte)rand;
			mesg_ID[i] = (byte)rand;
		}
		ping[8] = 127;
		mesg_ID[8] =127;
		ping[15] = 0;
		mesg_ID[15] = 0;
		//17th byte
		ping[16] = 0x00;
		//TTL
		ping[17] = 0x07;
		//hops
		ping[18] = 0x00;
		//PL
		ping[19] = 0x00;
		ping[20] = 0x00;
		ping[21] = 0x00;
		ping[22] = 0x00;
		return ping;
	}
	public byte[] gen_pong(byte[] ping,String ip, int port){
		byte[] pong = new byte[37];
  		System.arraycopy(ping, 0, pong, 0, 23);
  		pong[16] = 0x01;
  		int left_port = (port & 0xFF00) >> 8;
		int right_port = port & 0x00FF;
  		pong[23] = (byte) left_port;
  		pong[24] = (byte) right_port;
  		String[] ip_seg = ip.split("\\.");
  		pong[25] = (byte)Integer.parseInt(ip_seg[0]);
  		pong[26] = (byte)Integer.parseInt(ip_seg[1]);
  		pong[27] = (byte)Integer.parseInt(ip_seg[2]);
  		pong[28] = (byte)Integer.parseInt(ip_seg[3]);
  		int sizeOfKb = shareFileSize / 1024;
  		pong[32] = (byte)(shareFileNum & 0x000000FF);
		pong[31] = (byte)((shareFileNum & 0x0000FF00) >> 8);
		pong[30] = (byte)((shareFileNum & 0x00FF0000) >> 16);
		pong[29] = (byte)((shareFileNum & 0xFF000000) >> 24);
		pong[36] = (byte)(sizeOfKb & 0x000000FF);
		pong[35] = (byte)((sizeOfKb & 0x0000FF00) >> 8);
		pong[34] = (byte)((sizeOfKb & 0x00FF0000) >> 16);
		pong[33] = (byte)((sizeOfKb & 0xFF000000) >> 24);
  		return pong;
	}
	public byte[] gen_query(byte[] ping,String query){
		byte[] str = query.getBytes();
		byte[] query_str = new byte[23+2+str.length];
  		System.arraycopy(ping, 0, query_str, 0, 23);
  		System.arraycopy(str, 0, query_str, 25, str.length);
  		query_str[16] = (byte)0x80;
  		query_str[23] = 0x00;
  		query_str[24] = 0x00;
  		return query_str;
	}
	
	public byte[] gen_queryhit(byte[] query,Map<String,byte[]> matchfile){
		int size = 0;
  		for(String i: matchfile.keySet()){
  			size += matchfile.get(i).length; 			
  		}
  		if(size == 0){
  			return null;
  		}
		byte[] hit = new byte[34+size];
  		System.arraycopy(query, 0, hit, 0, 23);
  		hit[16] = (byte)0x81;
  		int left_port = (download_port & 0xFF00) >> 8;
		int right_port = download_port & 0x00FF;
		hit[23] = (byte)matchfile.size();
  		hit[24] = (byte) left_port;
  		hit[25] = (byte) right_port;
  		String[] ip_seg = ip.split("\\.");
  		hit[26] = (byte)Integer.parseInt(ip_seg[0]);
  		hit[27] = (byte)Integer.parseInt(ip_seg[1]);
  		hit[28] = (byte)Integer.parseInt(ip_seg[2]);
  		hit[29] = (byte)Integer.parseInt(ip_seg[3]);
  		hit[30] = 0x00;// set 0 temporily
  		hit[31] = 0x00;
  		hit[32] = 0x00;
  		hit[33] = 0x00;
  		int index=34;
  		for(String i: matchfile.keySet()){ 			
  			System.arraycopy(matchfile.get(i), 0, hit, index, matchfile.get(i).length);
  			index += matchfile.get(i).length;
  		}		
		return hit;
	}
	
	
	
	public static void main(String[] args) throws InterruptedException{
		server server = new server();
		if(!(args.length == 2 || args.length == 0) ){
			System.out.println("with argument of <port1> <port2>");
			System.exit(0);
		}
		if(args.length == 2){
			if(args[0].matches("\\d+") && args[1].matches("\\d+")){
				server.simpella_port = Integer.parseInt(args[0]);
				server.download_port = Integer.parseInt(args[1]);
			}
		}
		try {
			ServerSocket socket = new ServerSocket(server.simpella_port);
			ServerSocket download_socket = new ServerSocket(server.download_port);
			server.ping_mesg  = server.gen_ping();
			System.arraycopy(server.ping_mesg, 0, server.mesg_ID, 0, 16);
			//get real ip addr
			Enumeration<NetworkInterface> ifaces =  NetworkInterface.getNetworkInterfaces();
	        NetworkInterface iface = ifaces.nextElement();
	        for (Enumeration<InetAddress> addresses = iface.getInetAddresses();addresses.hasMoreElements(); ){
	        	InetAddress address = addresses.nextElement();
	            if(address.toString().split("/")[1].matches("\\b((?!\\d\\d\\d)\\d+|1\\d\\d|2[0-4]\\d|25[0-5])\\.((?!\\d\\d\\d)\\d+|1\\d\\d|2[0-4]\\d|25[0-5])\\.((?!\\d\\d\\d)\\d+|1\\d\\d|2[0-4]\\d|25[0-5])\\.((?!\\d\\d\\d)\\d+|1\\d\\d|2[0-4]\\d|25[0-5])\\b")){
	            	server.ip = address.toString().split("/")[1];
		    	    break;
	            }
	        }
	        System.out.println("Local IP: "+server.ip);
	        System.out.println("Simpella Net Port: "+server.simpella_port);
	        System.out.println("Downloading Port: "+server.download_port);
	        System.out.println("simpella version 0.6 (c) 20012-2013 Gaoqi Luo");
	        listener tcp_thread = server.new listener(socket,server.mesg_ID);
	        Thread tcp_incomes = new Thread(tcp_thread);
	        tcp_incomes.start();
	        download_listen dl_thread = server.new download_listen(download_socket);
	        Thread dl = new Thread(dl_thread);
	        dl.start();
	        
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		while(true){	
			//do a while loop to wait for user input
			System.out.print("simpella >>");
			String[] command;
			//readin the user command
			String str;
	        BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
	        try {
				str = input.readLine();
				command = str.split(" ");
				//command: 'info'
				if(command[0].equals("info") && command.length == 2){
					if(command[1].equals("c")){
						System.out.println("CONNECTION STATS:\n----------");
						int count=0;
						for(ByteArrayWrapper i:server.map.keySet()){
							count++;
							String remote_ip = server.map.get(i).getInetAddress().getHostAddress();
							int remote_port = server.map.get(i).getPort();
							System.out.println(count+") "+remote_ip+":"+remote_port);
							
						}
					}
					else if(command[1].equals("d")){
						
					}
					else if(command[1].equals("h")){
						System.out.println("HOST STATS:\n----------");
						System.out.println("  Host:"+server.hostNum+"     Files:"+server.peerFileNum+"     Size:"+server.peerFileSize+"kb");
					}
					else if(command[1].equals("n")){
						
					}
					else if(command[1].equals("q")){
						System.out.println("QUERY STATS:\n----------");
						System.out.println("  Queries: "+server.ReceivedQuery+"    Responses: "+server.RespondQuery);
					}
					else if(command[1].equals("s")){
						System.out.println("SHARE STATS:\n----------");		
						System.out.println("  Num Shared: "+server.shareFileNum+"  Size Shared: "+server.shareFileSize+" bytes");
					}else{
						System.out.println("Unknown option!");
					}
				}
				else if(command[0].equals("share")){
					if(command[1].equals("-i")){
						System.out.println("  Sharing "+server.dir);
					}
					else{
						if(command[1].charAt(0) != (char)47){
							File directory = new File("");
							String cur_path = directory.getAbsolutePath();
							String dir_path = cur_path+"/"+command[1];
							File dir = new File(dir_path);
							if(dir.isDirectory()){
								server.dir = dir_path;
								server.fileName = dir.list();
								File[] allfile = dir.listFiles();
								server.shareFileNum=0;
								server.shareFileSize=0;
								FileInputStream fis;
								for(int i=0;i<allfile.length;i++){
									if(allfile[i].isFile()){
										fis = new FileInputStream(allfile[i]);   
										server.shareFileSize += fis.available();
										server.shareFileNum++;
										fis.close();
									}
								}
							}
							else{
								System.out.println("  Not a directory!");
							}
						}else{
							
							File dir = new File(command[1]);
							//System.out.println(dir.getPath());
							if(dir.isDirectory()){
								server.dir = command[1];
								server.fileName = dir.list();
								File[] allfile = dir.listFiles();
								server.shareFileNum=0;
								server.shareFileSize=0;
								FileInputStream fis;
								for(int i=0;i<allfile.length;i++){
									if(allfile[i].isFile()){
										fis = new FileInputStream(allfile[i]);   
										server.shareFileSize += fis.available();
										server.shareFileNum++;
										fis.close();
									}
								}
							}
							else{
								System.out.println("  Not a directory!");
							}
						}
						
					}
				}
				else if(command[0].equals("scan")){
					System.out.println("  Scanning "+server.dir+" for files ...");		
					System.out.println("  Scanned "+server.shareFileNum+" files and "+server.shareFileSize+" bytes");					
				}
				else if(command[0].equals("open")){
					server.hostNum = 0;
					server.peerFileNum = 0;
					server.peerFileSize = 0;
					String [] host_port = command[1].split(":");
					String host = host_port[0];
					int port = Integer.parseInt(host_port[1]);
					InetAddress address = InetAddress.getByName(host);
					if(server.outcom_num < 3){
						Socket connection = new Socket(address, port);
						System.out.printf("Trying to connect to "+connection.getInetAddress().getHostAddress()+"\n");
						BufferedInputStream in=new BufferedInputStream(connection.getInputStream());
						byte[] dest_id = new byte[16];
						in.read(dest_id);
						ByteArrayWrapper dest_wrap = new ByteArrayWrapper(dest_id);
						if(!server.map.containsKey(dest_wrap)){
							BufferedOutputStream bout=new BufferedOutputStream(connection.getOutputStream());
							bout.write(("SIMPELLA CONNECT/0.6\r\n").getBytes());
							bout.flush();
						}
						else{
							System.out.println("Duplicate TCP connection!");
//							connection.close();
							continue;
						}
						
						BufferedReader brin=new BufferedReader(new InputStreamReader(connection.getInputStream()));
						String mesg = brin.readLine().toString();
						System.out.println(mesg);
						if(mesg.indexOf("200") >=0){
							BufferedOutputStream out=new BufferedOutputStream(connection.getOutputStream());
							out.write(("SIMPELLA/0.6 200 <OK>\r\n").getBytes());
							out.flush();
							server.map.put(dest_wrap,connection);
							server.outcom_num ++;
							selector selector = server.new selector(connection);
						    Thread sel_thread = new Thread(selector);
						    sel_thread.start();
							//if(server.incom_num ==0 && server.outcom_num == 1){
							BufferedOutputStream out_ping=new BufferedOutputStream(connection.getOutputStream());
							byte[] ping = new byte[23];
							System.arraycopy(server.ping_mesg, 0, ping, 0, 23);
							out_ping.write(ping);
							out_ping.flush();
								
							//}
						}
						else if(mesg.indexOf("503") >=0){
							connection.close();
						}
					}
		        	else{
		        			System.out.println("  Only 3 out-going connections are allowed, reaches the limitaion!");
		        	}					
				}
				else if(command[0].equals("update")){
					server.hostNum = 0;
					server.peerFileNum = 0;
					server.peerFileSize = 0;
					byte[] ping = new byte[23];
					System.arraycopy(server.ping_mesg, 0, ping,0,23);
					//ByteArrayWrapper ping_wrap = new ByteArrayWrapper(server.mesg_ID);
					ping[17] -= 1;
					ping[18] += 1;
					Set<ByteArrayWrapper> idset = server.map.keySet();
					//int count = 0;
					for(ByteArrayWrapper i : idset){
						BufferedOutputStream out=new BufferedOutputStream(server.map.get(i).getOutputStream());
						out.write(ping);
						out.flush();
						//count++;
					}	
				}
				else if(command[0].equals("find")){
					String query = str.substring(str.indexOf(command[1]));
					byte[] query_str = server.gen_query(server.ping_mesg, query);
					query_str[17] -= 1;
					query_str[18] += 1;
					Set<ByteArrayWrapper> idset = server.map.keySet();
					for(ByteArrayWrapper i : idset){
							BufferedOutputStream out=new BufferedOutputStream(server.map.get(i).getOutputStream());
							out.write(query_str);
							out.flush();
					}
					System.out.println("The query was '"+query+"':");
					System.out.println("press ENTER to continue\n--------------------");
					BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
					String s = in.readLine();
					
				}
					
				else if(command[0].equals("list")){
					System.out.print("File list:\n");
					int index=1;
					for(ReturnFile i:server.fileList){
						System.out.print(index+") "+i.getIp()+":"+i.getPort()+"     Size: "+i.getFileSize()+"Bytes\n   Name: "+i.getFileName()+"\n");
						index++;
					}
				}
				else if(command[0].equals("clear")){
					if(command.length == 1){
						server.fileList.clear();
					}else{
						int file_num = Integer.parseInt(command[1]);
						if(file_num > server.fileList.size()){
							System.out.println("File number out of bound!");
							continue;
						}
						server.fileList.remove(file_num-1);
					}
					
					
				}
				else if(command[0].equals("download")){
					int file_num = Integer.parseInt(command[1]);
					ReturnFile file = server.fileList.get(file_num-1);
					int fileindex = file.getFileIndex();
					String filename = file.getFileName();
					String ipaddr = file.getIp();
					int port = file.getPort();
					String ip_port = ipaddr+":"+port;
					InetAddress address = InetAddress.getByName(ipaddr);
					Socket connection = new Socket(address, port);
					BufferedOutputStream out=new BufferedOutputStream(connection.getOutputStream());
					out.write(("GET /get/"+fileindex+"/"+filename+" HTTP/1.1\r\n" +
							   "User-Agent: Simpella\r\n"+
							   "Host: "+ip_port+"\r\n"+
							   "Connection: Keep-Alive\r\n"+
							   "Range: bytes=0-\r\n \r\n").getBytes());
					out.flush();
					BufferedInputStream in=new BufferedInputStream(connection.getInputStream());
					byte[] b = new byte[4096];
					int count = in.read(b);
					String mesg = new String(b,0,count);
					if(mesg.indexOf("200")!=-1){
						download_accept download_accept = new download_accept(connection,filename);
				        Thread download = new Thread(download_accept);
				        download.start();
				        BufferedOutputStream ot=new BufferedOutputStream(connection.getOutputStream());
				        ot.write("download\n".getBytes());
				        ot.flush();
					}
					
				}
				else if(command[0].equals("monitor")){
					System.out.println("MONITORING SIMPELLA NETWORK:");
					System.out.println("press ENTER to continue\n--------------------");
					for(String i:server.SearchQuery){
						System.out.println("Search: '"+i+"'");
					}
					BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
					String s = in.readLine();
				}
				else if(command[0].equals("quit")){
					for(ByteArrayWrapper i: server.map.keySet()){
						BufferedOutputStream out=new BufferedOutputStream(server.map.get(i).getOutputStream());
						out.write(server.mesg_ID);
						out.write("quit\n".getBytes());
						out.flush();
					}
					System.exit(0);
				}
				else{
					System.out.print("  Unknown command!\n");
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}

	}
}
