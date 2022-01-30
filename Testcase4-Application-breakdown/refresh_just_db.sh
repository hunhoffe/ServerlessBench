#!/bin/bash

# Refresh the experiment db
cd /local/repository/serverlessbench
./refresh_db.sh
sleep 10

# Build & deploy serverless function to OpenWhisk, add data to database
cd ~/ServerlessBench/Testcase4-Application-breakdown
./deploy.sh --image-process
