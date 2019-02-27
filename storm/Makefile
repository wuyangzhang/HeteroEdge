compile:
	mvn clean package
clean:
	mvn clean
serial:
	/usr/local/storm/bin/storm jar target/storm-starter-2.0.0-SNAPSHOT.jar storm.topology.SerialReconstructionTopology

p:
	/usr/local/storm/bin/storm jar target/storm-starter-2.0.0-SNAPSHOT.jar storm.topology.MergeParallelReconstructionTopology

n:
	/usr/local/storm/bin/storm jar target/storm-starter-2.0.0-SNAPSHOT.jar storm.topology.NonPipelineTopology
d:
	/usr/local/storm/bin/storm jar target/storm-starter-2.0.0-SNAPSHOT.jar storm.topology.NonDisparityTopology
serialC:
	/usr/local/storm/bin/storm jar target/storm-starter-2.0.0-SNAPSHOT.jar storm.topology.SerialReconstructionTopology AR

pc:
	/usr/local/storm/bin/storm jar target/storm-starter-2.0.0-SNAPSHOT.jar storm.topology.MergeParallelReconstructionTopology AR
dc:
	/usr/local/storm/bin/storm jar target/storm-starter-2.0.0-SNAPSHOT.jar storm.topology.NonDisparityTopology AR
nc:
	/usr/local/storm/bin/storm jar target/storm-starter-2.0.0-SNAPSHOT.jar storm.topology.NonPipelineTopology AR
