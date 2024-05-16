### kill.sh
The kill script is a script designed to terminate instances of a Java program called "Node" and release the port 1099 if it's in use (bc you were using Java RMI).

`node_pids=$(pgrep -f "java Node")` searches for processes with the name "java Node" and stores their PIDs.


### run.sh
Start a network of Java processes (Chord ring).