package storm;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import java.io.*;

public class TopologyManager {

    private int fps = 2;

    public double getTaskOutput(String task) {
        return taskMap.get(task).getOutputSize();
    }

    public double getTaskMemory(String task) {
        return taskMap.get(task).getMemory();
    }

//    public double getTaskCpuUtilization(String task) {
//        return taskMap.get(task).getCpuUtilization();
//    }

    public double getTaskUpBandwidth(String task, double utilization) {
        return taskMap.get(task).getUpBandwidth(utilization);
    }

    public double getTaskLatency(String task, double utilization) {
        return taskMap.get(task).getLatency(utilization);
    }

    public double getTaskCostUtil(String task, double utilization) {
        return taskMap.get(task).getCostUtil(utilization);
    }

    private final String topologyLogFile;

    private Map<String, TaskResource> taskMap = new Hashtable<>();

    public ArrayList<String> getTaskList() {
        return new ArrayList<>(taskMap.keySet());
    }

    public TopologyManager(String topologyLogFile) {
        this.topologyLogFile = topologyLogFile;
    }

    public void init() throws Exception {

        try {
            for (int i = 5; i <= 100; i += 5) {
                String logAddr = this.topologyLogFile + String.valueOf(i) + ".log";
                processTaskLog(logAddr, (double) i);
            }
            computeEstimatedCurve();
        } catch (Exception e) {
            System.err.println(e);
        }
    }

