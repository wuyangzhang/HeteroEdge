
resourceCollectorPath=/root/ar/scheduler/src/jvm/storm/ResourceCollector.sh

function speedTest(){

    # Usage:
    #   ./scp-speed-test.sh user@hostname [test file size in kBs]
    #

    ssh_server=$1
    test_file=".resource.sh"

    # Optional: user specified test file size in kBs
    if test -z "$2"
    then
      test_size="1000" #KB
    else
      test_size=$2
    fi


    # generate a 10MB file of all zeros

    echo "Generating $test_size kB test file $test_file..."
    `dd if=/dev/zero of=$test_file bs=$(echo "$test_size*1024" | bc) \
      count=1 &> /dev/null`

    # upload test
    echo "Testing upload file $test_file to $ssh_server..."
    #up_speed= `scp -o StrictHostKeyChecking=no -v $test_file $ssh_server:$test_file  | \
    #  grep "Bytes per second" | \
    #  sed "s/^[^0-9]*\([0-9.]*\)[^0-9]*\([0-9.]*\).*$/\1/g"`

    up_speed="$(script -q -c "scp -v $test_file $ssh_server:$test_file")" 
    
    up_speed_val1=$(echo "$up_speed" | grep "Bytes per second" | \
      sed "s/^[^0-9]*\([0-9.]*\)[^0-9]*\([0-9.]*\).*$/\1/g")

    #echo $up_speed_val1
 
    # download test
    #echo "Testing download from $ssh_server..."
    #down_speed=`scp -o StrictHostKeyChecking=no  -v $ssh_server:$test_file $test_file  | \
    #  grep "Bytes per second" | \
    #  sed "s/^[^0-9]*\([0-9.]*\)[^0-9]*\([0-9.]*\).*$/\2/g"`
    #down_speed=$(echo "($down_speed/1000)" | bc)

    # clean up

    echo "Removing test file on $ssh_server..."
    `ssh $ssh_server "rm $test_file"`
    echo "Removing test file locally..."
    `rm $test_file`

    # # print result
    echo "Upload speed:$up_speed_val1:Bps"
    #echo "Download speed:$down_speed:MBps"
}


function ping(){
       rtt=rtt-${1}-${2}

       #access remote nodes
       ssh -o "StrictHostKeyChecking no" $1 <<+

       #execute remote bw test
       ping -c 1 node21-2 | grep rtt | cut -d '=' -f 2 | cut -d '/' -f 2 > $rtt

       #fetch bw data from remote nodes
       scp -o "StrictHostKeyChecking no" $rtt $3

       #clean data
       rm $rtt
+
}


function getMem(){
    
    n=mem-${1}
    ssh -o "StrictHostKeyChecking no" $1<<+
    touch $n
    free -m | grep --line-buffered Mem | awk '{print \$4}' > $n
    scp -o "StrictHostKeyChecking no" $n $2;
    rm $n
+

}

# when query cpu, we will retrieve the "core num", "CPU utilization" and "CPU max frequency".
function getCpu(){

    n=cpu-${1}
    echo [Query] cpu info from [$1] and save to file [$n]
    ssh -o "StrictHostKeyChecking no" $1 <<+
    touch $n
    nproc >> $n
    uptime | cut -d ',' -f 4 | cut -d ':' -f 2 | sed 's/ //g' >> $n
    lscpu | grep --line-buffered MHz | cut -d ':' -f 2 | sed 's/ //g'>> $n
    echo [Send] [$n] to [$2]
    scp -o "StrictHostKeyChecking no" $n $2
    rm $n
+

}

function getBw(){
    scp -o "StrictHostKeyChecking no" $resourceCollectorPath $1:~/
    upload=upload-${1}
    download=download-${1}
    bandwidth=bw-${1}-${2}

    #access remote nodes
    ssh -o "StrictHostKeyChecking no" $1 <<+

    #execute remote bw test
    echo test speed from $1 to $2
    ./ResourceCollector.sh speed $2 > bw.data
    echo test done!
    cat bw.data | grep --line-buffered Upload | cut -d ":" -f 2 | tee $upload
    #cat bw.data | grep --line-buffered Download | cut -d ":" -f 2 | tee $download
   
    touch $bandwidth
    cat $upload >> $bandwidth
    #cat $download >> $bandwidth

    #fetch bw data from remote nodes
    scp -o "StrictHostKeyChecking no" $bandwidth $3

    #clean data
    rm bw.data
    rm $bandwidth
+
}

# $1 command, $2 query node, $3 remote saving path
case $1 in
mem)
	getMem $2 $3
;;

cpu)
	getCpu $2 $3

;;

bw)

	getBw $2 $3 $4
# $2 fromNode, $3 toNode, $4 resultSavePath
;;

speed)
	speedTest $2 
	wait
;;

rtt)
	ping $2 $3 $4

;;
esac
