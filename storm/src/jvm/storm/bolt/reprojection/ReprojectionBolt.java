package storm.bolt.reprojection;

import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.StormSubmitter;
import org.apache.storm.task.ShellBolt;
import org.apache.storm.spout.SpoutOutputCollector;
import org.apache.storm.topology.BasicOutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.base.BaseRichSpout;
import org.apache.storm.utils.Utils;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.topology.IRichBolt;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.topology.base.BaseBasicBolt;
import org.apache.storm.topology.base.BaseRichBolt;

import org.apache.storm.scheduler.Cluster;
import org.apache.storm.scheduler.EvenScheduler;
import org.apache.storm.scheduler.ExecutorDetails;
import org.apache.storm.scheduler.IScheduler;
import org.apache.storm.scheduler.SupervisorDetails;
import org.apache.storm.scheduler.Topologies;
import org.apache.storm.scheduler.TopologyDetails;
import org.apache.storm.scheduler.WorkerSlot;

import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.Map;
import java.util.logging.Logger;
import java.io.*;
import java.net.*;

import topologyResourceConsumption.*;
import reconstructionAPI.*;
import storm.stormLogger.*;
import storm.spout.StereoPartition;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ReprojectionBolt extends BaseRichBolt {

	final private String boltName;
	private String[] inFieldsName;
	private String[] outFieldsName;
	private OutputCollector collector;

	final private String calibrationFileAddr, rectificationFileAddr;
	final private int imgRows, imgCols;
	private byte[] disparity;
	private byte[] Q;
	private TopologyResourceConsumption trc;
	private boolean isCheckResourceConsumption;
	private Logger log;
	private String logFile;

	private String serverIp;
	private int serverPort;

	public ReprojectionBolt(String boltName, String[] inFieldsName, String[] outFieldsName, String calibrationFileAddr, String rectificationFileAddr, int imgRows,
	                        int imgCols, boolean isCheckResourceConsumption, String logFile, String serverIp, int serverPort) {

		this.boltName = boltName;
		this.inFieldsName = inFieldsName;
		this.outFieldsName = outFieldsName;
		this.calibrationFileAddr = calibrationFileAddr;
		this.rectificationFileAddr = rectificationFileAddr;
		this.imgRows = imgRows;
		this.imgCols = imgCols;
		this.isCheckResourceConsumption = isCheckResourceConsumption;
		this.logFile = logFile;
		this.serverIp = serverIp;
		this.serverPort = serverPort;
	}

	@Override
	public void prepare(Map config, TopologyContext context, OutputCollector collector) {
		this.collector = collector;
		disparity = new byte[imgRows * imgCols * 3];
		byte[][] map = ReconstructionAPI.initImageRectification(calibrationFileAddr, rectificationFileAddr, imgRows, imgCols);
		Q = map[4];

		if (isCheckResourceConsumption) {
			log = new StormLogger(logFile, "reconstruction").getLogger();
			trc = new TopologyResourceConsumption(boltName, log);
		}
	}

	@Override
	public void execute(Tuple tuple) {


		int imgId = tuple.getIntegerByField(inFieldsName[0]);
		if (isCheckResourceConsumption && imgId % 1 == 0) {
			trc.updateConsumingResource(imgId);
		}
		int subImgId = tuple.getIntegerByField(inFieldsName[1]);
		byte[] disparity = tuple.getBinaryByField(inFieldsName[2]);
		byte[] xyz = ReconstructionAPI.reprojectTo3D(disparity, Q, imgRows, imgCols);
		this.collector.emit(outFieldsName[0], new Values(imgId, subImgId, xyz));
		
		if(imgId % 1 == 0){
				//this.collector.emit("overallLatencyTuples", new Values(imgId, trc.getElapseTime()));
				try{
					DatagramSocket clientSocket = new DatagramSocket();
		      		InetAddress IPAddress = InetAddress.getByName(serverIp);
		      		//byte[] sendData = new byte[100];
		      		//sendData = String.valueOf(imgId).getBytes();
		      		String log_s = boltName + " " + imgId + " " + trc.elapseTimeDiff(); 
		      		byte[] sendData = log_s.getBytes();
		      		DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, serverPort);
		      		clientSocket.send(sendPacket);
		      		sendPacket = null;
		      		clientSocket.close();
		      		clientSocket = null;
				}catch(Exception e){
					
				}
				
		}

		this.collector.ack(tuple);

		if (isCheckResourceConsumption && imgId % 1 == 0) {
			trc.setInOutputSize(disparity.length, xyz.length);
			trc.reportConsumingResource(imgId);
		}
	}

	@Override
	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		declarer.declareStream(outFieldsName[0], new Fields(outFieldsName[1], outFieldsName[2], outFieldsName[3]));
		//declarer.declareStream("overallLatencyTuples", new Fields(outFieldsName[1], "overallLatency"));
	}

}
