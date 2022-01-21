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
PREFIX="ok: invoked /_/imageProcessSequence with id "
if ! output=$(wsk action invoke imageProcessSequence -i --param imageName test.jpg 2>&1); then
    code=$?
    echo "Failed to invoke function: $output"
    exit -1
fi

if ! activation=${output#"$PREFIX"}; then
    code=$?
    echo "Failed to parse activation id from: $output"
    exit -1
fi
echo $activation
