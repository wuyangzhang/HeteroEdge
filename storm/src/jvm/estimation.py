import sys
import numpy as np
import glob

filedic = sys.argv[1]

for filename in glob.glob(filedic + "/*"):
    file = open(filename, 'r')
    latency = []
    for line in file.readlines():
        if 'disparityBolt' in line:
            re = line.split(',')
            time = float(re[6])
            latency.append(time)

    num = np.array(latency)
    print('file {} avg is {}\n'.format(filename.split('/')[-1], np.mean(num)))
