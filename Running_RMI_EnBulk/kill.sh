#!/bin/bash
# !!!! Be sure to first run chmod +x kill.sh !!!!
# Usage: ./kill.sh

node_pids=$(pgrep -f "java Node")

if [ -n "$node_pids" ]; then
    echo "Found at least one Node instance that must be terminated..."
    kill $node_pids
    echo "Finished terminating the Node instance(s)..."
else
    echo "Did not find any Node instances to terminate..."
fi 

fuser -k 1099/tcp
echo "Exiting..."