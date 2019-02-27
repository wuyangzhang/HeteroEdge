package simulateClient;

import java.io.*;
import java.net.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Date;


public class SimulateClient {


    private int clientPort;
    private ConcurrentHashMap<Integer, ArrayList<Long>> video = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, ArrayList<Long>> partition = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, ArrayList<Long>> rectification = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, ArrayList<Long>> merge = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, ArrayList<Long>> reprojection = new ConcurrentHashMap<>();

    private ConcurrentHashMap<Integer, String> videoIP = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, String> partitionIP = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, String> rectificationIP = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, String> mergeIP = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, String> reprojectionIP = new ConcurrentHashMap<>();


    private ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, String>> disparityIP = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, ArrayList<Long>>> disparityStartTime = new ConcurrentHashMap<>();

    private DatagramSocket serverSocket;
    private BufferedWriter writer;
    private String logFileName;
    private AtomicInteger count = new AtomicInteger();
    private long startTime = System.nanoTime();

    public SimulateClient(int clientPort) {
        logFileName = "log.csv";
        this.clientPort = clientPort;
        try {
            writer = new BufferedWriter(new FileWriter(logFileName, false));
            this.serverSocket = new DatagramSocket(clientPort);
        } catch (Exception e) {

        }
    }

    public void run() {
        while (true) {
            SimulateClientThread simulateClientThread = new SimulateClientThread(serverSocket);
            simulateClientThread.run();
        }
    }

    private long getTime() {
        return new Date().getTime();
    }

    private class SimulateClientThread extends Thread {
        private DatagramSocket socket = null;
        private byte receiveData[] = new byte[100];

