package storm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Comparator;
import java.util.TreeMap;
import java.util.Hashtable;

import org.apache.storm.scheduler.Cluster;
import org.apache.storm.scheduler.EvenScheduler;
import org.apache.storm.scheduler.ExecutorDetails;
import org.apache.storm.scheduler.IScheduler;
import org.apache.storm.scheduler.SupervisorDetails;
import org.apache.storm.scheduler.Topologies;
import org.apache.storm.scheduler.TopologyDetails;
import org.apache.storm.scheduler.WorkerSlot;
import org.apache.storm.scheduler.SchedulerAssignment;

import java.util.logging.Logger;
import java.util.logging.FileHandler;

public class LatencyFirstScheduler implements IScheduler {

    private static final String SCHEDULER_LOGFILE = "/root/scheduler.log";
    private static final String TOPOLOGY_NAME = "AR";
    private Map<String, TopologyManager> nodeTopologyManagerMap = new Hashtable<>();

    private static final boolean debug = true;

    public static class StormLogger {
        private static Logger Log;

        private void initLogger() {
            try {
                FileHandler handler = new FileHandler(SCHEDULER_LOGFILE, true);
                Log = Logger.getLogger("storm");
                Log.addHandler(handler);
            } catch (java.io.IOException ex1) {
                System.out.println("[Error] Failt to open" + SCHEDULER_LOGFILE);
            }

        }

        private StormLogger() {
            initLogger();
        }

        private static StormLogger stormLogger = new StormLogger();

        public static Logger getLogger() {
            return stormLogger.Log;
        }
    }

    private static final Logger log = StormLogger.getLogger();

    /* resource monitor */
    private ResourceMonitor resourceMonitor = ResourceMonitor.getResourceMonitor();

    /* topology information */
    private List<TopologyManager> topologyManagerList = new ArrayList<>();
    private Map<String, List<ExecutorDetails>> executorToNodeMap = new Hashtable<>();
    private Map<String, List<String>> nodeContainsTaskMap = new Hashtable<>();


    /*
        retrieve all tasks to be scheduled and sort them according to compute latency!
     */
    private Set<String> sortTask(TopologyManager topologyManager) {
        HashMap<String, Double> map = new HashMap<>();
        LatencyComparator latencyComparator = new LatencyComparator(map);
        TreeMap<String, Double> sort_map = new TreeMap<>(latencyComparator);

        for (String task : topologyManager.getTaskList()) {
            double computeLatency = topologyManager.getTaskLatency(task, 1);
            map.put(task, computeLatency);
        }

        sort_map.putAll(map);
        return sort_map.keySet();
    }


    private class LatencyComparator implements Comparator<String> {
        Map<String, Double> base;

        public LatencyComparator(Map<String, Double> base) {
            this.base = base;
        }

        public int compare(String a, String b) {
            if (base.get(a) > base.get(b)) {
                return -1;
            } else {
                return 1;
            }
        }
    }

    private boolean hasGPU(String node) {
        for (int i = 0; i < ResourceMonitor.GPUNodeList.length; i++) {
            if (ResourceMonitor.GPUNodeList[i].equals(node)) {
                return true;
            }
        }
        return false;
    }

    private double computeOverallLatencyForTask(String task, String node) {

        // check memory is available. if not, return infinite latency
        if(debug) {
            log.info("compute latency of task " + task + " on node " + node);
        }
        double memoryFree = resourceMonitor.nodeTable.get(node).getMemoryFree();
        double memoryDemand = nodeTopologyManagerMap.get(node).getTaskMemory(task);
        if (memoryFree < memoryDemand) {
            return Integer.MAX_VALUE;
        }

        // estimate computing latency in ms
        double cpuUtilization = resourceMonitor.nodeTable.get(node).getCpuUtilizationFree();
        double computingLatency = nodeTopologyManagerMap.get(node).getTaskLatency(task, cpuUtilization);

        // estimate network transmission latency in ms
        double uploadBandwidth = resourceMonitor.nodeTable.get(node).getUploadBandwidth();
        double transmissionLatency = 0.0;
        if(uploadBandwidth > 0){
            transmissionLatency = nodeTopologyManagerMap.get(node).getTaskOutput(task) / uploadBandwidth * 1000; // task unit MB, BW unit MBps, to ms
        }else{
            transmissionLatency = Integer.MAX_VALUE;
        }

        double sum = computingLatency + transmissionLatency;

        if (debug) {
            log.info("[LatencyFirstScheduler] node [" + node + "] utilization= " + cpuUtilization + " cpu latency = " + computingLatency + " ms bandwidth= " + uploadBandwidth + " bps uploadLatency = " + transmissionLatency + " ms sum = " + sum + " ms");
        }

        return sum;
    }


