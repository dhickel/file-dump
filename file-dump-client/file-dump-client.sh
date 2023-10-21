#!/bin/bash

# If using a large queue size, or transfering more than 3-4 files you will need to increase the xmx
# increase if you get OOM errors
java -jar -Xms64M -Xmx512M file-dump-client.jar

