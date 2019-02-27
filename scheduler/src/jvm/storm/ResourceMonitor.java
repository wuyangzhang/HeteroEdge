package storm;

import java.util.ArrayList;
import java.io.*;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.io.*;
import java.io.Writer;

import java.util.logging.Logger;
import java.util.logging.FileHandler;

public class ResourceMonitor {

    private static final String myIpAddr = "node21-2";
    private static final String[] ClusterNodeList = new String[]{"node21-1.grid.orbit-lab.org", "node21-2.grid.orbit-lab.org", "node21-4.grid.orbit-lab.org",
    								 "node21-5.grid.orbit-lab.org", "node21-6.grid.orbit-lab.org", "node21-7.grid.orbit-lab.org", "node21-3.grid.orbit-lab.org"};

    //private static final String myIpAddr = "wuyang-master";
    //private static final String[] ClusterNodeList = new String[]{"wuyang-master", "wuyang-1", "wuyang-2"};
    //public static final String[] GPUNodeList = new String[]{"wuyang-master"};
    public static final String [] GPUNodeList = new String[]{"node21-1.grid.orbit-lab.org", "node21-2.grid.orbit-lab.org"};
    private static final String resourceCollectorByShellAddr = "/root/ar/scheduler/src/jvm/storm/ResourceCollector.sh";
    private static final String clusterResourceSavingPath = "/root/ar/scheduler/src/jvm/storm/resource/";
    private static final String remoteResourceSendingPath = "root@" + myIpAddr + ":/root/ar/scheduler/src/jvm/storm/resource/";

    public Map<String, Node> nodeTable = new Hashtable<>();

    private static final ResourceMonitor resourceMonitor = new ResourceMonitor();

    public static ResourceMonitor getResourceMonitor() {
        return resourceMonitor;
    }

    public void collectResource() {
        for (Map.Entry<String, Node> entry : nodeTable.entrySet()) {
            String node = entry.getKey();
            collectCpuResource(node);
            collectMemResource(node);


            for (Map.Entry<String, Node> entry1 : nodeTable.entrySet()) {
                String nodeTo = entry1.getKey();
                if(node.equals(nodeTo)){
                    continue;
                }
                //collectBandwidthResource(node, nodeTo);
                break;
                //String toNode = entry1.getKey();
                //collectBandwidthResource(node, toNode);
                //collectRttResource(node, toNode);
            }
        }
    }

