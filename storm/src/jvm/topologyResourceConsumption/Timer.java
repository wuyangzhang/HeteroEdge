package topologyResourceConsumption;

public class Timer {

	public void start() {
		startTime[startCount] = System.nanoTime();

		if (startCount < maxStart - 1) {
			startCount++;
		} else {
			startCount = 0;
		}
	}

	public void end() {
		endTime[endCount] = System.nanoTime();

		if (endCount < maxEnd - 1) {
			endCount++;
		} else {
			endCount = 0;
		}
	}

	public long getElapseTime() {

		int lastEnd = endCount - 1;
		int lastStart = startCount - 1;
		if (endCount == 0) {
			lastEnd = maxEnd - 1;
		}
		if (startCount == 0) {
			lastStart = maxStart - 1;
		}
		
		return (endTime[lastEnd] - startTime[lastStart]) / 1000000;
	}

	public void print(String taskInfo) {
		System.out.println("[Latency] " + taskInfo + " : " + getElapseTime() + " ms");
	}
	
	private final int maxStart = 3;
	private final int maxEnd = 3;
	private long startTime[] = new long[maxStart];
	private long endTime[] = new long[maxEnd];
	private int startCount = 0;
	private int endCount = 0;

}
