#!/usr/bin/env bash
#server set 7 nodes in sb4

#grid
serverSet=(10.10.21.1 10.10.21.2 10.10.21.3 10.10.21.4 10.10.21.5 10.10.21.6 10.10.21.7)
master=root@10.10.21.1

#sb9
#serverSet=(10.19.1.7 10.19.1.9 10.19.1.10 10.19.1.11 10.19.1.12)
#master=root@10.19.1.7

#sb4
#serverSet=(10.14.1.3 10.14.1.4 10.14.1.5 10.14.1.6 10.14.2.1)
#master=root@10.14.1.3

serverCount=$((${#serverSet[@]}))

#1 Start and stop storm
    #1.1 Start nimbus & ui
    #1.2 configure zookeeper
    #1.3 start slave nodes
    #1.4 configure storm ip
    #1.5 update ubuntu package
    #1.6 clean storm log
    #1.7 update code
    #1.8 stop storm
    #1.9 restart node

#2 Topology compile

#3 Topology submission & kill

#4 Log Collection

#5 Cpu Control
    #5.1 Cpu frequency control & check
    #5.2 Cpu utilization control
    #5.3 Request Interval control

#6 Scheduling control

#7 experiment 

###########################################################################################################
###########################################################################################################
#####------------1----start and stop storm---------------#####
###########################################################################################################
###########################################################################################################

function runStorm(){

   for((i=0; i < $serverCount; i++))
   do
       cleanStormLogs root@${serverSet[$i]}
   done

   slaveRunning
   masterRunning
}

#---------------1.1----Start nimbus & ui---------------#
function masterRunning(){
    ssh $master <<+
    /usr/local/storm/bin/storm nimbus >/dev/null 2>&1 &
    /usr/local/storm/bin/storm ui >/dev/null 2>&1 &
+
}

#---------------1.2----configure zookeeper--------------------#
function configureZookeeper(){
   for((i=0; i<$serverCount; i++))
   do
    id=$((i+1))
    ssh root@${serverSet[$i]} <<+
    echo $id > /usr/local/zookeeper/tmp/myid;
+
   done
}

#---------------1.3-----Start slave nodes---------------#
function slaveRunning(){
   for((i=0; i<$serverCount; i++))
   do
       slaveConfigure root@${serverSet[$i]} >/dev/null 2>&1 &  
   done
   wait
}

#---------------1.4-----configure storm ip-----------------#

function runZookeeper(){
	for((i=0; i<$serverCount; i++))
   do
       __runZookeeper root@${serverSet[$i]} >/dev/null 2>&1 &  
   done
   wait
}

function __runZookeeper(){
	ssh $1 <<+
    /usr/local/zookeeper/bin/zkServer.sh restart
+
}

function slaveConfigure(){
    ssh $1 <<+
    /usr/local/zookeeper/bin/zkCli.sh rmr /storm
    /usr/local/zookeeper/bin/zkServer.sh restart
    /usr/local/storm/bin/storm supervisor >/dev/null 2>&1 &  
+
}

function _updateStormConfigure(){
    echo "accesing node", $1
    ip=$(echo $1 | cut -d '@' -f 2)
    scp /root/ar/storm/storm.yaml $1:/usr/local/storm/conf/
    scp /root/ar/storm/zoo.cfg $1:/usr/local/zookeeper/conf/
    ssh $1 <<+

    #zookeeper configure
    sed -i '5s/.*/initLimit=50/' /usr/local/zookeeper/conf/zoo.cfg
    sed -i '8s/.*/syncLimit=15/' /usr/local/zookeeper/conf/zoo.cfg

    #storm configure

    #master ip addr
    #sed -i '1s/.*/nimbus.seeds: ["10.10.20.1"]/' /usr/local/storm/conf/storm.yaml
    #sed -i '3s/.*/worker.heap.memory.mb: 4096/' /usr/local/storm/conf/storm.yaml
    #sed -i '5s/.*/supervisor.scheduler.meta:/' /usr/local/storm/conf/storm.yaml
    #sed -i '6s/.*/    tag: $ip/' /usr/local/storm/conf/storm.yaml

    #scheduler
    #sed -i '8s/.*/#storm.scheduler: storm.EmptyScheduler/' /usr/local/storm/conf/storm.yaml  

    #sed -i '10s/.*/supervisor.slots.ports:/' /usr/local/storm/conf/storm.yaml
    #sed -i '16s/.*/storm.zookeeper.servers:/' /usr/local/storm/conf/storm.yaml

    #prevent server from exiting ssh
    sudo sed -i '86s/.*/ClientAliveInterval 60000/' /etc/ssh/sshd_config
    sudo sed -i '87s/.*/ClientAliveCountMax 0/' /etc/ssh/sshd_config    

    #storm scheduler configuration
    #add name to supervisor nodes
+
}

function updateStormConfigure(){
    echo 'update zookeeper configuration'
    configureZookeeper
    for((i=0; i<$serverCount; i++))
    do 
    _updateStormConfigure root@${serverSet[$i]} &
    updatePackage root@${serverSet[$i]} &
   done
   wait
}

#---------------1.5-----update all required ubuntu package-----------------#

function updatePackage(){
    ssh $1 <<+
    apt-get install -y midori
    apt-get install -y cpulimit
    apt-get install -y stress
    apt-get install -y cpufrequtils
    apt-get install -y linux-tools-common
    apt-get install -y  linux-tools-3.13.0-108-generic
    apt-get install -y  linux-tools-generic
+
}

#---------------1.6-----clean storm logs-----------------#
function cleanStormLogs(){
    ssh $1 'rm -r /usr/local/storm/storm-local; rm -r /usr/local/storm/logs/*'
}

#---------------1.7-----update storm code-----------------#
function updateCode(){
    for((i=1; i<$serverCount; i++))do
       ssh root@${serverSet[$i]} 'rm -r /root/ar;' 
       scp -r /root/ar root@${serverSet[$i]}:/root >/dev/null 2>&1 &                                                                                           
   done
   wait
}

function compileCode(){
    for((i=0; i<$serverCount; i++))do
        ssh root@${serverSet[$i]} 'cd /root/ar/cpp/Debug/; make clean; make; make lib'
   done
   wait
}


#---------------1.8----stop storm-----------------#
function _stopStorm(){

    echo "now kill all storm processes"
    ssh $1 <<+
    jps -l | grep core | cut -d ' ' -f 1 | xargs -rn1 kill
    jps -l | grep nimbus | cut -d ' ' -f 1 | xargs -rn1 kill
    jps -l | grep QuorumPeerMain | cut -d ' ' -f 1 | xargs -rn1 kill
    jps -l | grep supervisor | cut -d ' ' -f 1 | xargs -rn1 kill
    jps -l | grep worker | cut -d ' ' -f 1 | xargs -rn1 kill
+
}

function stopStorm(){
   for((i=0; i<$serverCount; i++))
    do
        _stopStorm root@${serverSet[$i]}
    done
}

#---------------1.9----reboot node-----------------#
function _reboot(){
  ssh $1 'reboot'
}

function reboot(){
   for((i=0; i < $serverCount; i++))
   do
       _reboot root@${serverSet[$i]}
   done
}

function restartNimbus(){
    ssh $master <<+
    jps -l | grep nimbus | cut -d ' ' -f 1 | xargs -rn1 kill
    /usr/local/storm/bin/storm nimbus >/dev/null 2>&1 &

+
}
#------------------------------------------------------#

###########################################################################################################
###########################################################################################################
###########################################################################################################
####################---------------2----Topology compile------------------####
###########################################################################################################
###########################################################################################################
###########################################################################################################

function compileTopology(){
    ssh $master ' cd /root/ar/storm; make'
}

#------------------------------------------------------#



####---------------3----Topology submission & kill------------------####
function prepareSubmit(){
    ssh $1 <<+
    rm /root/reconstruction.log*
    rm -r /usr/local/storm/logs/workers-artifacts/*
+
}


function submitTopology(){
    
    for((i=0; i<$serverCount; i++))
    do
	prepareSubmit root@${serverSet[$i]}
    done

    cd /root/ar/storm;
    case $1 in
    serial)
	    make serialC
	    ;;
    nonPipe)
	    make nonPipeC
	    ;;
    parallel)
	    make parallelC
	    ;;
    esac
}


function submitStandAloneTopology(){

    prepareSubmit $1
    ssh $1 <<+
    cd /root/ar/storm
    make parallel
+
}
#------------------------------------------------------#


###########################################################################################################
###########################################################################################################
###########################################################################################################
#---------------3.1----Topology kill------------------####
###########################################################################################################
###########################################################################################################
###########################################################################################################

function killTopology(){
    echo "Kill AR topology!"
    /usr/local/storm/bin/storm kill AR
}

function killStandAloneTopology(){
ssh $1 <<+
    jps -l | grep MergeParallelReconstructionTopology | cut -d ' ' -f 1 | xargs -rn1 kill
+
}

#---------------Topology state check------------------#
function checkTopologyStatus(){
echo "Check status of VR topology!"
    ssh root@${serverSet[0]} <<+
    /usr/local/storm/bin/storm list
+
}

function _checkZookeeper(){
     printServerAddr $1
     ssh $1 <<+
     jps
     /usr/local/zookeeper/bin/zkServer.sh status
+
}

function checkZookeeper(){
 for((i=0; i<$serverCount; i++))
    do
        _checkZookeeper root@${serverSet[$i]}
    done
}

###########################################################################################################
###########################################################################################################
###########################################################################################################
#----------------4----Log Collection----------------------------------#
###########################################################################################################
###########################################################################################################
###########################################################################################################

function collectLog(){

    #prepare the folder to collect the logs
    mkdir $1

    for((i=0; i<$serverCount; i++))
    do
        ssh root@${serverSet[$i]}<<+
	    echo 'collect log in ' ${serverSet[$i]}
            #rename the log and scp back to the master node
            cat reconstruction.log* > relog.${serverSet[$i]}
	    
            scp -o StrictHostKeyChecking=no /root/relog.${serverSet[$i]} $master:$1
            #rm storm.log.[0-9]
	    #rm log.${serverSet[$i]}
+
    done

    cd $1
    touch allLog.log
    cat relog.* > allLog.log
    rm relog.*
}

function _collectLog(){
#      n=${1}.log
     
#      ssh $1 <<+
#      cd /usr/local/storm/logs
#      rm $n
#      touch $n
#      find . -name "worker.log" -exec cat {} > $n +
# +
#      addr=$1:/usr/local/storm/logs/$n
#      echo $addr
#      scp  $addr ./logs
    echo 'will not use'
}

function collectSingleAloneLog(){
    scp $1:/root/reconstruction.log ./utilization/$1/
}


###########################################################################################################
###########################################################################################################
###########################################################################################################
################################# 5----Cpu Control ########################################################
###########################################################################################################
###########################################################################################################

#----------------5.1----Cpu frequency control & check--------------------------------#


function cpuFrequencyCheck(){
    ssh $1 'cpupower frequency-info | grep 'available frequency''
}

function cpuFrequencySet(){
    echo set cpu frequency $2 on $1
    ssh $1 'cpupower frequency-set -f \'$2';'
}

function bandwidthControl(){
        ssh $1<<+
	tc qdisc add dev eth1 handle 1: root htb default 11
	tc class add dev eth1 parent 1: classid 1:1 htb rate $2
	tc class add dev eth1 parent 1:1 classid 1:11 htb rate $2
+
}

function releaseBandwidthControl(){
        ssh $1<<+
	tc qdisc del dev eth1 root
+
}
#----------------5.2----Cpu utilization control----------------------------------#

function cpuUtilization(){

    echo [CpuUtilization] set cpu utilization $2 on $1

    utilization=$2

    ssh $1 '

    # start cpu intensive application 'yes'
        coreNum=$(cat /proc/cpuinfo | grep processor | wc -l);
        echo core number '\$coreNum'
        for((i=0; i < '\$coreNum'; i++)){
            echo '\$i' start cpu intensive process yes
            yes > /dev/null &
        }
        sleep 0.1
    # start limit cpu utilization of the process 'yes'
        count=0
        pgrep yes | \
            while read i; do
                echo $count cpu limit utilization under \'$2'
                cpulimit -l \'$2' -p '\$i' >/dev/null 2>&1 &
                count=$((count+1))
            done;
'
}

function removeCpuUtilization(){
 ssh $1 '
    # kill cpulimit
        count1=0
        pgrep cpulimit | \
            while read i; do
                echo $count1 kill cpulimit process '\$i';
                kill -9 '\$i' >/dev/null 2>&1;
                count1=$((count1+1))
            done;

    # kill background workload process 'yes'
        count2=0
        pgrep yes | \
            while read i; do
                echo $count2 kill background workload process yes
                kill -9 '\$i' >/dev/null 2>&1;
                count2=$((count2+1))
            done;
'
}
#----------------5.3----Request Interval control----------------------------------#

function updateInterval(){
    ssh $master<<+ 
    sed -i "4s/.*/  final static int sendInterval = $1;/" /root/ar/storm/src/jvm/storm/topology/TopologyParameters.java;
+
}

function updatePartitionNumber(){
    ssh $master<<+
        sed -i "37s/.*/                final int partitionNum = $1;/" /root/ar/storm/src/jvm/storm/topology/MergeParallelReconstructionTopology.java;
+
}
#----------------6----Scheduling control----------------------------------#

function schedule(){
    if [ $1 == "0" ]
        then
        defaultSchedule
    elif [ $1 == "1" ]
        then
        customSchedule
    fi
}

function customSchedule(){
    echo "use custom scheduler"
    ssh $master 'sed -i '47s/.*/storm.scheduler: storm.EmptyScheduler/' /usr/local/storm/conf/storm.yaml'  
}

function defaultSchedule(){
    echo "use default scheduler"
    ssh $master 'sed -i '47s/.*/#storm.scheduler: storm.EmptyScheduler/' /usr/local/storm/conf/storm.yaml'  
}

# profile task on different node $2
function _utilization(){

        removeCpuUtilization $1
        #$1 node, $2 utilization
        cpuUtilization $1 $2

        echo submit topology, the utlization test for $1

		submitStandAloneTopology $1 & >/dev/null 2>&1
	
        sleep 30

        echo collect log from remote node $1, and save it to ./utilization/$1/
	collectSingleAloneLog $1

    mv ./utilization/$1/reconstruction.log ./utilization/$1/allLog_$2.log
	#mv /root/reconstruction.log ./utilization/$1/allLog_$1.log

	#kill topology
	echo kill topology running in $1
	killStandAloneTopology $1
    removeCpuUtilization $1

	sleep 5
}


function _utilizationCluster(){
    #set the cluster node to this cpu utilization
    
    # for((i=0; i<$serverCount; i++))
    # do
    #    cpuUtilization root@${serverSet[$i]} $1
    # done
    
    cpuUtilization $1 $u

    echo submit cluster 
    submitTopology parallel
    sleep 300
    killTopology

    sleep 30
}

function utilization(){

    rm -r utilization/$1
    mkdir utilization/$1

    for((u=1; u<=100; u=((u+10))));do
        _utilization $1 $u
		#_utilizationCluster $u
    done

    echo alldone@
}

###########################################################################################################
###########################################################################################################
#---------------------7----Experiments----------------------------------#
###########################################################################################################
###########################################################################################################

#variable 1 : image resolution
#variable 2 : fps
#variable 3 : non-pipe, pipe, different parallel level

bwSet=(50mbps 100mbps 200mbps 500mbps 1200mbps)
bwCount=$((${#bwSet[@]}))

function bwExperiment(){
    for((b=0; b<$bwCount; b++))
    do
	echo ready to set bw ${bwSet[$b]}
     	for((i=0; i<$serverCount; i++))
	do
	    bandwidthControl root@${serverSet[$i]} ${bwSet[$b]}

	done
	
	
	 echo submit cluster
	 submitTopology parallel
	 sleep 300
	 killTopology
	 sleep 30

	for((i=0; i<$serverCount; i++))
        do
            releaseBandwidthControl root@${serverSet[$i]} ${bwSet[$b]}
        done
    done
}

function executeExperiment(){

    echo 'start to execute the experiment'
    rm -r /root/stormLogs
    mkdir /root/stormLogs

    runTime=300
    for (( fps=10; fps <= 100; fps=((fps+10)) )); do
        echo 'test fps ' $fps

        mkdir /root/stormLogs/$fps
        #modify send interval 
        sendInterval=$((1000/fps))
	echo 'sendInterval is ' $sendInterval
        updateInterval $sendInterval

	mkdir /root/stormLogs/$fps/parallel
        for (( parallel=2; parallel <=64; parallel=((parallel*2)))); do
            echo -e '\n\n\n\n\ntest parallel version\n\n\n\n' $parallel
	    echo -e '\n\n\n\ntest fps' $fps
             #sleep a while and wait the topology to be killed
            #restart storm
#            stopStorm
            updatePartitionNumber $parallel
#	    runStorm
            compileTopology
#            sleep 15
            submitTopology parallel
	    sleep $runTime
	    killTopology
#            mkdir /root/stormLogs/$fps/parallel/$parallel
#            collectLog /root/stormLogs/$fps/parallel/$parallel
	    sleep 30
        done

        #sleep a while and make sure storm already starts.
#        sleep 30
        #submit different topology
        echo -e '\n\n\n\n\ntest serial version\n\n\n\n\n'
        submitTopology serial
        #run 200s and kill topology
        sleep $runTime
        killTopology
        #collect log from all nodes
#        mkdir  /root/stormLogs/$fps/serial
#        collectLog /root/stormLogs/$fps/serial

        #sleep a while and make sure storm already starts.
        echo -e '\n\n\n\n\ntest non-pipeline version\n\n\n\n'
        sleep 30
        submitTopology nonPipe
        sleep $runTime
        killTopology
#        mkdir /root/stormLogs/$fps/nonPipe
#        collectLog /root/stormLogs/$fps/nonPipe
	killTopology
	sleep 30
    done

    echo 'all done!'
}

case $1 in

#### start & stop & restart storm and node ####
run)
    echo 'start storm'
    runStorm
    ;;

stop)
    echo 'stop storm!'
    stopStorm
    ;;

rerun)
    echo 'restart storm!'
    stopStorm
    sleep 5
    runStorm
    ;;

restartNimbus)
    echo restart nimbus
    restartNimbus
    ;;

runZookeeper)
	echo 'run zookeeper'
	runZookeeper
	;;
rebootAll)
    echo 'reboot all nodes!'
    reboot
    ;;