        public SimulateClientThread(DatagramSocket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            try {

                socket.receive(receivePacket);
                String data = new String(receivePacket.getData()).trim();

                String[] splited = data.split("\\s+");
                Integer index = Integer.valueOf(splited[1]);


                //receive packet format: taskName index ip
                if (splited[0].equals("VideoSpout")) {
                    if (!video.containsKey(index)) {
                        ArrayList<Long> time = new ArrayList<>();
                        time.add(getTime());
                        video.put(index, time);
                    } else {
                        videoIP.put(index, splited[2]);
                        video.get(index).add(getTime());
                    }
                    return;
                }

                if (splited[0].equals("partitionBolt")) {
                    if (!partition.containsKey(index)) {
                        ArrayList<Long> time = new ArrayList<>();
                        time.add(getTime());
                        partition.put(index, time);
                    } else {
                        partitionIP.put(index, splited[2]);
                        partition.get(index).add(getTime());
                    }
                    return;
                }

                if (splited[0].equals("rectificationBolt")) {
                    if (!rectification.containsKey(index)) {
                        ArrayList<Long> time = new ArrayList<>();
                        time.add(getTime());
                        rectification.put(index, time);
                    } else {
                        rectificationIP.put(index, splited[2]);
                        rectification.get(index).add(getTime());
                    }
                    return;
                }

                if (splited[0].equals("mergeBolt")) {
                    if (!merge.containsKey(index)) {
                        ArrayList<Long> time = new ArrayList<>();
                        time.add(getTime());
                        merge.put(index, time);
                    } else {
                        mergeIP.put(index, splited[2]);
                        merge.get(index).add(getTime());
                    }
                    return;
                }


                // handle special event of disparity, receive format: disparity id subid ip
                if (splited[0].equals("disparityBolt")) {
                    Integer subindex = Integer.valueOf(splited[2]);
                    String ip = splited[3];
                    Long receiveTime = getTime();

                    if (disparityStartTime.get(index) == null) {
                        ArrayList<Long> countTime = new ArrayList<>();
                        countTime.add(receiveTime);
                        ConcurrentHashMap<Integer, ArrayList<Long>> subDisparity = new ConcurrentHashMap<>();
                        subDisparity.put(subindex, countTime);
                        disparityStartTime.put(index, subDisparity);

                        ConcurrentHashMap<Integer, String> subDisparityIP = new ConcurrentHashMap<>();
                        subDisparityIP.put(subindex, ip);
                        disparityIP.put(index, subDisparityIP);
                    } else if (disparityStartTime.get(index).get(subindex) == null) {
                        ArrayList<Long> countTime = new ArrayList<>();
                        countTime.add(receiveTime);
                        disparityStartTime.get(index).put(subindex, countTime);
                        disparityIP.get(index).put(subindex, ip);
                    } else {
                        disparityStartTime.get(index).get(subindex).add(receiveTime);
                    }

                    return;
                }

                if (splited[0].equals("reprojectionBolt")) {
		    System.out.println(data);
                    if (!reprojection.containsKey(index)) {
                        ArrayList<Long> time = new ArrayList<>();
                        time.add(getTime());
                        reprojection.put(index, time);
                    } else {
			//			System.out.println(data);
                        //all associated tasks are done if we are here! write out all logs and release resources
                        long start = video.get(index).get(0);
                        long end = video.get(index).get(1);
                        writer.append("videoSpout," + index + ",0," + start + "," + end +","+ (end - start) + "," + videoIP.get(index));
                        writer.newLine();

                        start = rectification.get(index).get(0);
                        end = rectification.get(index).get(1);
                        writer.append("rectificationBolt," + index + ",0," + start + "," + end +","+ (end - start) + "," + rectificationIP.get(index));
                        writer.newLine();

                        if(partition.get(index) != null){
                            start = partition.get(index).get(0);
                            end = partition.get(index).get(1);
                            writer.append("partitionBolt," + index + ",0," + start + "," + end + "," +(end - start) + "," + partitionIP.get(index));
                            writer.newLine();
                        }


                        Long firstStart = Long.MAX_VALUE;
                        Long lastEnd = 0L;
                        ConcurrentHashMap<Integer, ArrayList<Long>> subDisparityDic = disparityStartTime.get(index);
                        for (Integer subIndex : subDisparityDic.keySet()) {
                            ArrayList<Long> re = subDisparityDic.get(subIndex);
                             start = re.get(0);
                             end = re.get(1);
                            firstStart = Math.min(firstStart, start);
                            lastEnd = Math.max(lastEnd, end);
                            writer.append("disparityBoltSub," + index + "," + subIndex + "," + start + "," + end + "," + (end - start) + "," + disparityIP.get(index).get(subIndex));
                            writer.newLine();
                        }

                        writer.append("disparityBolt," + index + ",0,0,0," + (lastEnd - firstStart) + ",0");
                        writer.newLine();

                        if(merge.get(index) != null){
                            start = merge.get(index).get(0);
                            end = merge.get(index).get(1);
                            writer.append("mergeBolt," + index + ",0," + start + "," + end + "," + (end - start) + "," + mergeIP.get(index));
                            writer.newLine();
                        }


                        start = reprojection.get(index).get(0);
                        end = getTime();
			writer.append("reprojectionBolt," + index + ",0," + start + "," + end + ","+(end - start) + "," + reprojectionIP.get(index));
                        writer.newLine();


                        start = video.get(index).get(0);
                        end = getTime();
                        writer.append("overall," + index + ",0," + start + "," + end + "," + (end - start) + ",0");
                        writer.newLine();

                        video.remove(index);
                        videoIP.remove(index);

                        rectification.remove(index);
                        rectificationIP.remove(index);

                        if(partition.get(index) != null){
                            partition.remove(index );
                            partitionIP.remove(index);
                        }


                        disparityStartTime.remove(index);
                        disparityIP.remove(index);

                        if(merge.get(index) != null){
                            merge.remove(index);
                            mergeIP.remove(index);
                        }


                        reprojection.remove(index);
                        reprojectionIP.remove(index);

                        if (index % 1 == 0) {
                            writer.flush();
                        }

                    }
                    return;
                }

	    } catch (Exception e){
	    }
	}
    }

    public static void main(String args[]) {
        SimulateClient simulateClient = new SimulateClient(6666);
        simulateClient.run();
    }
}

