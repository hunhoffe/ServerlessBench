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

if [ -z "$TESTCASE4_HOME" ]; then
    echo "$0: ERROR: TESTCASE4_HOME environment variable not set"
    exit
fi

source $TESTCASE4_HOME/eval-config

SCRIPTS_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd $SCRIPTS_DIR/../

result=$(pwd)/eval-result.log
rm -f $result 
echo "Attempted to remove old result at $result"

echo "3. measuring concurrent invoking..."
if [ -z ${test_name+x} ]; then
    echo "$ python3 $SCRIPTS_DIR/run.py $concurrent_client $concurrent_loop $delay_sec"
    python3 $SCRIPTS_DIR/run.py $concurrent_client $concurrent_loop $delay_sec
else
    echo "$ python3 $SCRIPTS_DIR/run.py $concurrent_client $concurrent_loop $delay_sec -r ${test_name}_results.csv -e ${test_name}_errors.dat -l ${test_name}_log.dat"
    python3 $SCRIPTS_DIR/run.py $concurrent_client $concurrent_loop $delay_sec -r ${test_name}_results.csv -e ${test_name}_errors.dat -l ${test_name}_log.dat
fi
echo "image-process application running result: "
cat $result
