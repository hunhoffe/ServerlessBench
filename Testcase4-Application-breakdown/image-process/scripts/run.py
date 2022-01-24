
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
import subprocess
from subprocess import PIPE
import argparse
import json
import sys

# this script should be executed in parent dir of scripts
try:
    IMAGE_PROCESS_HOME=os.environ['TESTCASE4_HOME'] + "/image-process"
except Exception as e:
    print("Error: TESTCASE4_HOME environment variable not set. Exiting...")
    exit(-1)


def client(client_num, i, single_results, single_logs, single_errors):
    # run invoke command
    command = f"{IMAGE_PROCESS_HOME}/scripts/action_invoke.sh"
    result = subprocess.run(command, stdout=PIPE, stderr=PIPE)
    if result.returncode != 0:
        print(f"Client {client_num} iteration {i} failed to invoke function with returncode {result.returncode}\n")
        single_errors[i] = result.stderr.decode("utf-8").strip() + result.stderr.decode("utf-8").strip()
        return
    activation_id = result.stdout.decode("utf-8").strip()
    
    # activation result
    command = f"{IMAGE_PROCESS_HOME}/scripts/get_activation.sh"
    result = subprocess.run([command, activation_id], stdout=PIPE, stderr=PIPE)
    if result.returncode != 0:
        print(f"Client {client_num} iteration {i} failed to fetch activation record for {activation_id} with returncode {result.returncode}\n")
        single_errors[i] = result.stderr.decode("utf-8").strip() + result.stderr.decode("utf-8").strip()
        return
    result = result.stdout.decode("utf-8").strip()

    # Parse and record results
    parsed_result = parse_result(result)
    if parsed_result:
        single_results[i] = parsed_result
        if len(parsed_result) == 7:
            single_logs[i] = result
        else:
            print(f"Client {client_num} had error for invocation {i} (activation_id={activation_id})")
            single_errors[i] = result


def looping_client(client_num, results, logs, errors, num_iters, delay):
    print(f"client {client_num} start")
    threads = []
    single_results = []
    single_logs = []
    single_errors = []

    for invoke_num in range(num_iters):
        t = threading.Thread(target=client, args=(client_num, invoke_num, single_results, single_logs, single_errors))
        threads.append(t)
        single_results.append([])
        single_logs.append("")
        single_errors.append("")

    for invoke_num in range(num_iters):
        print(f"client {client_num} started {invoke_num}")
        threads[invoke_num].start()
        time.sleep(delay)

    for invoke_num in range(num_iters):
        threads[invoke_num].join()

    results[client_num] = single_results
    logs[client_num] = single_logs
    errors[client_num] = single_errors
    print(f"client {client_num} finished")


def main():
    args = parse_args()

    print(f"About to run {args.num_clients} clients with {args.num_iters} iterations each and {args.delay} delay")

    # Second: invoke the actions
    # Initialize the results and the clients
    threads = []
    results = []
    logs = []
    errors = []

    for i in range(args.num_clients):
        results.append([])
        logs.append([])
        errors.append([])

    # Create the clients
    for i in range(args.num_clients):
        t = threading.Thread(target=looping_client,args=(i, results, logs, errors, args.num_iters, args.delay))
        threads.append(t)

    # start the clients
    for i in range(args.num_clients):
        threads[i].start()

    # wait for the clients to complete
    for i in range(args.num_clients):
        threads[i].join()

    # write log to logfile
    with open(args.logfile, 'w', encoding='utf-8') as f:
        for l in logs:
            for log in l:
                if len(log) > 0:
                    f.write(log)
                    f.write('\n\n')

    # write to error file
    with open(args.errfile, 'w', encoding='utf-8') as f:
        for e in errors:
            for err in e:
                if len(err) > 0:
                    f.write(err)
                    f.write('\n\n')

    # write to results file
    with open(args.resfile, 'w', encoding='utf-8') as f:
        f.write("start, end, dbtime1, dbtime2, dbtime3, dbtime4, dbtime5\n")
        for rl in results:
            for r in rl:
                if len(r) > 0:
                    string_r = [str(i) for i in r]
                    f.write(', '.join(string_r))
                    f.write('\n')

    write_summary(args.sumfile, results, args.num_clients, args.num_iters, args.delay)

