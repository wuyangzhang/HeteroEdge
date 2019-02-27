package storm.bolt.partition;

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
import java.util.ArrayList;
import java.util.List;
import topologyResourceConsumption.*;
import reconstructionAPI.*;
import storm.stormLogger.*;
import storm.spout.StereoPartition;
import java.net.*;

public class PartitionBolt extends BaseRichBolt {

	final private String boltName;
	final private String[] inFieldsName;
	final private String[] outFieldsName;

	private OutputCollector collector;

	final private int imgRows, imgCols;
	final private int partitionNum;
	final private int upCompensationRowNum, downCompensationRowNum;

	private TopologyResourceConsumption trc;
	private boolean isCheckResourceConsumption;
	private Logger log;
	private String logFile;
	private String serverIp;
	private int serverPort;
	public PartitionBolt(String boltName, String[] inFieldsName, String[] outFieldsName, int imgRows,
	                     int imgCols, int partitionNum, int upCompensationRowNum, int downCompensationRowNum, 
	                     boolean isCheckResourceConsumption, String logFile, String serverIp, int serverPort) {

		this.boltName = boltName;
		this.inFieldsName = inFieldsName;
		this.outFieldsName = outFieldsName;
		this.imgRows = imgRows;
		this.imgCols = imgCols;
		this.partitionNum = partitionNum;
		this.upCompensationRowNum = upCompensationRowNum;
		this.downCompensationRowNum = downCompensationRowNum;
		this.isCheckResourceConsumption = isCheckResourceConsumption;
		this.logFile = logFile;
		this.serverIp = serverIp;
		this.serverPort = serverPort;
	}

	@Override
	public void prepare(Map config, TopologyContext context, OutputCollector collector) {
		this.collector = collector;

		if (isCheckResourceConsumption) {
			log = new StormLogger(logFile, "reconstruction").getLogger();
			trc = new TopologyResourceConsumption(boltName, log);
		}
	}

	@Override
	public void execute(Tuple tuple) {

		int imgId = tuple.getIntegerByField(inFieldsName[0]);
		int subImgId = tuple.getIntegerByField(inFieldsName[1]);

		if (isCheckResourceConsumption && imgId % 1 == 0 && subImgId == 1) {
			trc.updateConsumingResource(imgId);
		}
		
		byte[] originalBytes = tuple.getBinaryByField(inFieldsName[2]);

		byte[][] stereo = StereoPartition.partitionStereo(originalBytes);

		byte[][] leftPartitionImg = ReconstructionAPI.partitionImage(stereo[0], partitionNum, imgRows, imgCols, upCompensationRowNum, downCompensationRowNum);
		byte[][] rightPartitionImg = ReconstructionAPI.partitionImage(stereo[1], partitionNum, imgRows, imgCols, upCompensationRowNum, downCompensationRowNum);

		long overallPartitionSize = 0;
		//List<Values> emitTuple = new ArrayList<>();
		for (int i = 0 ; i < partitionNum; i++) {
			byte[] stereoPartition = StereoPartition.mergeStereo(leftPartitionImg[i], rightPartitionImg[i]);
			overallPartitionSize += stereoPartition.length;
			//emitTuple.add(new Values(imgId, i, stereoPartition));
		    this.collector.emit(outFieldsName[0], new Values(imgId, i, stereoPartition));
			
			stereoPartition = null;
			leftPartitionImg[i] = null;
			rightPartitionImg[i] = null;
			/*
			try {
			    Thread.sleep(1);
			} catch (InterruptedException ex1) {

			}
			*/
		}

		if(imgId % 10 == 0){
			//this.collector.emit("overallLatencyTuples", new Values(imgId, trc.getElapseTime()));
		}

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
		// this.collector.emit(outFieldsName[0], emitTuple);
		this.collector.ack(tuple);

		if (isCheckResourceConsumption) {
			trc.setInOutputSize(originalBytes.length, overallPartitionSize);
			trc.reportConsumingResource(imgId);
		}

		/*
		for(int i = 0; i < leftPartitionImg.length; i++){

		    leftPartitionImg[i] = null;
		    rightPartitionImg[i] = null;
		}
		*/
		stereo[0] = null;
		stereo[1] = null;
		stereo = null;

	}

	@Override
	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		declarer.declareStream(outFieldsName[0], new Fields(outFieldsName[1], outFieldsName[2], outFieldsName[3]));
				declarer.declareStream("overallLatencyTuples", new Fields(outFieldsName[1], "overallLatency"));
	}

}
