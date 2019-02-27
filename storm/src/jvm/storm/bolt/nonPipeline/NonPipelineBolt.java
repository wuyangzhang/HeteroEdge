
package storm.bolt.nonPipeline;

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
import storm.spout.*;
import storm.bolt.disparity.*;
import storm.bolt.rectification.*;
import storm.bolt.reprojection.*;

public class NonPipelineBolt extends BaseRichBolt {

    final private String boltName;
    final private String nonPipelineBoltsName[];
    private String[] inFieldsName;
    private String[] outFieldsName;
    private OutputCollector collector;

    final private String calibrationFileAddr, rectificationFileAddr;
    final private int imgRows, imgCols;
    private byte[][] map;
    private byte[] uLeft;
    private byte[] uRight;
    private byte[] Q;
    private int disparityAlgorithm, disparityNum;

    private TopologyResourceConsumption trc[];

    private boolean isCheckResourceConsumption;
    private Logger log;
    private String logFile;
    private String serverIp;
    private int serverPort;
    private static final String[] GPUlist = new String[]{"node21-1.grid.orbit-lab.org", "node21-2.grid.orbit-lab.org", "node21-3.grid.orbit-lab.org", "10.10.21.2", "10.10.21.1", "10.10.21.3", "10.10.21.23"};
    //private static final String[] GPUlist = new String[]{"1"};
    public NonPipelineBolt(String boltName, String[] nonPipelineBoltsName, String[] inFieldsName, String[] outFieldsName, String calibrationFileAddr, String rectificationFileAddr, int imgRows,
                           int imgCols, int disparityAlgorithm, int disparityNum, boolean isCheckResourceConsumption, String logFile, String serverIp, int serverPort) {

        this.boltName = boltName;
        this.nonPipelineBoltsName = nonPipelineBoltsName;
        this.inFieldsName = inFieldsName;
        this.outFieldsName = outFieldsName;
        this.calibrationFileAddr = calibrationFileAddr;
        this.rectificationFileAddr = rectificationFileAddr;
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
        this.map = ReconstructionAPI.initImageRectification(calibrationFileAddr, rectificationFileAddr, imgRows, imgCols);
        this.Q = map[4];
        this.uLeft = new byte[imgRows * imgCols * 3];
        this.uRight = new byte[imgRows * imgCols * 3];

        if (isCheckResourceConsumption) {
            this.log = new StormLogger(logFile, "reconstruction").getLogger();
            final int boltsNum = nonPipelineBoltsName.length;
            log = new StormLogger(logFile, "reconstruction").getLogger();
            trc = new TopologyResourceConsumption[boltsNum];
            for (int i = 0; i < boltsNum; i++) {
                trc[i] = new TopologyResourceConsumption(nonPipelineBoltsName[i], log);
            }
        }
    }

    private boolean ifHasGPU() {

        try {
            DatagramSocket clientSocket = new DatagramSocket();
            InetAddress IPAddress = InetAddress.getByName(serverIp);
            InetAddress inetAddress = InetAddress.getLocalHost();
            String myIpAddr = inetAddress.getHostAddress();
	    System.out.println(myIpAddr + "\n\n\n");
            for (int i = 0; i < GPUlist.length; i++) {
                if (GPUlist[i].equals(myIpAddr)) return true;
            }
        } catch (Exception e) {
            System.err.println(e);
        }

        return false;
    }

    @Override
    public void execute(Tuple tuple) {

        try{
	InetAddress inetAddress = InetAddress. getLocalHost();
	
        int imgId = tuple.getIntegerByField(inFieldsName[0]);
        if (isCheckResourceConsumption && imgId % 1 == 0) {
            trc[0].updateConsumingResource(imgId);
            String log_s = nonPipelineBoltsName[0] + " " + imgId + " " + inetAddress.getHostAddress();
            StormLogger.sendMessage(log_s);
        }
        int subImgId = tuple.getIntegerByField(inFieldsName[1]);
        byte[] stereoBytes = tuple.getBinaryByField(inFieldsName[2]);
        byte[][] stereo = StereoPartition.partitionStereo(stereoBytes);
        byte[] leftImgByte = stereo[0];
        byte[] rightImgByte = stereo[1];

        ReconstructionAPI.rectifyStereoImages(leftImgByte, rightImgByte, uLeft, uRight, imgRows, imgCols, map[0], map[1], map[2], map[3]);

        if (imgId % 1 == 0) {
            String log_s = nonPipelineBoltsName[0] + " " + imgId + " " + inetAddress.getHostAddress();
            StormLogger.sendMessage(log_s);
        }

        if (isCheckResourceConsumption && imgId % 1 == 0) {
            trc[0].reportConsumingResource(imgId);
        }

        if (isCheckResourceConsumption && imgId % 1 == 0) {
            trc[1].updateConsumingResource(imgId);
	    String log_s = nonPipelineBoltsName[1] + " " + imgId + " 0 " + inetAddress.getHostAddress();
	    StormLogger.sendMessage(log_s);
        }


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


        if (imgId % 1 == 0) {
            String log_s = nonPipelineBoltsName[1] + " " + imgId + " 0 " + inetAddress.getHostAddress();
            StormLogger.sendMessage(log_s);
        }

        if (isCheckResourceConsumption && imgId % 1 == 0) {
            trc[1].reportConsumingResource(imgId);
        }

        if (isCheckResourceConsumption && imgId % 1 == 0) {
            trc[2].updateConsumingResource(imgId);
            String log_s = nonPipelineBoltsName[2] + " " + imgId + " " + inetAddress.getHostAddress();
            StormLogger.sendMessage(log_s);
        }

        byte[] xyz = ReconstructionAPI.reprojectTo3D(disparity, Q, imgRows, imgCols);

        if (imgId % 1 == 0) {
            trc[2].reportConsumingResource(imgId);
            String log_s = nonPipelineBoltsName[2] + " " + imgId + " " + inetAddress.getHostAddress();
            StormLogger.sendMessage(log_s);
        }


        this.collector.emit(outFieldsName[0], new Values(imgId, subImgId, xyz));
        this.collector.ack(tuple);

        }catch(Exception e){
        
    }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declareStream(outFieldsName[0], new Fields(outFieldsName[1], outFieldsName[2], outFieldsName[3]));
        declarer.declareStream("overallLatencyTuples", new Fields(outFieldsName[1], "overallLatency"));
    }
}
