package storm.bolt.merge;

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
import java.util.Hashtable;
import java.net.*;

import topologyResourceConsumption.*;
import reconstructionAPI.*;
import storm.stormLogger.*;
import storm.spout.StereoPartition;

public class MergeBolt extends BaseRichBolt {

	final private String boltName;
	final private String[] inFieldsName;
	final private String[] outFieldsName;

	private OutputCollector collector;

	final private int imgRows, imgCols;
	final private int partitionNum;
	final private int upCompensationRowNum;
	final private int colorWay, matType;

	private TopologyResourceConsumption trc;
	private boolean isCheckResourceConsumption;
	private Logger log;
	private String logFile;
	private String serverIp;
	private int serverPort;
	private Hashtable<Integer, Hashtable<Integer, byte[]>> partitionImgTable;

	public MergeBolt(String boltName, String[] inFieldsName, String[] outFieldsName, int imgRows,
	                 int imgCols, int partitionNum, int upCompensationRowNum, int colorWay, int matType,
	                 boolean isCheckResourceConsumption, String logFile, String serverIp, int serverPort) {

		this.boltName = boltName;
		this.inFieldsName = inFieldsName;
		this.outFieldsName = outFieldsName;
		this.imgRows = imgRows;
		this.imgCols = imgCols;
		this.partitionNum = partitionNum;
		this.upCompensationRowNum = upCompensationRowNum;
		this.colorWay = colorWay;
		this.matType = matType;
		this.isCheckResourceConsumption = isCheckResourceConsumption;
		this.logFile = logFile;
		this.serverIp = serverIp;
		this.serverPort = serverPort;
	}

	@Override
	public void prepare(Map config, TopologyContext context, OutputCollector collector) {
		this.collector = collector;

		if (isCheckResourceConsumption) {
			this.partitionImgTable = new Hashtable<>();
			this.log = new StormLogger(logFile, "reconstruction").getLogger();
			this.trc = new TopologyResourceConsumption(boltName, log);
		}
	}

	@Override
	public void execute(Tuple tuple) {

		int imgId = tuple.getIntegerByField(inFieldsName[0]);

		int subImgId = tuple.getIntegerByField(inFieldsName[1]);
		byte[] partitionBytes = tuple.getBinaryByField(inFieldsName[2]);
		

		if (!partitionImgTable.containsKey(imgId)) {

			Hashtable<Integer, byte[]> subPartitionTable = new Hashtable<>();
			subPartitionTable.put(subImgId, partitionBytes);
			partitionImgTable.put(imgId, subPartitionTable);
			this.collector.ack(tuple);
			return;
		}

		if (partitionImgTable.get(imgId).size() < partitionNum - 1) {

			partitionImgTable.get(imgId).put(subImgId, partitionBytes);
			this.collector.ack(tuple);
			return;
		}


		if (isCheckResourceConsumption && imgId % 1 == 0) {
			trc.updateConsumingResource(imgId);
		}
		partitionImgTable.get(imgId).put(subImgId, partitionBytes);
		byte[][] partition2dArray = new byte[partitionNum][];
		for (int i = 0 ; i < partitionNum; i++) {
			partition2dArray[i] = partitionImgTable.get(imgId).get(i);
		}

		byte[] merge = ReconstructionAPI.mergeImage(partition2dArray, imgRows, imgCols, upCompensationRowNum,
		               colorWay, matType);

		//ReconstructionAPI.displayDisparityMap(merge, imgRows, imgCols, 160, true);
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

		this.collector.emit(outFieldsName[0], new Values(imgId, subImgId, merge));
		this.collector.ack(tuple);

		for (int i = 0 ; i < partitionNum; i++) {
			partitionImgTable.get(imgId).remove(i);
			partition2dArray[i] = null;
		}

		merge = null;
		partition2dArray = null;
		partitionImgTable.remove(imgId);

		if (isCheckResourceConsumption && imgId % 1 == 0) {
			trc.setInOutputSize(partitionBytes.length * partitionNum, partitionBytes.length * partitionNum);
			trc.reportConsumingResource(imgId);
		}
	}

	@Override
	public void declareOutputFields(OutputFieldsDeclarer declarer) {
				declarer.declareStream("overallLatencyTuples", new Fields(outFieldsName[1], "overallLatency"));
		declarer.declareStream(outFieldsName[0], new Fields(outFieldsName[1], outFieldsName[2], outFieldsName[3]));
	}
}