def write_summary(sumfile, results, num_clients, num_iters, delay):
    START=0
    END=1
    DB_TIME_START=2

    latencies = []
    latencies_no_db = []
    err_count = 0.0
    min_start = 0x7fffffffffffffff
    max_end = 0

    for rs in results:
        for r in rs: 
            # count errors
            if len(r) < 7:
                err_count += 1
            if len(r) >= 2:
                min_start = min(r[START], min_start)
                max_end = max(r[END], max_end)

                # Calculate latency for each result
                latency = r[END] - r[START]
                latencies.append(latency)

                # Calculate latency minus db but only for successful invocations
                if len(r) == 7:
                    latency_no_db = latency - sum(r[DB_TIME_START:])
                    latencies_no_db.append(latency_no_db)

    # Calculate number of requests and duration
    num_requests = float(num_clients * num_iters)
    num_successful_requests = float(len(latencies_no_db))
    duration = float(max_end - min_start)
    # theoretical_duration = float(num_iters * delay)

    # sort the latencies
    latencies.sort()
    latencies_no_db.sort()
    with open(sumfile, 'w') as fh:
        format_summary(fh, "Including DB Time", num_requests, latencies, num_successful_requests, duration, sumfile)
        format_summary(fh, "Excluding DB Time", num_requests, latencies_no_db, num_successful_requests, duration, sumfile)
    print(f"Lost {num_requests - len(latencies)} requests....\n")
 

def format_summary(fh, title, num_requests, latencies, num_successful_requests, duration, sumfile):

    # calculate the average latency
    total = 0
    for latency in latencies:
        total += latency

    num_results = len(latencies)
    fh.write("\n")
    fh.write(f"------------------ {title} ---------------------\n")
    fh.write(f"{num_successful_requests} / {num_requests} requests, duration is {duration}\n")
    fh.write("latency (ms):\navg\t50%\t75%\t90%\t95%\t99%\n")
    if num_requests > 0:
        average_latency = float(total) / num_results
        _50_pc_latency = latencies[int(num_results * 0.5) - 1]
        _75_pc_latency = latencies[int(num_results * 0.75) - 1]
        _90_pc_latency = latencies[int(num_results * 0.9) - 1]
        _95_pc_latency = latencies[int(num_results * 0.95) - 1]
        _99_pc_latency = latencies[int(num_results * 0.99) - 1]
        fh.write("%.2f\t%d\t%d\t%d\t%d\t%d\n" % (average_latency, _50_pc_latency, _75_pc_latency, _90_pc_latency, _95_pc_latency, _99_pc_latency))
    fh.write("throughput (n/s):\n%.2f\n" % (num_requests / (duration / 1000)))
    fh.write("goodput (n/s):\n%.2f\n" % (num_successful_requests / (duration / 1000)))

def parse_result(result):
    json_start = result.find('{')
    json_str = result[json_start:]

    try:
        json_result = json.loads(json_str)

        # Grab values for return
        start = json_result['start']
        end = json_result['end']

        if json_result['statusCode'] != 0:
            return (start, end)
        else:
            response = json_result['response']
            result = response['result']
            comm_times = json_result['response']['result']['commTimes']
            sys.stdout.flush()

            # Check results
            assert(len(comm_times) == 5)

            # return parsed values
            return [start, end] + comm_times

    except:
        print(f"Could not parse results json: {json_str}")

    return None


def parse_args():
    # Create arg parser
    parser = argparse.ArgumentParser(description='Run image-process sequences.')
    parser.add_argument(
            'num_clients', 
            type=int, 
            help='Number of concurrent clients'
    )
    parser.add_argument(
            'num_iters',
            type=int,
            help='Number of invocations per client'
    )
    parser.add_argument(
            'delay',
            type=float,
            help='Delay between invocations on each client. Measured in seconds.'
    )
    parser.add_argument(
            '-s',
            '--sumfile',
            default='eval-result.log',
            help='Path to summary output file'
    )
    parser.add_argument(
            '-e',
            '--errfile',
            default='error.dat',
            help='Path to error results output file'
    )
    parser.add_argument(
            '-r',
            '--resfile',
            default="results.csv",
            help='Path to results output file'
    )
    parser.add_argument(
            '-l',
            '--logfile',
            default='log.dat',
            help='Path to log output file'
    ) 
    args = parser.parse_args()

    # Check bounds
    if args.num_clients < 1:
        print("Error: num_clients must be >= 1")
        parser.print_help()
        exit(-1)

    if args.num_iters < 1:
        print("Error: num_iters must be >= 1")
        parser.print_help()
        exit(-1)
        
    if args.delay < 0.0:
        print("Error: delay must be >= 0")
        parser.print_help()
        exit(-1)
    return args

if __name__ == "__main__":
    main()
