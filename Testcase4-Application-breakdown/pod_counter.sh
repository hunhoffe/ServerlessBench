#!/bin/bash

while true
do
  num_total=$(kubectl get pods -n openwhisk -o wide | grep extractimage | wc -l)
  num_running=$(kubectl get pods -n openwhisk -o wide | grep extractimage | grep " Running " | wc -l)
  echo $num_total, $num_running
  sleep .5
done