//public class SimulateClient {
//
//    ///	private static final Logger LOG = LoggerFactory.getLogger(SimulateClient.class);
//    private int clientPort;
//    private ConcurrentHashMap<Integer, ArrayList<Long>> taskLatencyCount = new ConcurrentHashMap<>();
//    private ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, ArrayList<Long>>> disparityStartTime = new ConcurrentHashMap<>();
//
//    private DatagramSocket serverSocket;
//    private BufferedWriter writer;
//    private String logFileName;
//    private AtomicInteger count = new AtomicInteger();
//    private long startTime = System.nanoTime();
//
//    public SimulateClient(int clientPort) {
//        logFileName = "log.csv";
//        this.clientPort = clientPort;
//        try {
//            writer = new BufferedWriter(new FileWriter(logFileName, false));
//            this.serverSocket = new DatagramSocket(clientPort);
//        } catch (Exception e) {
//
//        }
//    }
//
//    public void run() {
//        while (true) {
//            SimulateClientThread simulateClientThread = new SimulateClientThread(serverSocket);
//            simulateClientThread.run();
//        }
//    }
//
//    private class SimulateClientThread extends Thread {
//        private DatagramSocket socket = null;
//        private byte receiveData[] = new byte[100];
//
//        public SimulateClientThread(DatagramSocket socket) {
//            this.socket = socket;
//        }
//
//        @Override
//        public void run() {
//            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
//            try {
//
//                socket.receive(receivePacket);
//                String data = new String(receivePacket.getData()).trim();
//
//                //System.out.println("[receive packet]:\t" + data + " " );
//                String[] splited = data.split("\\s+");
//
//                // handle special event of disparity
//                if (splited[0].equals("disparityBolt")) {
//                    Integer index = Integer.valueOf(splited[1]);
//                    Integer subindex = Integer.valueOf(splited[2]);
//                    Long receiveTime = new Date().getTime();
//
//                    if(disparityStartTime.get(index) == null){
//                        ArrayList<Long> countTime = new ArrayList<>();
//                        countTime.add(receiveTime);
//                        ConcurrentHashMap<Integer, ArrayList<Long>> subDisparity = new ConcurrentHashMap<>();
//                        subDisparity.put(subindex, countTime);
//                        disparityStartTime.put(index, subDisparity);
//                    }else if(disparityStartTime.get(index).get(subindex) == null){
//                        ArrayList<Long> countTime = new ArrayList<>();
//                        countTime.add(receiveTime);
//                        disparityStartTime.get(index).put(subindex, countTime);
//                    }else{
//                        disparityStartTime.get(index).get(subindex).add(receiveTime);
//                    }
//
//                    return;
//                }
//
//                // if we receive the merge request, all the associated disparity tasks are done!
//                if (splited[0].equals("mergeBolt")) {
//                    int index = Integer.valueOf(splited[1]);
//                    ConcurrentHashMap<Integer, ArrayList<Long>> subDisparityDic = disparityStartTime.get(index);
//                    Long firstStart = Long.MAX_VALUE;
//                    Long lastEnd = 0L;
//                    for(Integer subIndex : subDisparityDic.keySet()){
//                        ArrayList<Long> re = subDisparityDic.get(subIndex);
//                        Long start = re.get(0);
//                        Long end = re.get(1);
//                        firstStart = Math.min(firstStart, start);
//                        lastEnd = Math.max(lastEnd, end);
//                        writer.append("disparityBoltSub," + index + "," + subIndex + "," + start + "," + end + "," + (end-start));
//                        writer.newLine();
//                    }
//
//                    writer.append("disparityBolt," + index + "," + (lastEnd - firstStart) + ",0,0,0");
//                    writer.newLine();
//
//                    writer.append(splited[0] + "," + splited[1] + "," + splited[2] + ",0,0,0");
//                    writer.newLine();
//                    return;
//                }
//
//                // simply record the normal events of partition, rectification and merge!
//                writer.append(splited[0] + "," + splited[1] + "," + splited[2]+ ",0,0,0");
//                writer.newLine();
//
//                // video spout is the first task in the pipeline. when we receive it, we will create a latency list for any following tasks.
//                Integer index = Integer.valueOf(splited[1]);
//                if (splited[0].equals("VideoSpout")) {
//                    ArrayList<Long> latencyList = new ArrayList<>();
//                    latencyList.add(new Date().getTime());
//                    taskLatencyCount.put(index, latencyList);
//                    count.incrementAndGet();
//                } else if (splited[0].equals("reprojectionBolt")) {
//                    Long initialTime = taskLatencyCount.get(index).get(0);
//                    Long elapseTime = new Date().getTime() - initialTime;
//                    long totalElapseTime = (System.nanoTime() - startTime) / (1000 * 1000 * 1000);
//                    double averageTaskThroughput = count.get() * 1000.0 / 1000.0 / (double) totalElapseTime;
//                    System.out.println("overallLatency," + index + "," + elapseTime);
//                    System.out.println("throughput," + String.valueOf(averageTaskThroughput));
//
//                    writer.append("overallLatency," + index + "," + elapseTime + ",0,0,0");
//                    writer.newLine();
//                    taskLatencyCount.remove(index);
//                    disparityStartTime.remove(index);
//                    if(index % 20 == 0){
//                        writer.flush();
//                    }
//                }
//
//            } catch (Exception e) {
//
//            }
//
//        }
//    }
//
//    public static void main(String args[]) {
//        SimulateClient simulateClient = new SimulateClient(6666);
//        simulateClient.run();
//    }
//}
