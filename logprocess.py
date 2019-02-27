import re
import matplotlib.pyplot as plt
import numpy as np
import csv
import statsmodels.api as sm

class TaskProcess:
    p = re.compile('[(.*?)]')

    def __init__(self, tasks, features, category):

        self.category = category
        self.maxId = 0
        self.ProcessedNumPerSecond = 0

        self.MBps = 0
        self.tasks = tasks
        self.features = features
        self.taskLatencyDic = {}
        self.networkLatencyDic = {}
        self.overallLatencyDic = {}

        self.avgOverallLatency = 0
        self.stdOverallLatency = 0

        self.averageTasksLatencyList = []
        self.stdTasksLatencyList = []

        self.averageNetworkLatency = 0
        self.stdNetworkLatency = 0

        self.throughputList = []

        for task in tasks:
            self.taskLatencyDic[task] = {}
            for feature in features:
                self.taskLatencyDic[task][feature] = {}

    '''
        processing log
    '''
    def readLog(self, logAddr):

        file = open(logAddr + 'allLog.log', 'r')
        for line in file.readlines():

            if self.containsTask(line):
                result = re.findall('\[(.*?)\]', line)
                self.setFeatureNum(result)

            elif self.containsOverallLatency(line):
                result = re.findall('\[(.*?)\]', line)
                self.setOverallLatency(result)

            elif self.containsOverallCount(line):
                result = re.findall('\[(.*?)\]', line)
                self.setThroughput(result)

        self.compensateEmptyValue()
        self.filterExceptionValue()

        #calculate network latency list
        self.calculateNetworkLatency()

        #calculate system throughput
        self.getTaskTraffic()

        self.generateCSV(logAddr + 'result.csv')


    # cvs format
    #processedNumPerSecond + overallTaskSize

    #id + overalltime + network + each task time...
    def generateCSV(self, fileAddr):

        file = open(fileAddr, 'w')
        f_csv = csv.writer(file)

        firstRow = []

        firstRow.append(self.ProcessedNumPerSecond)
        firstRow.append(sum(self.getTaskTraffic()))

        f_csv.writerow(firstRow)

        for id in self.overallLatencyDic.keys():
            row = []
            row.append(id)
            row.append(self.overallLatencyDic[id])
            row.append(self.networkLatencyDic[id])
            for task in self.tasks:
                clockTime = self.taskLatencyDic[task][self.features[1]][id]
                row.append(clockTime)
            f_csv.writerow(row)

        file.close()

    def readFromCSV(self, csvAddr):

        with open(csvAddr + 'result.csv') as file:
            reader = csv.reader(file)
            for row in reader:
                if reader.line_num == 1:
                    self.ProcessedNumPerSecond = float(row[0])
                    self.MBps = float(row[0]) * float(row[1])
                    continue
                id = int(row[0])
                self.overallLatencyDic[id] = int(row[1])
                self.networkLatencyDic[id] = int(row[2])
                for i in range (len(self.tasks)):
                    self.taskLatencyDic[self.tasks[i]][self.features[1]][id] = int(row[3+i])

    '''
        If logs contains specific contents
    '''
    def containsTask(self, line):
        for task in self.tasks:
            if task in line:
                return True
        return False

    def containsOverallLatency(self, line):
        if 'overallLatency' in line:
            return True
        else:
            return False

    def containsOverallCount(self, line):
        if 'overallCount' in line:
            return True
        else:
            return False


    '''
        Extract the potentially useful numbers from logs
        
        example log line: 
        [reprojectionBolt] task is [226] timestamp is : [1516306604485] CpuTimeDiff is: [8] ms ElapseTimeDiff is: [7] ms 
        CpuUtilization is: [14.285714285714286] % MemoryDiff is: [36.87776] MB InputSize is : [0.0] 
        MB OutputSize is : [0.0] MB DownloadBandwidth is : [ 0.0] MBps UploadBandwidth is : [ 0.0] MBps
    '''
    def setFeatureNum(self, resultList):

        taskName = resultList[0]
        id = int(resultList[1])
        taskCpuTime = int(resultList[3])
        taskClockTime = int(resultList[4])
        outputSize = float(resultList[8])

        if id in self.taskLatencyDic[taskName][self.features[0]]:
            if int(taskCpuTime) < self.taskLatencyDic[taskName][self.features[0]][id]:
                return

        self.taskLatencyDic[taskName][self.features[0]][id] = taskCpuTime
        self.taskLatencyDic[taskName][self.features[1]][id] = taskClockTime
        self.taskLatencyDic[taskName][self.features[2]][id] = outputSize

    '''
        Set log values to data structures
    '''
    # throughput unit :
    # processed task per second
    def setThroughput(self, resultList):
        overallCount = int(resultList[0])
        overallTime = resultList[1]
        if overallCount > self.maxId:
            self.maxId = overallCount
            self.ProcessedNumPerSecond = float(overallCount) / float(overallTime) * 1000.0

    def setOverallLatency(self, resultList):
        id = int(resultList[0])
        overallLatency = resultList[1]
        self.overallLatencyDic[id] = int(overallLatency)

    def compensateEmptyValue(self):
        for id in self.overallLatencyDic:
            for task in self.tasks:
                for feature in self.features:
                    if id not in self.taskLatencyDic[task][feature]:
                        self.taskLatencyDic[task][feature][id] = 0


    '''
        Filter exceptional values
    '''
    def filterExceptionValue(self):
        tmp = list(self.overallLatencyDic.values())
        threshold = np.percentile(np.array(tmp), 80)

        nonExistList = []

        for id in self.overallLatencyDic:
            for task in self.tasks:
                for feature in self.features:
                    if id not in self.taskLatencyDic[task][feature]:
                        nonExistList.append(id)

        for id in nonExistList:
            if id in self.overallLatencyDic:
                del self.overallLatencyDic[id]

        filterIdList = []
        for id in self.overallLatencyDic:
            if self.overallLatencyDic[id] > threshold:
                filterIdList.append(id)

        for id in filterIdList:
            del self.overallLatencyDic[id]
            for task in self.tasks:
                for feature in self.features:
                    del self.taskLatencyDic[task][feature][id]


    '''
        Calculate the derivative values
        
        - Network latency
        - average overall latency
        - average task latency
        - average throughput
        - task traffic
    '''

    def calculateNetworkLatency(self):

        for id in self.overallLatencyDic.keys():
            overallLatency = self.overallLatencyDic[id]
            allTasksLatency = 0
            for task in self.taskLatencyDic.keys():
                allTasksLatency += self.taskLatencyDic[task][self.features[1]][id]

            networkLatency = overallLatency - allTasksLatency
            self.networkLatencyDic[id] = networkLatency

    def calculateAverageOverallLatency(self):

        self.avgOverallLatency = np.mean(list(self.overallLatencyDic.values()))
        self.stdOverallLatency = np.std(list(self.overallLatencyDic.values()))
        return self.avgOverallLatency, self.stdOverallLatency

    def calulateAverageTasksLatency(self):

        for task in self.tasks:
            avg = np.mean(list(self.taskLatencyDic[task][self.features[1]].values()))
            std = np.std(list(self.taskLatencyDic[task][self.features[1]].values()))
            self.averageTasksLatencyList.append(avg)
            self.stdTasksLatencyList.append(std)

        return self.averageTasksLatencyList, self.stdTasksLatencyList

    def calculateAverageNetworkLatency(self):

        self.averageNetworkLatency = np.mean(list(self.networkLatencyDic.values()))
        self.stdNetworkLatency = np.std(list(self.networkLatencyDic.values()))
        return self.averageNetworkLatency, self.stdNetworkLatency

    def calculateAverageThroughput(self):
        return np.mean(self.throughputList)

    def getOverallLatency(self):
        overallLatencyList = []
        for id in self.overallLatencyDic:
            overallLatencyList.append(self.overallLatencyDic[id])
        return overallLatencyList

    def getTaskTraffic(self):
        trafficList = []
        for task in self.tasks:
            tmp = self.taskLatencyDic[task][self.features[2]]
            id = next(iter(tmp))
            traffic = tmp[id]
            trafficList.append(traffic)

        trafficList[3] = trafficList[3] * self.category

        self.MBps = self.ProcessedNumPerSecond * sum(trafficList)
        return trafficList

