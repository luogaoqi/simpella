package simpella;

public class ReturnFile {
	private String fileName;
	private double fileSize;
	private String ip;
	private int port;
	private int fileIndex;
	public ReturnFile(String fileName, int fileindex,double fileSize, String ip, int port){
		this.fileName = fileName;
		this.fileSize = fileSize;
		this.ip = ip;
		this.port = port;
		this.fileIndex = fileindex;
	}
	public String getFileName(){
		return fileName;
	}
	public double getFileSize(){
		return fileSize;
	}
	public String getIp(){
		return ip;
	}
	public int getPort(){
		return port;
	}
	public int getFileIndex(){
		return fileIndex;
	}
}
