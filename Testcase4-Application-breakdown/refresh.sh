#!/bin/bash

cd /home/ec/openwhisk-deploy-kube

# uninstall openwhisk & delete namespace
helm uninstall owdev -n openwhisk
kubectl delete namespace openwhisk

# reinstall openwhisk
helm install owdev ./helm/openwhisk --create-namespace -n openwhisk -f mycluster.yaml

# Refresh the experiment db
cd /local/repository/serverlessbench
./refresh_db.sh
sleep 10

# Wait for openwhisk to finish installing
kubectl get pods -n openwhisk
printf "%s: %s\n" "$(date +"%T.%N")" "Waiting for OpenWhisk to complete deploying (this can take several minutes): "
DEPLOY_COMPLETE=$(kubectl get pods -n openwhisk | grep owdev-install-packages | grep Completed | wc -l)
while [ "$DEPLOY_COMPLETE" -ne 1 ]
do
    sleep 2
    DEPLOY_COMPLETE=$(kubectl get pods -n openwhisk | grep owdev-install-packages | grep Completed | wc -l)
done
printf "%s: %s\n" "$(date +"%T.%N")" "OpenWhisk deployed!"
kubeclt get pods -n openwhisk
wsk property set --apihost 192.168.6.1:31001
wsk property set --auth 23bc46b1-71f6-4ed5-8c54-816aa4f8c502:123zO3xZCLrMN6v2BKK1dXYFpXlPkccOFqm12CdAsMgRU4VrNZ9lyGVCGuMDGIwP

# Build & deploy serverless function to OpenWhisk, add data to database
cd ~/ServerlessBench/Testcase4-Application-breakdown
./deploy.sh --image-process