    /*
        we collect average cpu utilization in the last 1 minute and the maximum CPU frequency.
     */
    public void collectCpuResource(String nodeIp) {

        Process p;
        String exeCMD = resourceCollectorByShellAddr + " cpu " + nodeIp + " " + remoteResourceSendingPath;
        String cmd[] = {"bash", "-c", exeCMD};
        String resultFileAddr = clusterResourceSavingPath + "cpu" + "-" + nodeIp;

        try {
            p = Runtime.getRuntime().exec(cmd);
            int exitVal = p.waitFor();
            if (exitVal != 0) {
                System.err.println("Fail to execute the command");
            }

            BufferedReader br = new BufferedReader(new FileReader(resultFileAddr));
            int cpuNum = Integer.parseInt(br.readLine());
            double cpuUtilization = Double.parseDouble(br.readLine());
            double cpuFrequency = Double.parseDouble(br.readLine());
            Node node = nodeTable.get(nodeIp);
            node.setCpuNum(cpuNum);
            node.setMaxCpuUtilization(100);
            node.setCpuUtilizationFree(cpuUtilization);
            node.setCpuFrequency(cpuFrequency);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void collectMemResource(String nodeIp) {

        Process p;
        String exeCMD = resourceCollectorByShellAddr + " mem " + nodeIp + " " + remoteResourceSendingPath;
        String cmd[] = {"bash", "-c", exeCMD};
        String resultFileAddr = clusterResourceSavingPath + "mem" + "-" + nodeIp;

        try {
            p = Runtime.getRuntime().exec(cmd);
            int exitVal = p.waitFor();
            if (exitVal != 0) {
                System.err.println("Fail to execute the command");
            }

            BufferedReader br = new BufferedReader(new FileReader(resultFileAddr));
            double memFree = Double.parseDouble(br.readLine());
            nodeTable.get(nodeIp).setMemoryFree(memFree);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void collectBandwidthResource(String fromNodeIp, String toNodeIp) {

        Process p;
        String exeCMD = resourceCollectorByShellAddr + " bw " + fromNodeIp + " " + toNodeIp + " " + remoteResourceSendingPath;
        String cmd[] = {"bash", "-c", exeCMD};
        String resultFileAddr = clusterResourceSavingPath + "bw" + "-" + fromNodeIp + "-" + toNodeIp;

	System.out.println("execute cmd" + exeCMD + "result file " + resultFileAddr);
        try {
            p = Runtime.getRuntime().exec(cmd);
            int exitVal = p.waitFor();
            if (exitVal != 0) {
                System.err.println("Fail to execute the command");
            }

            BufferedReader br = new BufferedReader(new FileReader(resultFileAddr));
            double upload = Double.parseDouble(br.readLine());
            nodeTable.get(fromNodeIp).setUploadBandwidth(upload);
//            double download = Double.parseDouble(br.readLine());
//            nodeTable.get(fromNodeIp).setUploadBandwidth(upload, toNodeIp);
//            nodeTable.get(fromNodeIp).setDownloadBandwidth(download, toNodeIp);

//            if (!fromNodeIp.equals(toNodeIp)) {
//                nodeTable.get(fromNodeIp).setUploadBandwidth(upload);
//                nodeTable.get(fromNodeIp).setDownloadBandwidth(download);
//            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void collectRttResource(String fromNodeIp, String toNodeIp) {

        Process p;
        String exeCMD = resourceCollectorByShellAddr + " rtt " + fromNodeIp + " " + toNodeIp + " " + remoteResourceSendingPath;
        String cmd[] = {"bash", "-c", exeCMD};
        String resultFileAddr = clusterResourceSavingPath + "rtt" + "-" + fromNodeIp + "-" + toNodeIp;
        try {
            p = Runtime.getRuntime().exec(cmd);
            int exitVal = p.waitFor();
            if (exitVal != 0) {
                System.err.println("Fail to execute the command");
            }

            BufferedReader br = new BufferedReader(new FileReader(resultFileAddr));
            double rtt = Double.parseDouble(br.readLine());
            nodeTable.get(fromNodeIp).setRtt(rtt, toNodeIp);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private ResourceMonitor() {
        initNode();
    }

    /*
     * Find the node list from storm configuration file and initiate the node table
     */
    private void initNode() {

        for (int i = 0; i < ClusterNodeList.length; i++) {
            Node node = new Node(ClusterNodeList[i]);
            nodeTable.put(ClusterNodeList[i], node);
        }
    }

    private Node getNode(String name) {
        return nodeTable.get(name);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Node> entry : nodeTable.entrySet()) {
            String node = entry.getKey();
            sb.append(getNode(node));
        }
        return sb.toString();
    }

    public class Node {
        private final String ipAddr;
        private final int ClusterSize = 0;
        private double cpuUtilizationFree = 0.0;
        private double cpuFrequency = 0.0;
        private double memoryFree = 0.0;
        private int cpuNum = 1;
        private double maxCpuUtilization = 0.0;
        private double localUploadBandwidth = 0.0;
        private double localDownloadBandwidth = 0.0;
        private double uploadBandwidth = 1000.0;
        private double downloadBandwidth = 1000.0; //Mbps
        private Map<String, Double> uploadBandwidthMap = new Hashtable<>();
        private Map<String, Double> downloadBandwidthMap = new Hashtable<>();
        private Map<String, Double> rttMap = new Hashtable<>();

        public Node(String ip) {
            this.ipAddr = ip;
        }

        public String getNodeIp() {
            return ipAddr;
        }

        public double getCpuUtilizationFree() {
            return cpuUtilizationFree;
        }

        public double getCpuFrequency() {
            return cpuFrequency;
        }

        public double getMemoryFree() {
            return memoryFree;
        }

        public double getLocalUploadBandwidth() {
            return localUploadBandwidth;
        }

        public double getLocalDownloadBandwidth() {
            return localDownloadBandwidth;
        }

        public double getUploadBandwidth(String nodeIp) {
            return uploadBandwidthMap.get(nodeIp);
        }

        public double getDownloadBandwidth(String nodeIp) {
            return downloadBandwidthMap.get(nodeIp);
        }

        public double getUploadBandwidth() {
            return uploadBandwidth;
        } //byte

        public double getDownloadBandwidth() {
            return downloadBandwidth;
        }

        public int getCpuNum() {
            return cpuNum;
        }

        public double getMaxCpuUtilization() {
            return maxCpuUtilization;
        }

        public double getRtt(String nodeIp) {
            return rttMap.get(nodeIp);
        }

        public void setMaxCpuUtilization(double val) {
            maxCpuUtilization = val;
        }

        public void setCpuNum(int val) {
            cpuNum = val;
        }

        public void setCpuUtilizationFree(double val) {
    
	    cpuUtilizationFree = Math.min(100, Math.max(3.0, val));
	    
        }

        public void setCpuFrequency(double val) {
            cpuFrequency = val;
        }

        public void setMemoryFree(double val) {
            memoryFree = val;
        }

        public void setLocalUploadBandwidth(double val) {
            localUploadBandwidth = val;
        }

        public void setLocalDownloadBandwidth(double val) {
            localDownloadBandwidth = val;
        }

        public void setUploadBandwidth(double val, String nodeIp) {
            uploadBandwidth = val;
        }

        public void setDownloadBandwidth(double val, String nodeIp) {
            downloadBandwidthMap.put(nodeIp, val);
        }

        public void setUploadBandwidth(double val) {
            uploadBandwidth = val;
        }

        public void setDownloadBandwidth(double val) {
            downloadBandwidth = val;
        }

        public void setRtt(double val, String nodeIp) {
            rttMap.put(nodeIp, val);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("[Node]" + ipAddr + "\t");
            sb.append("cpuFrequency " + getCpuFrequency() + " MHz\t");
            sb.append("cpuUtilization " + getCpuUtilizationFree() + " %\t");
            sb.append("cpu core num " + getCpuNum() + " \t");
            sb.append("memory " + getMemoryFree() + " MB\t");
            sb.append("upload bw " + getUploadBandwidth() + " MBps\t");
//            for (Map.Entry<String, Node> entry : nodeTable.entrySet()) {
//                String node = entry.getKey();
//                sb.append("uploadBandwidth" + node + " " + getUploadBandwidth(node) + "\t");
//                sb.append("downloadBandwidth" + node + " " + getDownloadBandwidth(node) + "\t");
//                sb.append("rtt" + node + " " + getRtt(node) + "\t");
//            }

            sb.append("\n");
            return sb.toString();
        }
    }


    public static void main(String args[]) throws Exception {
        ResourceMonitor rm = getResourceMonitor();
        //rm.collectMemResource("node21-1");
        rm.collectResource();
        System.out.println(rm);
    }
}

