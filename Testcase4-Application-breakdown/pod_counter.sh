#!/bin/bash

while true
do
  num_total=$(kubectl get pods -n openwhisk -o wide | grep extractimage | wc -l)
  num_running=$(kubectl get pods -n openwhisk -o wide | grep extractimage | grep " Running " | wc -l)
  current_time=$(date +%s%N)
  echo $current_time, $num_total, $num_running
  sleep .25
done