reboot)
    echo reboot node $2
    _reboot $2
    ;;
#### control storm topology ####

submit)
    echo 'submit storm topology' $2
    submitTopology $2
	;;
    
kill)
    echo 'kill storm topology'
    killTopology
    ;;

#### update code and configure ####

updateStorm)
    echo 'update storm configure'
    updateStormConfigure
    ;;

updateCode)
    echo 'update storm code'
    updateCode
    ;;

compileCode)
     echo 'compile code'
     compileCode
     ;;
#### compile storm topology ####
compileStorm)
    echo 'compile storm topology'
    compileTopology
    ;;

#### log collection ####
log)
    collectLog /root/log
    ;;

#### cpu frequency control #####
cpuFreq)
  echo change cpu frequency example command: 'cpuFreq root@node1-3 2.9GHz'
  cpuFrequencySet $2 $3
  ;;

#### cpu utilization control #####

cpuUtilization)
    echo cpu utilization control example command: 'cpuUtilization root@node1-4 30'
    cpuUtilization $2 $3
    ;;

removeCpuUtilization)
	removeCpuUtilization $2
	;;
#### request interval control #####
interval)
    echo request interval control example command: 'interval root@node1-4 300'
    updateInterval $2 $3
    ;;
#### bandwidth control
bw)
    echo control bandwidth
    bandwidthControl $2 $3
    ;;
bwstop)
    echo release bandwidth control
    releaseBandwidthControl $2
    ;;
#### scheduling algorithm control ####
schedule)
    echo 'default schedule command 0; custom schedule command 1'
    schedule $2
    ;;
experiment)
    executeExperiment
    ;;

utilization)
#$2, node
    utilization $2
    ;;

bwTest)
    bwExperiment
    ;;
esac
