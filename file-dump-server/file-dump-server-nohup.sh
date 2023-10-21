#!/bin/bash

# If lowering watch out for out of memory and agressive GC might cause slow downs
# Use "psaux | grep file-dump-server" to get the pid to kill the process when needing to stop
nohup java -jar -Xms128M -Xmx768M file-dump-server.jar &

