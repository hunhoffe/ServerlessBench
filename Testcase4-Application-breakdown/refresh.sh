#!/bin/bash

cd /local/repository/serverlessbench
./refresh_db.sh
sleep 10
cd ~/ServerlessBench/Testcase4-Application-breakdown
./deploy.sh --image-process
