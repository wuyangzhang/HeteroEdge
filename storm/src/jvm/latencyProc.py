import sys
import numpy as np
filename = sys.argv[1]

file = open(filename, 'r')
l = []
dis = []
subdis = []
node = {}
print('open file: ' + filename)
mindis = 1e14
for line in file.readlines():
    if 'overall' in line:
        latency = float(line.split(',')[5])
        if latency > 1000:
            continue
        l.append(latency)
    if 'disparityBoltSub' in line:
        start = float(line.split(',')[3])
        end = float(line.split(',')[4])
        if start < mindis:
            mindis = start
        subdis.append(end)
        disparityTime = float(line.split(',')[5])
        server = line.split(',')[6]
        if server not in node:
            re = []
            re.append(disparityTime)
            node[server] = re
        else:
            node[server].append(disparityTime)
    if 'disparityBolt,' in line:
        for x in range(len(subdis)):
            subdis[x] = subdis[x] - mindis
            #print(subdis[x], mindis)
            dis.append(subdis[x])
        mindis = 1e14
        del subdis[:]
                      
num = np.array(l)
disparity = np.array(dis)
print('average overall latency is {}, std is {}'.format(np.mean(num, axis=0), np.std(num, axis=0)))
print('average disparity latency is {}, std is {}'.format(np.mean(disparity, axis=0), np.std(disparity, axis=0)))

for server in node:
    avg = np.mean(node[server])
    print('server {} avg disparity {}'.format(server, avg))

