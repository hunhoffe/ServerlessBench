#!/bin/bash
#
# Copyright (c) 2020 Institution of Parallel and Distributed System, Shanghai Jiao Tong University
# ServerlessBench is licensed under the Mulan PSL v1.
# You can use this software according to the terms and conditions of the Mulan PSL v1.
# You may obtain a copy of Mulan PSL v1 at:
#     http://license.coscl.org.cn/MulanPSL
# THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR
# PURPOSE.
# See the Mulan PSL v1 for more details.
#

SCRIPTS_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd $SCRIPTS_DIR/../

ACTIONNAME=imageProcessSequence
PRINTLOG=false

while getopts "r:t:l" OPT; do
    case $OPT in
    # result file
    r)
        RESULT=$OPTARG
        ;;

    # The loop time
    t)
        TIMES=$OPTARG
        expr $TIMES + 0 &>/dev/null
        if [[ $? != 0 ]] || [[ $TIMES -lt 1 ]]; then
            echo "Error: loop times must be a positive integer"
            exit
        fi
        ;;

    # Output the results to the log with this argument.
    l)
        PRINTLOG=true
        ;;

    ?)
        echo "unknown arguments"
    esac
done

LOGFILE=$ACTIONNAME.csv

if [[ $PRINTLOG = true && ! -e $LOGFILE ]]; then
    echo logfile:$LOGFILE
    echo "invokeTime,endTime" > $LOGFILE
fi

LATENCYSUM=0

for i in $(seq 1 $TIMES)
do
    oldIFS="$IFS" 
    invokeTime=`date +%s%3N`
    IFS=$'\n' output=( $($SCRIPTS_DIR/action_invoke.sh) )
    endTime=`date +%s%3N`
    IFS="$oldIFS"

    # check invoke result
    python3 $SCRIPTS_DIR/checkInvoke.py "${output[@]}"
    if [[ $? -eq 0 ]]; then
        echo "invokeTime: $invokeTime, endTime: $endTime" 

        latency=`expr $endTime - $invokeTime`
        LATENCYSUM=`expr $latency + $LATENCYSUM`
        # The array starts from array[1], not array[0]!
        LATENCIES[$i]=$latency

        if [[ $PRINTLOG = true ]];then
            echo "$invokeTime,$endTime" >> $LOGFILE
        fi
    fi
done

# Sort the latencies
for((i=0; i<$TIMES+1; i++)){
  for((j=i+1; j<$TIMES+1; j++)){
    if [[ ${LATENCIES[i]} -gt ${LATENCIES[j]} ]]
    then
      temp=${LATENCIES[i]}
      LATENCIES[i]=${LATENCIES[j]}
      LATENCIES[j]=$temp
    fi
  }
}

echo "------------------ result ---------------------"
_50platency=${LATENCIES[`echo "$TIMES * 0.5"| bc | awk '{print int($0)}'`]}
_75platency=${LATENCIES[`echo "$TIMES * 0.75"| bc | awk '{print int($0)}'`]}
_90platency=${LATENCIES[`echo "$TIMES * 0.90"| bc | awk '{print int($0)}'`]}
_95platency=${LATENCIES[`echo "$TIMES * 0.95"| bc | awk '{print int($0)}'`]}
_99platency=${LATENCIES[`echo "$TIMES * 0.99"| bc | awk '{print int($0)}'`]}

echo "Successful invocations: ${#LATENCIES[@]} / $TIMES"
echo "Latency (ms):"
echo -e "Avg\t50%\t75%\t90%\t95%\t99%\t"
if [ ${#LATENCIES[@]} -gt 0 ];then
    echo -e "`expr $LATENCYSUM / ${#LATENCIES[@]}`\t$_50platency\t$_75platency\t$_90platency\t$_95platency\t$_99platency\t"
fi

# output to result file
if [ ! -z $RESULT ]; then
    echo -e "\n\n------------------ (single)result ---------------------" >> $RESULT
    echo "mode: $MODE, loop_times: $TIMES" >> $RESULT
    echo "Successful invocations: ${#LATENCIES[@]} / $TIMES" >> $RESULT
    echo "Latency (ms):" >> $RESULT
    echo -e "Avg\t50%\t75%\t90%\t95%\t99%\t" >> $RESULT
    if [ ${#LATENCIES[@]} -gt 0 ];then
        echo -e "`expr $LATENCYSUM / ${#LATENCIES[@]}`\t$_50platency\t$_75platency\t$_90platency\t$_95platency\t$_99platency\t" >> $RESULT
    fi
fi
