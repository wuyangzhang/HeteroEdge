package topologyResourceConsumption;

import java.lang.management.ManagementFactory;
import com.sun.management.ThreadMXBean;
import java.util.logging.Logger;
import java.util.Date;
import java.lang.Runtime;

/*
     OS resource usage collection
*/

/*
 collect CPU time of a single thread
 */
public class TopologyResourceConsumption {
	private final Logger log;
	private long cpuTime;
	private long usedCpuTimeDiff;
	private long elapseTime;
	private long elapseTimeDiff;
	private long memory;
	private final String boltName;
	private long inputSize;
	private long outputSize;
	private int cores;
	private int count = 0;
	private final static ThreadMXBean threadMXBean = (com.sun.management.ThreadMXBean) ManagementFactory
			.getThreadMXBean();

	public TopologyResourceConsumption(String boltName, Logger log, int inputSize, int outputSize) {
		this.boltName = boltName;
		this.log = log;
		this.inputSize = inputSize;
		this.outputSize = outputSize;
		elapseTime = getElapseTime();
		cpuTime = 0;
		memory = 0;
		elapseTimeDiff = 0;
		usedCpuTimeDiff = 0;
		cores = Runtime.getRuntime().availableProcessors();
	}

	public TopologyResourceConsumption(String boltName, Logger log) {
		this.boltName = boltName;
		this.log = log;
		elapseTime = getElapseTime();
		cpuTime = 0;
		memory = 0;
		cores = Runtime.getRuntime().availableProcessors();
	}

	public void setInOutputSize(long inputSize, long outputSize) {
		this.inputSize = inputSize;
		this.outputSize = outputSize;
	}

	public void sendConsumption(byte[] sendData){

	}
	private long getCpuTime() {
		return cpuTime;
	}

	private long getMemory() {
		return memory;
	}

	private void setCpuTime(long val) {
		cpuTime = val;
	}

	private void setElapseTime(long val) {
		elapseTime = val;
	}

	private void setMemory(long val) {
		memory = val;
	}

	/*
	 * @return CPU time in ms
	 */
	private long getUsedCpuTime() {
		long threadId = Thread.currentThread().getId();
		return threadMXBean.getThreadCpuTime(threadId) / 1000000;
	}

	public long getElapseTime() {
		Date date = new Date();
		return date.getTime();
	}

	/*
	 * @return used memory in bytes
	 */
	private long getUsedMemory() {
		long threadId = Thread.currentThread().getId();
		return threadMXBean.getThreadAllocatedBytes(threadId);
	}

	private long usedCpuTimeDiff() {
		long currentUsedCpuTime = getUsedCpuTime();
		usedCpuTimeDiff = currentUsedCpuTime - cpuTime;
		cpuTime = currentUsedCpuTime;
		return usedCpuTimeDiff;
	}

	public long elapseTimeDiff() {
		long currentElapseTime = getElapseTime();
		elapseTimeDiff = currentElapseTime - elapseTime;
		return elapseTimeDiff;
	}

	//unit MB
	private double memoryDiff() {
		long currentMemory = getUsedMemory();
		long memoryDiff = currentMemory - memory;
		memory = currentMemory;
		return memoryDiff * 100 / 100.00 / 100000;
	}

	private double cpuUtilization() {
	    //log.info(" cpu utilization is : " + elapseTimeDiff + " " + usedCpuTimeDiff + "core " + cores + " count: "+ count);
		if (elapseTimeDiff == 0 || usedCpuTimeDiff == 0) {
			return 0;
		}

		// return usedCpuTimeDiff * 100 / (elapseTimeDiff * cores);
		return usedCpuTimeDiff / (double) (elapseTimeDiff * cores * 100 / 100.00);
	}

	//unit MB
	private double downloadBandwidth() {
		if (elapseTimeDiff == 0)
			return 0;
		return inputSize * (1000 / (double) (elapseTimeDiff * 100 / 100.0)) / 1000000;
	}

	//unit MB
	private double uploadBandwidth() {
		if (elapseTimeDiff == 0)
			return 0;
		return outputSize * (1000 / (double) (elapseTimeDiff * 100 / 100.00)) / 1000000;
	}

	public void updateConsumingResource(int imgId) {
		// update cpu and memory
		cpuTime = getUsedCpuTime();
		elapseTime = getElapseTime();
		memory = getUsedMemory();
		//log.info(boltName + " receive task [" + imgId + "]" + " timestamp is : [" + getElapseTime() + "]");
	}

	public void recordLog(String boltName, String logInfo){
		log.info(boltName + " " + logInfo);
	}

	public void reportConsumingResource(int imgId) {
	    //count++;
		/* cpu time */
		log.info(boltName + "," + imgId + ","
						  + "CpuTimeDiff is," + usedCpuTimeDiff() + ",ms,"
						  + "ElapseTimeDiff," + elapseTimeDiff() + ",ms,"
						  + "CpuUtilization," + cpuUtilization() + ",%,"
						  + "MemoryDiff," + memoryDiff() + ",MB,"
						  + "InputSize," + inputSize * 100 / 100.00 / 1000000 + ",MB,"
						  + "OutputSize," + outputSize * 100 / 100.00 / 1000000 + ",MB,"
						  + "DownloadBandwidth," + downloadBandwidth() + ",MBp,s"
						  + "UploadBandwidth," + uploadBandwidth() + ",MBps,"
                          + "In," + inputSize + ",Byte,"
                          + "Output," + outputSize + ",Byte");


		// log.info(boltName + " ElapseTimeDiff is: " + elapseTimeDiff() + " ms");
		// log.info(boltName + " CpuUtilization is: " + cpuUtilization() + " %");

		// /* retained heap size */
		// log.info(boltName + " MemoryDiff is: " + memoryDiff() + " MB");

		// /* I/O */
		// log.info(boltName + " InputSize is : " + inputSize * 100 / 100.00 / 1000000 + " MB");
		// log.info(boltName + " OutputSize is : " + outputSize * 100 / 100.00 / 1000000 + " MB");

		// log.info(boltName + " DownloadBandwidth is : " + downloadBandwidth() + " MBps");
		// log.info(boltName + " UploadBandwidth is : " + uploadBandwidth() + " MBps");
	}

}