    private String findOptimalNodeForComponent(String task, List<String> supervisorHostList) {


        // we need to get available resource of all nodes 
        double lowestLatency = Integer.MAX_VALUE;
        String optimalNode = null;

        for (String node : supervisorHostList) {
            double latency = computeOverallLatencyForTask(task, node);
            if (latency < lowestLatency) {
                lowestLatency = latency;
                optimalNode = node;
            }
        }

        return optimalNode;
    }


    private void updateNodeResource(String nodename, String task, Map<String, List<ExecutorDetails>> componentToExecutors) {

        ResourceMonitor.Node node = resourceMonitor.nodeTable.get(nodename);
        //update memory
        double memoryFree = node.getMemoryFree();
        double memoryDemand = nodeTopologyManagerMap.get(nodename).getTaskMemory(task);
        node.setMemoryFree(memoryFree - memoryDemand); // MB - MB

        //update cpu utilization
        double cpuUtilizationFree = node.getCpuUtilizationFree();

	//        if(!hasGPU(nodename) || !task.equals("disparityBolt")){
            double cpuUtilizationDemand = nodeTopologyManagerMap.get(nodename).getTaskCostUtil(task, cpuUtilizationFree);
            node.setCpuUtilizationFree(cpuUtilizationFree + cpuUtilizationDemand);
	    log.info("[LatencyFirstScheduler] demand cpu utilization : " + cpuUtilizationDemand + " after update free cpu util: " + node.getCpuUtilizationFree());
	    //}

        //update bandwidth
        double uploadBandwidthFree = node.getUploadBandwidth(); //MBps
        double uploadBandwidthDemand = nodeTopologyManagerMap.get(nodename).getTaskUpBandwidth(task, cpuUtilizationFree); //MBps
        double remainUploadBandwidth = Math.max(uploadBandwidthFree - uploadBandwidthDemand, 0);
        node.setUploadBandwidth(remainUploadBandwidth);
        if(debug) {
            log.info("[LatencyFirstScheduler] update resource node " + node + " free uploadBanwidth= " + uploadBandwidthFree + " uploadBandwidthDemand= " + uploadBandwidthDemand + " remainBandwidth= " + remainUploadBandwidth + "\n");
        }
    }

    private void scheduleComponent(String task, Map<String, List<ExecutorDetails>> componentToExecutors, List<String> supervisorsList) {

        List<ExecutorDetails> executors = componentToExecutors.get(task);
        if (executors == null) {
            return;
        }

        for (ExecutorDetails ed : executors) {

            String optimalNode = findOptimalNodeForComponent(task, supervisorsList);

            if (!executorToNodeMap.containsKey(optimalNode)) {
                List<ExecutorDetails> executorDetailsList = new ArrayList<>();
                executorToNodeMap.put(optimalNode, executorDetailsList);
            }

            //put executorDetails to node list
            executorToNodeMap.get(optimalNode).add(ed);

            //update the resoure cost on the optimal node
            updateNodeResource(optimalNode, task, componentToExecutors);

            //record the result
            if (nodeContainsTaskMap.get(optimalNode) == null) {
                List<String> taskList = new ArrayList<>();
                taskList.add(task);
                nodeContainsTaskMap.put(optimalNode, taskList);
            } else {
                nodeContainsTaskMap.get(optimalNode).add(task);
            }

            if (debug) {
                log.info("to assign component " + task + " optimal node " + optimalNode);
            }
        }

    }

