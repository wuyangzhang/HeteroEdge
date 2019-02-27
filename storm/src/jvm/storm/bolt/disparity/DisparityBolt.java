package storm.bolt.disparity;

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
import java.io.*;

import topologyResourceConsumption.*;
import reconstructionAPI.*;
import storm.stormLogger.*;
import storm.spout.StereoPartition;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DisparityBolt extends BaseRichBolt {

    final private String boltName;
    private String[] inFieldsName;
    private String[] outFieldsName;
    private OutputCollector collector;

    final private int imgRows, imgCols;
    private int disparityAlgorithm, disparityNum;
    private TopologyResourceConsumption trc;
    private boolean isCheckResourceConsumption;
    private Logger log;
    private String logFile;
    private String serverIp;
    private int serverPort;
    //private static final String[] GPUlist = new String[]{"10.10.21.1", "10.10.21.2"};
    private static final String[] GPUlist = new String[]{"1"};

    public DisparityBolt(String boltName, String[] inFieldsName, String[] outFieldsName, int imgRows,
                         int imgCols, int disparityAlgorithm, int disparityNum, boolean isCheckResourceConsumption,
                         String logFile, String serverIp, int serverPort) {

        this.boltName = boltName;
        this.inFieldsName = inFieldsName;
        this.outFieldsName = outFieldsName;
        this.imgRows = imgRows;
        this.imgCols = imgCols;
        this.disparityAlgorithm = disparityAlgorithm;
        this.disparityNum = disparityNum;
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

    private boolean ifHasGPU() {

        try {
            InetAddress inetAddress = InetAddress.getLocalHost();
            String myIpAddr = inetAddress.getHostAddress();
            for(int i = 0; i < GPUlist.length; i++){
                if(GPUlist[i].equals(myIpAddr)) return true;
            }
        }catch(Exception e){
            System.err.println(e);
        }

        return false;
    }

    @Override
    public void execute(Tuple tuple) {


        int imgId = tuple.getIntegerByField(inFieldsName[0]);
        int subImgId = tuple.getIntegerByField(inFieldsName[1]);
        try{        
            InetAddress inetAddress;
            inetAddress = InetAddress. getLocalHost();
       
        if (isCheckResourceConsumption && imgId % 1 == 0) {
            trc.updateConsumingResource(imgId);
            String log_s = boltName + " " + imgId + " " + subImgId + " " + inetAddress.getHostAddress();
            StormLogger.sendMessage(log_s);
            
            

        }


        byte[] stereoBytes = tuple.getBinaryByField(inFieldsName[2]);
        byte[][] stereo = StereoPartition.partitionStereo(stereoBytes);
        byte[] leftImgByte = stereo[0];
        byte[] rightImgByte = stereo[1];

        byte[] disparity;

        if (!ifHasGPU()) {
            disparity = ReconstructionAPI.calculateDisparityMap(leftImgByte, rightImgByte, imgCols, disparityAlgorithm);
            ReconstructionAPI.calculateDisparityMap(leftImgByte, rightImgByte, imgCols, disparityAlgorithm);
            ReconstructionAPI.calculateDisparityMap(leftImgByte, rightImgByte, imgCols, disparityAlgorithm);
            ReconstructionAPI.calculateDisparityMap(leftImgByte, rightImgByte, imgCols, disparityAlgorithm);
        } else {
            disparity = ReconstructionAPI.calculateDisparityMapGPU(leftImgByte, rightImgByte, imgCols, disparityAlgorithm);
            // ReconstructionAPI.calculateDisparityMapGPU(leftImgByte, rightImgByte, imgCols, disparityAlgorithm);
            // ReconstructionAPI.calculateDisparityMapGPU(leftImgByte, rightImgByte, imgCols, disparityAlgorithm);
            // ReconstructionAPI.calculateDisparityMapGPU(leftImgByte, rightImgByte, imgCols, disparityAlgorithm);
        }

        //		if(isCheckResourceConsumption & imgId % 1 == 0 && subImgId < 1){
        if (isCheckResourceConsumption && imgId % 1 == 0) {
            trc.setInOutputSize(leftImgByte.length + rightImgByte.length, disparity.length);
            trc.reportConsumingResource(imgId);

            String log_s = boltName + " " + imgId + " " + subImgId + " " + inetAddress.getHostAddress();
            StormLogger.sendMessage(log_s);
        }


        this.collector.emit(outFieldsName[0], new Values(imgId, subImgId, disparity));

        this.collector.ack(tuple);


        stereoBytes = null;
        leftImgByte = null;
        rightImgByte = null;
        disparity = null;
        }catch(Exception e){

        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declareStream("overallLatencyTuples", new Fields(outFieldsName[1], "overallLatency"));
        declarer.declareStream(outFieldsName[0], new Fields(outFieldsName[1], outFieldsName[2], outFieldsName[3]));
    }

}
