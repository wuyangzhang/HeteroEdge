package storm.topology;

import org.apache.storm.Config;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.LocalCluster;
import org.apache.storm.StormSubmitter;
import org.apache.storm.scheduler.Cluster;
import org.apache.storm.scheduler.EvenScheduler;
import org.apache.storm.scheduler.ExecutorDetails;
import org.apache.storm.scheduler.IScheduler;
import org.apache.storm.scheduler.SupervisorDetails;
import org.apache.storm.scheduler.Topologies;
import org.apache.storm.scheduler.TopologyDetails;
import org.apache.storm.scheduler.WorkerSlot;
import org.apache.storm.tuple.Fields;

import java.util.logging.Logger;
import java.util.logging.FileHandler;

import storm.spout.*;
import storm.bolt.disparity.*;
import storm.bolt.rectification.*;
import storm.bolt.reprojection.*;
import storm.bolt.merge.*;
import storm.bolt.partition.*;
import storm.bolt.rectificationAndPartitionBolt.*;
import storm.bolt.mergeAndReprojectionBolt.*;
import storm.stormLogger.*;
import storm.bolt.simulateClient.*;

public class MergeParallelReconstructionTopology extends TopologyParameters{

	public static void main(String args[]) throws Exception {

		final int sendInterval = TopologyParameters.sendInterval;
		final int totalClusterNodeNum = TopologyParameters.totalClusterNodeNum;
                final int partitionNum = 16;

		TopologyBuilder builder = new TopologyBuilder();
		final String topologyName = "reconstruction";

		final String spoutName = "VideoSpout";
		final String spoutOutFields[] = { "stereoTuples", "imgId", "imgPartitionId", "stereoBytes" };

		final String rectificationBoltName = "rectificationBolt";

		final String rectificationInFields[] = { "imgId", "imgPartitionId", "stereoBytes" };

		final String RectificationAndPartitionBoltName[] = {"rectificationBolt", "partitionBolt"};

		final String MergeAndReprojectionBoltName[] = {"mergeBolt", "reprojectionBolt"};

		final String partitionBoltOutFields[] = {"partitionTuples", "imgId", "imgPartitionId", "partitionBytes"};

		final String disparityBoltName = "disparityBolt";

		final String disparityInFields[] = { "imgId", "imgPartitionId", "partitionBytes" };
		final String disparityOutFields[] = { "disparityTuples", "imgId", "imgPartitionId", "disparityBytes" };


		final String mergeBoltInFields[] = {"imgId", "imgPartitionId", "disparityBytes"};

		final String reprojectionBoltName = "reprojectionBolt";

		final String reprojectionOutFields[] = { "reprojectionTuples", "imgId", "imgPartitionId", "reprojectionBytes" };

		final String leftImgAddr = TopologyParameters.leftImgAddr;
		final String rightImgAddr = TopologyParameters.rightImgAddr;

		final String calibrationFileAddr = "/root/ar/cpp/calibration.xml";
		final String rectificationFileAddr = "/root/ar/cpp/rectification.xml";

		final int imgRows = TopologyParameters.imgRows, imgCols = TopologyParameters.imgCols;
		final int cameraId = 1;
		final int upCompensationRowNum = 35, downCompensationRowNum = 35;
		final int colorWay = 1, matType = 3;
		final int disparityAlgorithm = 0;
		final int disparityNum = 160;
		boolean isCheckResourceConsumption = true;
		final String loggerFile = "/root/reconstruction.log";
		final String loggerName = "stormLogger";

		final String severIP = TopologyParameters.serverIP;
		final int serverPort = 6666;
		//VideoSpout videoSpout = new VideoSpout(spoutName, spoutOutFields, imgRows, imgCols, cameraId, isCheckResourceConsumption, loggerFile);
		
		FileSpout fileSpout = new FileSpout(spoutName, spoutOutFields, leftImgAddr, rightImgAddr, imgRows, imgCols,
											 sendInterval, isCheckResourceConsumption, loggerFile, 
											 severIP, serverPort);

		RectificationAndPartitionBolt rectificationAndPartitionBolt = new RectificationAndPartitionBolt(RectificationAndPartitionBoltName,
														rectificationInFields, partitionBoltOutFields, calibrationFileAddr, 
														rectificationFileAddr, imgRows, imgCols, partitionNum, upCompensationRowNum,
														downCompensationRowNum, isCheckResourceConsumption, loggerFile, 
														severIP, serverPort);
       
		DisparityBolt disparityBolt = new DisparityBolt(disparityBoltName, disparityInFields, disparityOutFields, imgRows, imgCols, disparityAlgorithm,
								disparityNum, isCheckResourceConsumption, loggerFile, severIP, serverPort);

		MergeAndReprojectionBolt mergeAndReprojectionBolt = new MergeAndReprojectionBolt(MergeAndReprojectionBoltName, mergeBoltInFields, reprojectionOutFields,
												 calibrationFileAddr, rectificationFileAddr, imgRows, imgCols,
												 partitionNum, upCompensationRowNum, colorWay, matType,
												 isCheckResourceConsumption, loggerFile, severIP, serverPort);

		
                builder.setSpout(spoutName, fileSpout, 1).setCPULoad(1);
		
		builder.setBolt("RectificationAndPartitionBolt", rectificationAndPartitionBolt, 20 * totalClusterNodeNum).shuffleGrouping(spoutName, spoutOutFields[0]).setCPULoad(2).setMemoryLoad(20.0);

		
		builder.setBolt(disparityBoltName, disparityBolt,   20 * partitionNum).shuffleGrouping("RectificationAndPartitionBolt", partitionBoltOutFields[0]).setCPULoad(5).setMemoryLoad(50);

		
		builder.setBolt("MergeAndReprojectionBolt", mergeAndReprojectionBolt, 20 * totalClusterNodeNum).fieldsGrouping(disparityBoltName, disparityOutFields[0], 
															  new Fields("imgId")).setCPULoad(2).setMemoryLoad(20.0);
		
		Config conf = new Config();
		//conf.setTopologyStrategy(org.apache.storm.scheduler.resource.strategies.scheduling.DefaultResourceAwareStrategy.class);
		
		conf.setDebug(false);
		conf.put(Config.TOPOLOGY_ACKER_EXECUTORS, totalClusterNodeNum);
		conf.put(Config.TOPOLOGY_WORKER_MAX_HEAP_SIZE_MB, 1024);
		conf.put(Config.WORKER_HEAP_MEMORY_MB, 1024);
		conf.put(Config.TOPOLOGY_DISABLE_LOADAWARE_MESSAGING, true);

		/*
		conf.put(Config.TOPOLOGY_DISRUPTOR_BATCH_SIZE, 1);
		conf.put(Config.TOPOLOGY_WORKER_MAX_HEAP_SIZE_MB, 4096 * 2);
		conf.put(Config.STORM_NETTY_MESSAGE_BATCH_SIZE, 1);
		conf.put(Config.TOPOLOGY_EXECUTOR_SEND_BUFFER_SIZE, 1024 * 1024 / 2);
		conf.put(Config.TOPOLOGY_EXECUTOR_RECEIVE_BUFFER_SIZE, 1024 * 1024 / 2);
		conf.put(Config.TOPOLOGY_TRANSFER_BUFFER_SIZE, 1024 * 1024 / 2);
		conf.put(Config.STORM_MESSAGING_NETTY_SERVER_WORKER_THREADS, 8);
		conf.put(Config.STORM_MESSAGING_NETTY_CLIENT_WORKER_THREADS, 8);
		conf.put(Config.STORM_MESSAGING_NETTY_BUFFER_SIZE, 1024 * 1024 * 2);
		conf.put(Config.TOPOLOGY_DISRUPTOR_BATCH_TIMEOUT_MILLIS, 1);
		conf.put(Config.TOPOLOGY_DISRUPTOR_WAIT_TIMEOUT_MILLIS, 1);
		conf.put(Config.STORM_MESSAGING_NETTY_MAX_SLEEP_MS, 10000);
		conf.put(Config.STORM_MESSAGING_NETTY_MIN_SLEEP_MS, 5000);
		*/
		if (args != null && args.length > 0) {

			conf.setNumWorkers(totalClusterNodeNum * 45);
			StormSubmitter.submitTopologyWithProgressBar(args[0], conf, builder.createTopology());

		} else {
  
			System.out.println("\nSubmit the parallel version topology to a local server!");
			//conf.setMaxTaskParallelism(4);

			LocalCluster cluster = new LocalCluster();
			cluster.submitTopology(topologyName, conf, builder.createTopology());

			Thread.sleep(10 * 60000);

			cluster.shutdown();
		}
	}

}