    /*
      @In scheduleComponent function , it just assign a task to a specific node. 
      Then in each node, we assign all tasks to available workslots in round robin way
    */
    private void assignComponentToSlot(Cluster cluster, TopologyDetails topology, Map<String, List<WorkerSlot>> supervisorToAvailableslots) {

        for (String node : executorToNodeMap.keySet()) {
            List<ExecutorDetails> executorDetailsList = executorToNodeMap.get(node);
            //log.info("assign task to slot");
            //log.info("node " + node + "executor" + executorDetailsList);
            int availableSlotNum = supervisorToAvailableslots.get(node).size();

            for (int slot = 0; slot < availableSlotNum; slot++) {
                List<ExecutorDetails> executorListToSlot = new ArrayList<>();
                for (int executor = 0 + slot; executor < executorDetailsList.size(); executor += availableSlotNum) {
                    executorListToSlot.add(executorDetailsList.get(executor));
                }

                WorkerSlot workerSlot = supervisorToAvailableslots.get(node).get(slot);
                if (workerSlot != null) {
                    cluster.assign(workerSlot, topology.getId(), executorListToSlot);
                    if (debug) {
                        log.info("[LatencyFirstScheduler] Assign executor " + executorListToSlot + " to slot: [ " + workerSlot.getNodeId() + ", " + workerSlot.getPort() + "]");
                    }
                } else {
                    log.info("cannot get the slot");
                }
            }
        }
    }

    @Override
    public Map config(){
        return null;
    }

    @Override
    public void prepare(Map config) {
        try {
            log.info("[LatencyFirstScheduler] Start to monitor resource!");
            resourceMonitor.collectResource();
            resourceMonitor.nodeTable.get("node21-1.grid.orbit-lab.org").setUploadBandwidth(1025); //MBps
            resourceMonitor.nodeTable.get("node21-2.grid.orbit-lab.org").setUploadBandwidth(1025); 
	    resourceMonitor.nodeTable.get("node21-3.grid.orbit-lab.org").setUploadBandwidth(1025);
            resourceMonitor.nodeTable.get("node21-4.grid.orbit-lab.org").setUploadBandwidth(1025);
            resourceMonitor.nodeTable.get("node21-5.grid.orbit-lab.org").setUploadBandwidth(1025);
            resourceMonitor.nodeTable.get("node21-6.grid.orbit-lab.org").setUploadBandwidth(1025);
            resourceMonitor.nodeTable.get("node21-7.grid.orbit-lab.org").setUploadBandwidth(1025);
	    
	    resourceMonitor.nodeTable.get("node21-1.grid.orbit-lab.org").setCpuUtilizationFree(3.0);
	    resourceMonitor.nodeTable.get("node21-2.grid.orbit-lab.org").setCpuUtilizationFree(3.0);
	    resourceMonitor.nodeTable.get("node21-3.grid.orbit-lab.org").setCpuUtilizationFree(3.0);
	    resourceMonitor.nodeTable.get("node21-4.grid.orbit-lab.org").setCpuUtilizationFree(3.0);
	    resourceMonitor.nodeTable.get("node21-5.grid.orbit-lab.org").setCpuUtilizationFree(3.0);
	    resourceMonitor.nodeTable.get("node21-6.grid.orbit-lab.org").setCpuUtilizationFree(3.0);
	    resourceMonitor.nodeTable.get("node21-7.grid.orbit-lab.org").setCpuUtilizationFree(3.0);
	    log.info(resourceMonitor.toString() + "\n[LatencyFirstScheduler] resource monitor task is done!");
        } catch (Exception e) {
            log.info("FAIL to get resource from node!");
        }
    }


