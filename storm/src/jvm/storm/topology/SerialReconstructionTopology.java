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
import org.apache.storm.generated.Bolt;

import java.util.logging.Logger;
import java.util.logging.FileHandler;

import storm.spout.*;
import storm.bolt.disparity.*;
import storm.bolt.rectification.*;
import storm.bolt.reprojection.*;
import storm.stormLogger.*;
import storm.bolt.simulateClient.*;

public class SerialReconstructionTopology extends TopologyParameters {

	public static void main(String args[]) throws Exception {

		final  int sendInterval = TopologyParameters.sendInterval;
		final int totalClusterNodeNum = TopologyParameters.totalClusterNodeNum;


		TopologyBuilder builder = new TopologyBuilder();
		final String topologyName = "reconstruction";

		final String spoutName = "VideoSpout";
		final String spoutOutFields[] = { "stereoTuples", "imgId", "imgPartitionId", "stereoBytes" };

		final String rectificationBoltName = "rectificationBolt";
		final String rectificationInFields[] = { "imgId", "imgPartitionId", "stereoBytes" };
		final String rectificationOutFields[] = { "stereoRectificationTuples", "imgId", "imgPartitionId",
				"stereoRectificationBytes" };

		final String disparityBoltName = "disparityBolt";
		final String disparityInFields[] = { "imgId", "imgPartitionId", "stereoRectificationBytes" };
		final String disparityOutFields[] = { "disparityTuples", "imgId", "imgPartitionId", "disparityBytes" };

		final String reprojectionBoltName = "reprojectionBolt";
		final String reprojectionInFields[] = { "imgId", "imgPartitionId", "disparityBytes" };
		final String reprojectionOutFields[] = { "reprojectionTuples", "imgId", "imgPartitionId", "reprojectionBytes" };

		final String simulateClientBoltName = "simulateClient";

		final String calibrationFileAddr = "/root/ar/cpp/calibration.xml";
		final String rectificationFileAddr = "/root/ar/cpp/rectification.xml";

		final String leftImgAddr = TopologyParameters.leftImgAddr;
                final String rightImgAddr = TopologyParameters.rightImgAddr;

                final int imgRows = TopologyParameters.imgRows, imgCols = TopologyParameters.imgCols;
		final int cameraId = 1;
		final int disparityAlgorithm = 0;
		final int disparityNum = 160;
		boolean isCheckResourceConsumption = true;
		final String loggerFile = "/root/reconstruction.log";
		final String loggerName = "stormLogger";
		final String severIP = "10.10.21.1";
		final int serverPort = 6666;

		FileSpout fileSpout = new FileSpout(spoutName, spoutOutFields, leftImgAddr, rightImgAddr, imgRows, imgCols, sendInterval, isCheckResourceConsumption, loggerFile, severIP, serverPort);
		//VideoSpout videoSpout = new VideoSpout(spoutName, spoutOutFields, imgRows, imgCols, cameraId, isCheckResourceConsumption, loggerFile);
		RectificationBolt rectificationBolt = new RectificationBolt(rectificationBoltName, rectificationInFields, rectificationOutFields, calibrationFileAddr,
				rectificationFileAddr, imgRows, imgCols, isCheckResourceConsumption, loggerFile, severIP, serverPort);
		DisparityBolt disparityBolt = new DisparityBolt(disparityBoltName, disparityInFields, disparityOutFields, imgRows, imgCols, disparityAlgorithm,
				disparityNum, isCheckResourceConsumption, loggerFile, severIP, serverPort);
		ReprojectionBolt reprojectionBolt = new ReprojectionBolt(reprojectionBoltName, reprojectionInFields, reprojectionOutFields, calibrationFileAddr,
				rectificationFileAddr, imgRows, imgCols, isCheckResourceConsumption, loggerFile, severIP, serverPort);

		// construction serial reconstruction topology
		builder.setSpout(spoutName, fileSpout, 1);

		builder.setBolt(rectificationBoltName, rectificationBolt, totalClusterNodeNum).shuffleGrouping(spoutName, spoutOutFields[0]);

		builder.setBolt(disparityBoltName, disparityBolt, totalClusterNodeNum).shuffleGrouping(rectificationBoltName, rectificationOutFields[0]);

		builder.setBolt(reprojectionBoltName, reprojectionBolt, totalClusterNodeNum).shuffleGrouping(disparityBoltName, disparityOutFields[0]);


		Config conf = new Config();
		conf.put(Config.TOPOLOGY_ACKER_EXECUTORS, totalClusterNodeNum);
		// conf.put(Config.TOPOLOGY_DISRUPTOR_BATCH_SIZE, 1);
		// conf.put(Config.TOPOLOGY_WORKER_MAX_HEAP_SIZE_MB, 4096 * 2);
		// conf.put(Config.STORM_NETTY_MESSAGE_BATCH_SIZE, 1);
		// conf.put(Config.TOPOLOGY_EXECUTOR_SEND_BUFFER_SIZE, 1024 * 1024 / 2);
		// conf.put(Config.TOPOLOGY_EXECUTOR_RECEIVE_BUFFER_SIZE, 1024 * 1024 / 2);
		// conf.put(Config.TOPOLOGY_TRANSFER_BUFFER_SIZE, 1024 * 1024 / 2);
		// conf.put(Config.STORM_MESSAGING_NETTY_SERVER_WORKER_THREADS, 8);
		// conf.put(Config.STORM_MESSAGING_NETTY_CLIENT_WORKER_THREADS, 8);
		// conf.put(Config.STORM_MESSAGING_NETTY_BUFFER_SIZE, 1024 * 1024 * 2);
		// conf.put(Config.TOPOLOGY_DISRUPTOR_BATCH_TIMEOUT_MILLIS, 1);
		// conf.put(Config.TOPOLOGY_DISRUPTOR_WAIT_TIMEOUT_MILLIS, 1);
		// conf.put(Config.STORM_MESSAGING_NETTY_MAX_SLEEP_MS, 10000);
		// conf.put(Config.STORM_MESSAGING_NETTY_MIN_SLEEP_MS, 5000);
		
		if (args != null && args.length > 0) {

			conf.setNumWorkers(totalClusterNodeNum);
			StormSubmitter.submitTopologyWithProgressBar(args[0], conf, builder.createTopology());

		} else {

			System.out.println("Submit the topology to a local server!");
			//conf.setMaxTaskParallelism(4);

			LocalCluster cluster = new LocalCluster();
			cluster.submitTopology(topologyName, conf, builder.createTopology());

			Thread.sleep(10 * 60000);

			cluster.shutdown();
		}
	}

}
