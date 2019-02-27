package storm.bolt.mergeAndReprojectionBolt;

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
import java.io.*;
import java.net.*;

import topologyResourceConsumption.*;
import reconstructionAPI.*;
import storm.stormLogger.*;
import storm.spout.StereoPartition;


import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MergeAndReprojectionBolt extends BaseRichBolt {

    final private String boltName[];
    final private String[] inFieldsName;
    final private String[] outFieldsName;

    private OutputCollector collector;

    final private int imgRows, imgCols;
    final private int partitionNum;
    final private int upCompensationRowNum;
    final private int colorWay, matType;

    final private String calibrationFileAddr, rectificationFileAddr;
    private byte[] Q;

    private TopologyResourceConsumption trc[];
    private boolean isCheckResourceConsumption;
    private Logger log;
    private String logFile;

    private String serverIp;
    private int serverPort;

    private Hashtable<Integer, Hashtable<Integer, byte[]>> partitionImgTable;

    public MergeAndReprojectionBolt(String boltName[], String[] inFieldsName, String[] outFieldsName, String calibrationFileAddr, String rectificationFileAddr, int imgRows,
                                    int imgCols, int partitionNum, int upCompensationRowNum, int colorWay, int matType, boolean isCheckResourceConsumption, String logFile, String serverIp, int serverPort) {

        this.boltName = boltName;
        this.inFieldsName = inFieldsName;
        this.outFieldsName = outFieldsName;
        this.imgRows = imgRows;
        this.imgCols = imgCols;
        this.calibrationFileAddr = calibrationFileAddr;
        this.rectificationFileAddr = rectificationFileAddr;
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
        byte[][] map = ReconstructionAPI.initImageRectification(calibrationFileAddr, rectificationFileAddr, imgRows, imgCols);
        Q = map[4];

        if (isCheckResourceConsumption) {
            this.partitionImgTable = new Hashtable<>();
            log = new StormLogger(logFile, "reconstruction").getLogger();
            trc = new TopologyResourceConsumption[2];
            for (int i = 0; i < 2; i++) {
                //trc[i] = new TopologyResourceConsumption(boltName[i], log);
            }
            trc[0] = new TopologyResourceConsumption("MergeAndReprojectionBolt", log);
            trc[1] = new TopologyResourceConsumption(boltName[1], log);
        }
    }

    @Override
    public void execute(Tuple tuple) {
        Date date = new Date();
        System.out.println("merge" + new Timestamp(date.getTime()));

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


        try{        

	       InetAddress inetAddress = InetAddress. getLocalHost();
	
        if (isCheckResourceConsumption && imgId % 1 == 0) {
            trc[0].updateConsumingResource(imgId);
	    String log_s = boltName[0] + " " + imgId + " 0 " + inetAddress.getHostAddress();
	    StormLogger.sendMessage(log_s);
        }



        partitionImgTable.get(imgId).put(subImgId, partitionBytes);
        byte[][] partition2dArray = new byte[partitionNum][];
        for (int i = 0; i < partitionNum; i++) {
            partition2dArray[i] = partitionImgTable.get(imgId).get(i);
        }


        byte[] merge = ReconstructionAPI.mergeImage(partition2dArray, imgRows, imgCols, upCompensationRowNum,
                colorWay, matType);

        if (imgId % 1 == 0) {
            //this.collector.emit("overallLatencyTuples", new Values(imgId, trc.getElapseTime()));
            String log_s = boltName[0] + " " + imgId + " " + inetAddress.getHostAddress();
            StormLogger.sendMessage(log_s);

        }


        if (isCheckResourceConsumption && imgId % 1 == 0) {
	    
            trc[1].updateConsumingResource(imgId);
            String log_s = boltName[1] + " " + imgId + " " + inetAddress.getHostAddress();
            StormLogger.sendMessage(log_s);
        }

        byte[] xyz = ReconstructionAPI.reprojectTo3D(merge, Q, imgRows, imgCols);
        this.collector.emit(outFieldsName[0], new Values(imgId, subImgId, xyz));


        if (isCheckResourceConsumption && imgId % 1 == 0) {
            trc[0].setInOutputSize(merge.length, xyz.length);
            trc[0].reportConsumingResource(imgId);
            trc[1].setInOutputSize(merge.length, xyz.length);
            //trc[1].reportConsumingResource(imgId);

            String log_s = boltName[1] + " " + imgId + " " + inetAddress.getHostAddress();
            StormLogger.sendMessage(log_s);
        }

        partitionImgTable.remove(imgId);
        xyz = null;
        merge = null;

        this.collector.ack(tuple);

    }catch(Exception e){

    }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declareStream(outFieldsName[0], new Fields(outFieldsName[1], outFieldsName[2], outFieldsName[3]));
    }
}