    @Override
    public void schedule(Topologies topologies, Cluster cluster) {

        //-------------------------------------LatencyFirst Scheduler starts to schedule!------------------------------------;

        Collection<WorkerSlot> usedslots = cluster.getUsedSlots();
        Collection<SupervisorDetails> supervisors = cluster.getSupervisors().values();
        List<String> supervisorHostList = new ArrayList<String>();
        Map<String, Integer> supervisorAvailableSlotNum = new HashMap<String, Integer>();  // store supervisor available slots
        Map<String, List<WorkerSlot>> supervisorAvailableSlots = new HashMap<String, List<WorkerSlot>>();  //slots<WorkerSlot>


        // init available supervisor slots
        for (SupervisorDetails supervisor : supervisors) {
            String host = supervisor.getHost();
            supervisorHostList.add(host);
            List<WorkerSlot> availableSlots = cluster.getAvailableSlots(supervisor);
            supervisorAvailableSlots.put(host, availableSlots);
            int slotSize = availableSlots.size();
            supervisorAvailableSlotNum.put(host, slotSize);
        }

        if (debug) {
            for (String host : supervisorHostList) {
                log.info("------------------supervisor named " + host + " ------------------");
                log.info(host + " has  < " + supervisorAvailableSlotNum.get(host) + " > availableslots and the slots list :" + supervisorAvailableSlots.get(host));
                log.info("-------------------------------------end------------------------------------");
            }
        }

        //start to schedule the topology
        TopologyDetails topology = topologies.getByName(TOPOLOGY_NAME);
        if (topology != null) {
            boolean needsScheduling = cluster.needsScheduling(topology);

            for (String host : supervisorHostList) {
                String topologyLogFile = "/root/ar/utilization/" + host + "/allLog_";

                TopologyManager topologyManager = new TopologyManager(topologyLogFile);
                try {
                    topologyManager.init();
                } catch (Exception e) {
                    System.err.println(e);
                }
                nodeTopologyManagerMap.put(host, topologyManager);
            }


            if (!needsScheduling) {
                log.info("[LatencyFirstScheduler] topology {} DOES NOT NEED schedule!" + TOPOLOGY_NAME);
                return;
            } else {
                log.info("[LatencyFirstScheduler] starts to schedule topology !" + TOPOLOGY_NAME);
            }


            //prepare all tasks to be scheduled
            Map<String, List<ExecutorDetails>> componentToExecutors = cluster.getNeedsSchedulingComponentToExecutors(topology);
            log.info("[LatencyFirstScheduler] Tasks to be scheduled: " + componentToExecutors);

            //scheduling result.
            SchedulerAssignment currentAssignment = cluster.getAssignmentById(topology.getId());

            if (debug) {
                if (currentAssignment != null) {
                    log.info("[LatencyFirstScheduler] current assignments: " + currentAssignment.getExecutorToSlot());
                } else {
                    log.info("[LatencyFirstScheduler] current assignments is empty");
                }
            }


            /* sort the task list according to the latency cost. We give the priority to the task with high latency */

            Set<String> taskAllocationInOrderList = sortTask(nodeTopologyManagerMap.get(supervisorHostList.get(0)));

            for(String host : supervisorHostList){
                if(!hasGPU(host)){
                    taskAllocationInOrderList = sortTask(nodeTopologyManagerMap.get(host));
                    break;
                }
            }

            if (debug) {
                log.info("[LatencyFirstScheduler] Schedule task in the order of :" + taskAllocationInOrderList);
            }

            executorToNodeMap.clear();

            // schedule task to a workSlot...
            for (String task : taskAllocationInOrderList) {
                log.info("[LatencyFirstScheduler] to schedule " + task);
                scheduleComponent(task, componentToExecutors, supervisorHostList);
            }

            //delete
            log.info("[LatencyFirstScheduler] finish schedule and start to assign");
            log.info("[LatencyFirstScheduler] node mapping" + nodeContainsTaskMap);
            assignComponentToSlot(cluster, topology, supervisorAvailableSlots);

        }

        //assign the remaining system task storm default even scheduler will handle the rest work.
        new EvenScheduler().schedule(topologies, cluster);

    }


}