def calculateOverallTraffic(taskTrafficList, avgThroughputList):

    #re-calculate traffic in the paralell settings
    partitionNum = 2
    for i in range(2, len(taskTrafficList)):
        taskTrafficList[i][3] = taskTrafficList[i][3] * partitionNum
        partitionNum *= 2

    overallTrafficList = []

    for task in taskTrafficList:
        overallTraffic = sum(task)
        overallTrafficList.append(overallTraffic)

    #unit: MBps
    networkTraffic = []
    for i in range(len(overallTrafficList)):
        traffic = overallTrafficList[i] * avgThroughputList[i]
        networkTraffic.append(traffic)

    return networkTraffic

'''
    Plot functions
    
    -- plot overall latency
    -- plot network traffic
    -- plot latency CDF

'''
def plotOverallLatency(overallLatencyDic):
    x = np.linspace(1, len(overallLatencyDic.keys()), len(overallLatencyDic.keys()))
    y = list(overallLatencyDic.values())
    plt.plot(x, y)
    plt.show()


def plotNetworkTraffic(overallTrafficList):

    xticksList = []
    for i in range(len(overallTrafficList)):
        tmp = 'CA_' + str(i + 1)
        xticksList.append(tmp)

    y_pos = np.arange(len(xticksList))

    plt.bar(y_pos, overallTrafficList)
    plt.xticks(y_pos, xticksList)
    plt.ylabel('Network Traffic(MBps)', fontsize = 10, fontweight = 'bold')

    plt.show()

