#!/bin/bash
# !!!! Be sure to first run chmod +x run.sh !!!!
# Usage: ./run.sh OR ./run.sh -d 

cd ../
DEBUG_FLAG=""
if [ "$1" == "-d" ]; then
    DEBUG_FLAG="-d"
fi 

echo "Starting the RMI registry on port 1099..."
rmiregistry &
sleep 1s
echo

# Loop 8 times and run the Node.java program with a different node ID.
echo "Starting the nodes in the Chord ring as background processes..."
echo
for i in {0..7}
do
    # https://stackoverflow.com/questions/3004811/how-do-you-run-multiple-programs-in-parallel-from-a-bash-script
    (java Node $i $DEBUG_FLAG) &

    sleep 1s
done

echo
echo "Loading the contents of the dictionary file into the Chord ring..."
java DictionaryLoader 0 dict.txt
echo "Exiting..."