sudo rm /usr/local/storm/lib/original-storm-scheduler-2.0.0-SNAPSHOT.jar
sudo rm /usr/local/storm/lib/storm-scheduler-2.0.0-SNAPSHOT.jar
mvn clean package
sudo cp ~/ar/scheduler/target/*.jar /usr/local/storm/lib
