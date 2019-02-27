package storm.bolt.rectificationAndPartitionBolt;

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
import java.net.*;

import topologyResourceConsumption.*;
import reconstructionAPI.*;
import storm.stormLogger.*;
import storm.spout.StereoPartition;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;


public class RectificationAndPartitionBolt extends BaseRichBolt {

	final private String boltName[];
	private OutputCollector collector;
	final private String calibrationFileAddr, rectificationFileAddr;
	final private int imgRows, imgCols;
	final private int partitionNum;
	final private int upCompensationRowNum, downCompensationRowNum;
	private byte[][] map;
	private byte[] uLeft;
	private byte[] uRight;
	private String[] inFieldsName;
	private String[] outFieldsName;
	private TopologyResourceConsumption trc[];
	private boolean isCheckResourceConsumption;
	private Logger log;
	private String logFile;
	private String serverIp;
	private int serverPort;


	public RectificationAndPartitionBolt(String boltName[], String[] inFieldsName, String[] outFieldsName, String calibrationFileAddr,
										String rectificationFileAddr, int imgRows,
	                        			int imgCols, int partitionNum, int upCompensationRowNum, int downCompensationRowNum,
	                        			boolean isCheckResourceConsumption, String logFile,
	                        			String serverIp, int serverPort) {

		this.boltName = boltName;
		this.inFieldsName = inFieldsName;
		this.outFieldsName = outFieldsName;
		this.calibrationFileAddr = calibrationFileAddr;
		this.rectificationFileAddr = rectificationFileAddr;
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
		map = ReconstructionAPI.initImageRectification(calibrationFileAddr, rectificationFileAddr, imgRows, imgCols);
		uLeft = new byte[imgRows * imgCols * 3];
		uRight = new byte[imgRows * imgCols * 3];

		if (isCheckResourceConsumption) {
			log = new StormLogger(logFile, "reconstruction").getLogger();
			trc = new TopologyResourceConsumption[2];
			for(int i = 0; i < 2; i++){
			    //trc[i] = new TopologyResourceConsumption(boltName[i], log);
			}
			trc[0] = new TopologyResourceConsumption("RectificationAndPartitionBolt", log);
			trc[1] = new TopologyResourceConsumption(boltName[1], log);
		}
	}

	@Override
	public void execute(Tuple tuple) {

	    Date date = new Date();
	    System.out.println("rectification" + new Timestamp(date.getTime()));

	    try{
            InetAddress inetAddress = InetAddress. getLocalHost();
	    
		int imgId = tuple.getIntegerByField(inFieldsName[0]);
		if (isCheckResourceConsumption && imgId % 1 == 0) {
			trc[0].updateConsumingResource(imgId);
            String log_s = boltName[0] + " " + imgId + " " + inetAddress.getHostAddress();
            StormLogger.sendMessage(log_s);
		}

		int subImgId = tuple.getIntegerByField(inFieldsName[1]);

		byte[] stereoBytes = tuple.getBinaryByField(inFieldsName[2]);
		byte[][] stereo = StereoPartition.partitionStereo(stereoBytes);

		byte[] leftImgByte = stereo[0];
		byte[] rightImgByte = stereo[1];
		ReconstructionAPI.rectifyStereoImages(leftImgByte, rightImgByte, uLeft, uRight, imgRows, imgCols, map[0], map[1], map[2], map[3]);


		if(isCheckResourceConsumption && imgId % 1 == 0){
				//this.collector.emit("overallLatencyTuples", new Values(imgId, trc.getElapseTime()));
            String log_s = boltName[0] + " " + imgId + " " + inetAddress.getHostAddress();
            StormLogger.sendMessage(log_s);
		}



		if (isCheckResourceConsumption && imgId % 1 == 0) {
		    trc[1].updateConsumingResource(imgId);
            String log_s = boltName[1] + " " + imgId + " " + inetAddress.getHostAddress();
            StormLogger.sendMessage(log_s);
		}

		byte[][] leftPartitionImg = ReconstructionAPI.partitionImage(uLeft, partitionNum, imgRows, imgCols, upCompensationRowNum, downCompensationRowNum);
		byte[][] rightPartitionImg = ReconstructionAPI.partitionImage(uRight, partitionNum, imgRows, imgCols, upCompensationRowNum, downCompensationRowNum);

		long overallPartitionSize = 0;

		for(int i = 0; i < leftPartitionImg.length; i++){
		    overallPartitionSize += (leftPartitionImg[i].length + rightPartitionImg[i].length);
		}
		if (isCheckResourceConsumption && imgId % 1 == 0) {
		    trc[0].setInOutputSize(uLeft.length + uRight.length, overallPartitionSize);
		    trc[0].reportConsumingResource(imgId);
		    trc[1].setInOutputSize(uLeft.length + uRight.length, overallPartitionSize);

		    String log_s = boltName[1] + " " + imgId + " " + inetAddress.getHostAddress();
		    StormLogger.sendMessage(log_s);
		}
		
		for (int i = 0 ; i < partitionNum; i++) {
			byte[] stereoPartition = StereoPartition.mergeStereo(leftPartitionImg[i], rightPartitionImg[i]);
			overallPartitionSize += stereoPartition.length;
			this.collector.emit(outFieldsName[0], new Values(imgId, i, stereoPartition));
			stereoPartition = null;
			leftPartitionImg[i] = null;
			rightPartitionImg[i] = null;
		}





		this.collector.ack(tuple);
	}catch(Exception e){
		
	}
	}

	@Override
	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		declarer.declareStream(outFieldsName[0], new Fields(outFieldsName[1], outFieldsName[2], outFieldsName[3]));
		//declarer.declareStream("overallLatencyTuples", new Fields(outFieldsName[1], "overallLatency"));
	}

}
