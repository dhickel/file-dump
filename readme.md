## Building


### Linux
If not already installed apt-get install either:
- openjdk-17-jdk
- openjdk-17-jdk-headless
- openjdk-17-jre

Or any other flavor of jdk >= 17

You will also need maven so apt-get install maven as well

Repository contains both a client and a server build that will need build separately.

In both file-dump-client and file-dump-server from the terminal run

```
maven clean package 
```

You will get 2 jars, the jar-with-dependencies is the one you want.

Rename and put in a folder with the config, and launch script (if you prefer)

<br>

### Windows

- Get an openJDK build from:https://jdk.java.net/
- Add java as a path variable https://www.java.com/en/download/help/path.html
- Install maven (guide) https://mkyong.com/maven/how-to-install-maven-in-windows/


<br>

## Running

You will want to limit the heap size, or else it will grow quite large. This can be done with the -Xms -Xmx flags, but care should be taken to not set it too lower, or you can get Out Of Memory errors, or bad performance.

The included .sh and .bat scripts in the their directories are good for most use cases.


### For the client 
```
java -Xms32M -Xmx128M -jar file-dump-client.jar
```

You may be able to get away with lower, but it normally set around 80-100MB with these settings and 2-3 transfers

### For the server
```
java -Xms128M -Xmx512M -jar file-dump-client.jar
```

The server tends to be around 500-600MB in use, you can edit the config and runtime settings to get it lower, but it is more likely to face OOM issues.

If you wish to limit its memory either disable using a separate thread for writes, or decrease the queue size and or block buffer size.

When not using a separate thread or with a small queue and block buffer size you can get down to 100-250, dropping the queue to 4 should let you run under 200MB with

```
java -Xms64M -Xmx256M -jar file-dump-client.jar
```

When not using a separate thread you will run around 100


## Configuration

See the include config.yaml files for more details about what the different settings are, included defaults should provide near max performance, though at a higher memory cost.