def plotOverallLatencyCDF(overallLatencyList):

    plotList = []

    for overallLatency in overallLatencyList:
        #plt.hist(overallLatency, cumulative=True, normed=True, histtype='step')
        ecdf = sm.distributions.ECDF(overallLatency)
        x = np.linspace(min(overallLatency), max(overallLatency), len(overallLatency))
        y = ecdf(x)
        plot = plt.plot(x, y)
        plotList.append(plot[0])

    xticksList = []
    for i in range(len(overallLatencyList)):
        tmp = 'CA_' + str(i + 1)
        xticksList.append(tmp)

    plt.legend(tuple(plotList), xticksList, fontsize = 10, loc = 7, fancybox = True)
    plt.xlabel('End to End Latency(ms)', fontsize = 10, fontweight = 'bold')
    plt.ylabel('CDF Plot', fontsize = 10, fontweight = 'bold')
    plt.show()

def plotTaskLatencyDistributionBar(parallelTasks, avgTaskLatencyList, avgNetworkLatencyList):

    width = 0.5

    for partitionCategory in range(len(avgTaskLatencyList)):

        p0 = plt.bar(partitionCategory, avgNetworkLatencyList[partitionCategory], width)
        bottomList = []
        bottomList.append(avgNetworkLatencyList[partitionCategory])
        plot = []
        for i in range(len(parallelTasks)):
            plot.append(plt.bar(partitionCategory, avgTaskLatencyList[partitionCategory][i], width, bottom = sum(bottomList)))
            bottomList.append(avgTaskLatencyList[partitionCategory][i])

    plt.legend((p0[0], plot[0][0], plot[1][0], plot[2][0], plot[3][0], plot[4][0], plot[5][0]),
               ('Network', parallelTasks[0], parallelTasks[1], parallelTasks[2], parallelTasks[3], parallelTasks[4], parallelTasks[5]),
               fontsize=10, loc=2,
               framealpha=0, fancybox=True)


    ind = range(0, len(avgTaskLatencyList))
    #plt.tight_layout(pad = 10)
    xticksList = []
    for i in range(len(avgTaskLatencyList)):
        tmp = 'CA_' + str(i+1)
        xticksList.append(tmp)
    plt.xticks(ind, xticksList, rotation = 0, fontsize = 10)
    plt.yticks(fontsize = 10)
    plt.xlabel('Computing Architecture', fontsize = 10, fontweight = 'bold')
    plt.ylabel('Task Latency(ms)', fontsize = 10, fontweight = 'bold')
    #plt.ylim(0, 270)
    plt.show()
    plt.savefig('parallel.pdf')



'''
    Main function
'''
def main():

    parallelTasks = ["VideoSpout", "rectificationBolt", "partitionBolt", "disparityBolt", "mergeBolt",
                     "reprojectionBolt"]
    features = ["cpuTime", "clockTime", 'outputSize']


    defaultAddr = '/root/stormLogs/10/'
    nonPipeAddr = defaultAddr + 'nonPipe/'
    serialAddr = defaultAddr + 'serial/'

    logAddrList = []
    logAddrList.append(nonPipeAddr)
    logAddrList.append(serialAddr)

    i = 2
    while i <= 2**6:
        parallelLogAddr = defaultAddr + 'parallel/' + str(i) + '/'
        logAddrList.append(parallelLogAddr)
        i = i * 2

    avgTaskLatencyList = []
    avgNetworkLatencyList = []
    overallLatencyList = []
    avgThroughputList = []
    taskTrafficList = []

    categoryList = [1, 1, 2, 4, 8, 16, 32, 64]
    i = 0
    for addr in logAddrList:

        taskProcess = TaskProcess(parallelTasks, features, categoryList[i])
        i+=1
        taskProcess.readLog(addr)
        #taskProcess.readFromCSV(addr)

        #process each task latency & network latency
        #avgTaskLatency, stdTaskLatency = taskProcess.calulateAverageTasksLatency()
        #avgNetworkLatency, stdNetworkLatency = taskProcess.calculateAverageNetworkLatency()
        #avgTaskLatencyList.append(avgTaskLatency)
        #avgNetworkLatencyList.append(avgNetworkLatency)
        #overallLatency = taskProcess.getOverallLatency()
        #overallLatencyList.append(overallLatency)

        #process throughput
        #taskTrafficList.append(taskProcess.MBpsa)

    '''
        Plot performance evaluation figures
    '''

    #plotTaskLatencyDistributionBar(parallelTasks, avgTaskLatencyList, avgNetworkLatencyList)

    #plotOverallLatencyCDF(overallLatencyList)

    '''
        find the real task traffic. Parallel version introduces additional traffic.
        calculate the network throughput: each setting traffic per task * each setting finished task number per second
    '''

    #plotNetworkTraffic(taskTrafficList)

plt.style.use('ggplot')
main()
