#!/bin/bash

# just watch out for out of memory and agressive GC might cause slow downs

java -jar -Xms64M -Xmx512M file-dump-server.jar

