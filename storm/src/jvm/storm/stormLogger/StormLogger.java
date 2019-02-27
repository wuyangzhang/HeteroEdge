package storm.stormLogger;

import java.util.logging.Logger;
import java.util.logging.FileHandler;
import java.io.File;
import storm.topology.*;

import java.net.*;

public class StormLogger {
	private Logger Log;
	final private String logFile;
	final private String logName;

	private static final String serverIp = TopologyParameters.serverIP;
	private static final int serverPort = TopologyParameters.serverPort;


	public StormLogger(String logFileAddr, String logName) {
		this.logFile = logFileAddr;
		this.logName = logName;
		initLogger();
	}

	private void initLogger() {
		try {
			FileHandler handler = new FileHandler(logFile, true);
			Log = Logger.getLogger(logName);
			Log.addHandler(handler);
		} catch (java.io.IOException ex1) {
			System.out.println("[Error] Failt to open" + logFile);
		}
	}


	public static void sendMessage(String message){
        try{
            DatagramSocket clientSocket = new DatagramSocket();
            InetAddress IPAddress = InetAddress.getByName(serverIp);
            byte[] sendData = message.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, serverPort);
            clientSocket.send(sendPacket);
            sendPacket = null;
            clientSocket.close();
            clientSocket = null;
        }catch(Exception e){

        }
    }

	public Logger getLogger(){
		return this.Log;
	}
}