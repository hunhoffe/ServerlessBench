#!/bin/bash

NOT_DONE_PREFIX="error: Unable to get activation*"
ACTIVATION_FOUND_PREFIX="ok: got activation*"
activation_id=$1

while true; do
    activation=$(wsk -i activation get $activation_id 2>&1)
    if [[ $activation == $NOT_DONE_PREFIX* ]]
    then
        sleep 5
    else
        if [[ $activation == $ACTIVATION_FOUND_PREFIX* ]]
	then
	    echo $activation
	    exit 0
	else
	    echo "Unknown output: $activation"
	    exit -1
	fi
    fi
done
