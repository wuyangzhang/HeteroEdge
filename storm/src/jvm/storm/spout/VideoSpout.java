package storm.spout;

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

import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.logging.Logger;
import java.util.Map;
import java.util.logging.FileHandler;

import topologyResourceConsumption.*;
import reconstructionAPI.*;
import storm.stormLogger.*;

public class VideoSpout extends BaseRichSpout {

	private String spoutName;
	private String outFieldsName[];

	private SpoutOutputCollector collector;
	private int imgId = 0;
	private byte leftImg[];
	private byte rightImg[];
	private TopologyResourceConsumption rm;
	private final int rows, cols;

	private int cameraId;
	private long cameraPointer;

	private boolean isCheckResourceConsumption;
	private Logger log;
	private String logFile;

	// private Hashtable<long> timer = new Hashtable<>();

	public VideoSpout(String spoutname, String outFieldsName[], int imgRows, int imgCols, int cameraId, boolean isCheckResourceConsumption, String logFile) {

		this.spoutName = spoutname;
		this.outFieldsName = outFieldsName;
		this.rows = imgRows;
		this.cols = imgCols;

		this.cameraId = cameraId;

		this.isCheckResourceConsumption = isCheckResourceConsumption;
		this.logFile = logFile;
	}

	@Override
	public void open(Map conf, TopologyContext context, SpoutOutputCollector collector) {
		this.collector = collector;
		this.leftImg = new byte[rows * cols * 3];
		this.rightImg = new byte[rows * cols * 3];

		cameraPointer = ReconstructionAPI.cameraInit(cameraId);

		if (isCheckResourceConsumption) {
			log = new StormLogger(logFile, "reconstruction").getLogger();
			rm = new TopologyResourceConsumption(spoutName, log);
		}
	}

	@Override
	public void nextTuple() {

		while (true) {

			if (isCheckResourceConsumption) {
				rm.updateConsumingResource(imgId);
			}

			System.out.printf("[Spout] send image %d at time %d\n", imgId, rm.getElapseTime());
			this.collector.emit("overallLatencyTuples", new Values(imgId, rm.getElapseTime()));
			/* emit byte array of stereo images */
			ReconstructionAPI.readFromCamera(cameraPointer, leftImg, rightImg);

			byte[] stereo = StereoPartition.mergeStereo(leftImg, rightImg);

			this.collector.emit(outFieldsName[0], new Values(imgId, -1, stereo));

			if (isCheckResourceConsumption) {
				rm.reportConsumingResource(imgId);
			}

			stereo = null;
			imgId++;
		}

	}

	@Override
	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		declarer.declareStream(outFieldsName[0], new Fields(outFieldsName[1], outFieldsName[2], outFieldsName[3]));
		declarer.declareStream("overallLatencyTuples", new Fields(outFieldsName[1], "overallLatency"));
	}

	public void ack(Object id) {
	}

	public void fail(Object id) {
	}


	@Override
	public void close() {
		ReconstructionAPI.releaseCamera(cameraPointer);
	}

}
