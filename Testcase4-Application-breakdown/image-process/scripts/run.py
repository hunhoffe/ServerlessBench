
# Copyright (c) 2020 Institution of Parallel and Distributed System, Shanghai Jiao Tong University
# ServerlessBench is licensed under the Mulan PSL v1.
# You can use this software according to the terms and conditions of the Mulan PSL v1.
# You may obtain a copy of Mulan PSL v1 at:
#     http://license.coscl.org.cn/MulanPSL
# THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR
# PURPOSE.
# See the Mulan PSL v1 for more details.

import os
import threading
import time
import sys, getopt

# this script should be executed in parent dir of scripts

def client(i,single_results):
    IMAGE_PROCESS_HOME=os.environ['TESTCASE4_HOME'] + "/image-process"
    command = "%s/scripts/run-single.sh -t 1" % (IMAGE_PROCESS_HOME)
    r = os.popen(command)  
    text = r.read()
    single_results[i] = text
 
def looping_client(i,results,loopTimes,delaySec):
    print("client %d start" %i)
    threads = []
    single_results = []

    for j in range(loopTimes):
        t = threading.Thread(target=client, args=(j,single_results))
        threads.append(t)
        single_results.append("")

    for j in range(loopTimes):
        print("client %d started %d" % (i,j))
        threads[j].start()
        time.sleep(delaySec)

    for j in range(loopTimes):
        threads[j].join()

    results[i] = "\n".join(single_results)
    print("client %d finished" %i)

def main():
    argv = getargv()
    clientNum = argv[0]
    loopTimes = argv[1]
    delaySec = argv[2]

    print("About to run %d clients, %d loops, %f delay" % (clientNum, loopTimes, delaySec))

    # Second: invoke the actions
    # Initialize the results and the clients
    threads = []
    results = []

    for i in range(clientNum):
        results.append('')

    # Create the clients
    for i in range(clientNum):
        t = threading.Thread(target=looping_client,args=(i,results,loopTimes,delaySec))
        threads.append(t)

    # start the clients
    for i in range(clientNum):
        threads[i].start()

    # wait for the clients to complete
    for i in range(clientNum):
        threads[i].join()


    outfile = open("result.csv","w")
    outfile.write("invokeTime,endTime\n")
   
    latencies = []
    minInvokeTime = 0x7fffffffffffffff
    maxEndTime = 0
    for i in range(clientNum):
        # get and parse the result of a client
        clientResult = parseResult(results[i])
        # print the result of every loop of the client
        for j in range(len(clientResult)):
            outfile.write(clientResult[j][0] + ',' + clientResult[j][1] + '\n') 
            # Collect the latency
            latency = int(clientResult[j][-1]) - int(clientResult[j][0])
            latencies.append(latency)

            # Find the first invoked action and the last return one.
            if int(clientResult[j][0]) < minInvokeTime:
                minInvokeTime = int(clientResult[j][0])
            if int(clientResult[j][-1]) > maxEndTime:
                maxEndTime = int(clientResult[j][-1])

    formatResult(latencies,maxEndTime - minInvokeTime, clientNum, loopTimes)

def parseResult(result):
    lines = result.split('\n')
    parsedResults = []
    for line in lines:
        if line.find("invokeTime") == -1:
            continue
        parsedTimes = ['','']

        i = 0
        count = 0
        while count < 2:
            while i < len(line):
                if line[i].isdigit():
                    parsedTimes[count] = line[i:i+13]
                    i += 13
                    count += 1
                    continue
                i += 1 
        parsedResults.append(parsedTimes)
    return parsedResults

def getargv():
    if len(sys.argv) != 4:
        print("Usage: python3 run.py <client number> <loop times> <delay sec>")
        exit(0)
    if not str.isdigit(sys.argv[1]) or not str.isdigit(sys.argv[2]) or not sys.argv[3].replace('.','',1).isdigit() or int(sys.argv[1]) < 1 or int(sys.argv[2]) < 1 or float(sys.argv[3]) < 0.0:
        print("Usage: python3 run.py <client number> <loop times> <delay sec>")
        print("Client number, loop times, and delay seconds must be an positive integer")
        exit(0)
    return (int(sys.argv[1]),int(sys.argv[2]),float(sys.argv[3]))


def formatResult(latencies,duration,client,loop):
    requestNum = len(latencies)
    
    # sort the latencies
    latencies.sort()

    # Duration is total time for all latencies - but includes time/overhead between function calls
    duration = float(duration)

    # calculate the average latency
    total = 0
    for latency in latencies:
        total += latency
    print("\n")
    print("------------------ result ---------------------")
    print("%s / %d requests finished in %.2f seconds" %(requestNum, (loop * client), (duration/1000)))
    print("latency (ms):\navg\t50%\t75%\t90%\t95%\t99%")
    if requestNum > 0:
        averageLatency = float(total) / requestNum
        _50pcLatency = latencies[int(requestNum * 0.5) - 1]
        _75pcLatency = latencies[int(requestNum * 0.75) - 1]
        _90pcLatency = latencies[int(requestNum * 0.9) - 1]
        _95pcLatency = latencies[int(requestNum * 0.95) - 1]
        _99pcLatency = latencies[int(requestNum * 0.99) - 1]
        print("%.2f\t%d\t%d\t%d\t%d\t%d" %(averageLatency,_50pcLatency,_75pcLatency,_90pcLatency,_95pcLatency,_99pcLatency))
    print("throughput (n/s):\n%.2f" %(requestNum / (duration/1000)))
    # output result to file
    resultfile = open("eval-result.log","a")
    resultfile.write("\n\n------------------ (concurrent)result ---------------------\n") 
    resultfile.write("client: %d, loop_times: %d\n" % (client, loop))
    resultfile.write("%s / %d requests finished in %.2f seconds\n" %(requestNum, (loop * client), (duration/1000)))
    resultfile.write("latency (ms):\navg\t50%\t75%\t90%\t95%\t99%\n")
    if requestNum > 0:
        resultfile.write("%.2f\t%d\t%d\t%d\t%d\t%d\n" %(averageLatency,_50pcLatency,_75pcLatency,_90pcLatency,_95pcLatency,_99pcLatency))
    resultfile.write("throughput (n/s):\n%.2f\n" %(requestNum / (duration/1000)))

main()