    private void processTaskLog(String logAddr, double utilization) throws Exception {
        try {

            FileReader fileReader =
                    new FileReader(logAddr);

            BufferedReader bufferedReader =
                    new BufferedReader(fileReader);

            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (line.contains("<message>")) {
                    consumeTaskLog(line, utilization);
                }
            }

            bufferedReader.close();

        } catch (FileNotFoundException ex) {
            System.out.println(
                    "Unable to open file '" +
                            logAddr + "'");

        } catch (IOException ex) {
            System.out.println(
                    "Error reading file '"
                            + logAddr + "'");
        }
    }

    private void consumeTaskLog(String line, double utilization) {

        String results[] = line.split(",");

        String taskName = results[0].split(">")[1];
        double elapseTime = Double.valueOf(results[6]);
        double costCpuUtil = Double.valueOf(results[9]) * 100; // emperically modify cost cpu utilization
        double memory = Double.valueOf(results[12]);
        double input = Double.valueOf(results[15]);
        double output = Double.valueOf(results[18]);
        //double downloadBw = input * Math.min((double) fps, 1000 / elapseTime);
        double upLoadBw = output * Math.min((double) fps, 1000 / elapseTime) * 1000 * 1000; //bps

        TaskResource taskResource = taskMap.get(taskName);
        // place static values
        if (taskResource == null) {
            taskResource = new TaskResource();
            taskMap.put(taskName, taskResource);
            taskResource.setInputSize(input);
            taskResource.setOutputSize(output);
            taskResource.setMemory(memory);
        }

        taskResource.setCostUtilizationMap(utilization, costCpuUtil);
        taskResource.setLatencyUtilization(utilization, elapseTime);
        taskResource.setBwUtilization(utilization, upLoadBw);
    }

    /*
     * 1-> loop over the task list and calculate the average task latency under each utilization setup
     * 2-> given the sampling latency under different utilizations, we fit them into a curve and use it to estimate the latency under any utilization
     */
    private void computeEstimatedCurve() {
        for (Map.Entry<String, TaskResource> entry : taskMap.entrySet()) {
            TaskResource taskResource = entry.getValue();
            taskResource.latencyCurve = taskResource.curveFit(taskResource.latencyUtilizationMap);
            taskResource.bwCurve = taskResource.curveFit(taskResource.bwUtilizationMap);
            taskResource.costCpuUtilCurve = taskResource.curveFit(taskResource.costUtilizationMap);
        }
    }


    private class TaskResource {

        //unit: MByte
        private double memory = 0.0;
        private double inputSize = 0.0;
        private double outputSize = 0.0;
//        private double upBandwidth = 0.0;
//        private double downBanwidth = 0.0;

        //public Map<Double, List<Double>> latencyUtilizationListMap = new Hashtable<>();
        private Map<Double, Double> latencyUtilizationMap = new Hashtable<>();
        private Map<Double, Double> bwUtilizationMap = new Hashtable<>();
        private Map<Double, Double> costUtilizationMap = new Hashtable<>();

        private CurveFit latencyCurve;
        private CurveFit bwCurve;
        private CurveFit costCpuUtilCurve;

        public CurveFit curveFit(Map<Double, Double> map) {
            List<Double> utilization = new ArrayList<>();
            List<Double> val = new ArrayList<>();
            for (Map.Entry<Double, Double> entry : map.entrySet()) {
                utilization.add(entry.getKey());
                val.add(entry.getValue());
            }
            CurveFit curve = new CurveFit(utilization, val);
            return curve.init(3);
        }


        public void setMemory(double val) {
            memory = val;
        }

        public void setInputSize(double val) {
            inputSize = val;
        }

        public void setOutputSize(double val) {
            outputSize = val;
        }


        public void setLatencyUtilization(double utilization, double val) {
            if (!latencyUtilizationMap.containsKey(utilization)) {
                latencyUtilizationMap.put(utilization, val);
            }
            double currLatency = (latencyUtilizationMap.get(utilization) + val) / 2;
            latencyUtilizationMap.put(utilization, currLatency);
        }

        public void setBwUtilization(double utilization, double val) {
            if (!bwUtilizationMap.containsKey(utilization)) {
                bwUtilizationMap.put(utilization, val);
            }
            double currLatency = (bwUtilizationMap.get(utilization) + val) / 2;
            bwUtilizationMap.put(utilization, currLatency);
        }

        public void setCostUtilizationMap(double utilization, double val) {
            if (!costUtilizationMap.containsKey(utilization)) {
                costUtilizationMap.put(utilization, val);
            }
            double currLatency = (costUtilizationMap.get(utilization) + val) / 2;
            costUtilizationMap.put(utilization, currLatency);
        }

        public double getMemory() {
            return memory;
        }

        public double getOutputSize() {
            return outputSize;
        }

        public double getUpBandwidth(double utilization) {
	    double latency = latencyCurve.predict(utilization);
	    double true_fps = Math.min(1000.0/latency, fps);
  
            return outputSize * true_fps;
        }

        public double getLatency(double utilization) {
            return latencyCurve.predict(utilization);
        }

        public double getCostUtil(double utilization) {
            return costCpuUtilCurve.predict(utilization);
        }
    }

    public static void main(String args[]) throws Exception {
        String topologyName = "AR";
        //String nodeList[] = new String[]{"wuyang-master", "wuyang-1", "wuyang-2"};

        String nodeList[] = new String[]{"node21-1.grid.orbit-lab.org", "node21-2.grid.orbit-lab.org",
                "node21-3.grid.orbit-lab.org", "node21-4.grid.orbit-lab.org", "node21-5.grid.orbit-lab.org",
                "node21-6.grid.orbit-lab.org", "node21-7.grid.orbit-lab.org"};

        Map<String, TopologyManager> nodeTopologyMap = new Hashtable<>();

        for (int k = 0; k < nodeList.length; k++) {
            String topologyLogFile = "/root/ar/utilization/" + nodeList[k] + "/allLog_";

            TopologyManager topologyManager = new TopologyManager(topologyLogFile);

            try {
                topologyManager.init();
            } catch (Exception e) {
                System.err.println(e);
            }
            nodeTopologyMap.put(nodeList[k], topologyManager);
        }

        TopologyManager tm = nodeTopologyMap.get("node21-7.grid.orbit-lab.org");
        for(double i = 0.5; i < 100; i+=0.5){
            double val = tm.getTaskCostUtil("disparityBolt", i);
            System.out.println("cpuUtil," + i + "," + val);
        }
    }



//        System.out.println(topologyManager.getTaskInput("rectificationBolt"));
//        System.out.println(topologyManager.getTaskMemory("partitionBolt"));
//        System.out.println(topologyManager.getTaskLatency("disparityBolt",42));
}
