#!/bin/bash

# If using a large queue size, or transfering more than 3-4 files you will need to increase the xmx
# increase if you get OOM errors
# Use "psaux | grep file-dump-server" to get the pid to kill the process when needing to stop
nohup java -jar -Xms64M -Xmx512M file-dump-client.jar  &

