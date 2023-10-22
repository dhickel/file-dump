## Info
A small program for transferring, and replacing files in bulk. Optimized for high throughput. Primarily intended for chia re-plotting. The program differs from other programs methods in a few different ways and was written for specific constrains I faced.

* Multiple instances can be ran on different interfaces. This allows for the systems with multiple nics to take advantage of of the extra bandwidth
* Allow for hot config reloading, as the program is running you can alter your config adding/removing directories without the need to restart and stop existing transfers
* Can delete files for space with the ability to delete by size, and/or for being in a specific directory.
* Define the max number of transfers at once per server, in the client config.
* Automatically rotated directories when writing.
* Outputs transfer statistics to a file transfer_stats.csv


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
mvn clean package
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
Rename your jar-with-dependencies from the build to file-dump<server/client>.jar, be sure the chmod +x the scripts if using them.

Also make sure your config.yaml is in the same directory as your jar when launching.


The program include to run scripts which can be customized. You can also launch with

```java -jar file-dump-server.jar ```

```java -jar file-dump-client.jar```

You run the client on the pc you are transferring ***from***

You run the server on the pc you are transferring ***to***

If wishing to run the server across multiple nic you will run an instance of the server for each address, binding it in the server config. The client can communicate to multiple server via the same instance, so you only need to run one instance of it. You can also limit in the client config the max amount of transfer to initiate to each server. These come in the form of a list so iif you have 2 servers in the limit list define the limit for that server on the same index of the server list (example below).

### Linux
Two script are provide, the nohup up one detaches it from the terminal so it can run in the background. If not familiar with this, this means you will need to manually stop it when you are ready.
this can be done with:
```bash
ps aux | grep file-dump-server
```
the take the pid (first number on the right) and do:
```bash
kill <pid>
```

You can tail -f the nohup.out file in the directory to monitor.



### Windows
***Program has not been tested on windows, its java so it should run fine, but the script to launch is really basic***

***You will also need to install the jvm manually on windows***




<br>

## Hot config reloading
The program checks the hash of the config file once every minute to detect changes, this can be used to add and remove files while not having to stop transfers to allow for easier management of rotating in new drives as needed.

<br>

## Deletion Feature
A list of deletion directories can be added, when there is no space available for a transfer the program will delete from these directories, you can also set a file size limit so that only files over that size will be deleted allowing for easy replacement with newer compressed plot files.


<br>


## Server config

**If running across multiple NICs be sure to bind each config instance to a specific address for that interface**

```yaml
#####################
### Server Config ###
#####################


bindAddress: "192.168.1.177" # Can set to 0.0.0.0 if running only one instance

# Port to use
port: 9988


# Best left on, some memory can be save by disabling
# Disabling will block from receiving data every time it writes the blockBuffer
separateThreadForWriting: true

# Max queue size when using separateThreadForWrite
# will increase memory usage both of application and os network buffer
# 16 is an ideal size, your can try 32 if your want
queueSize: 16

# Indifferent to trailing slash, you may need to escape backslashes on windows
outputDirectories:
- "/mnt/19/"
- "/mnt/20/"


# Will only initiate one transfer to a directory at at time regardless of free space
limitOneTransferPerDirectory: true


# -1 to let jvm control, best left untouched, too small will slow transfers
socketBufferSize: 32768

# Amount of block of data from client to buffer before writing or send to queue
blockBufferSize: 1048576


# Size of buffer for disk writes when using separateThreadForWrite, may see improvements editing
# Should be in a power of 2 of your hard disk blocksize, and small enough to fit into its cache.
# Increasing it can lead to less seeks, but your hdd is likely already caching multiple buffers worth before writing
# -1 lets jvm control
writeBufferSize: 1048576

# Delete files from the specified deletion directory if they meet the type and size requirements
deleteForSpace: true

# Do not include the . only the extension name ex. "plot" not ".plot"
deletedFileTypes:
- "plot"

# Directories who files are subject to deletion if they meet the filetype and size requirements
deletionDirectories:
- "/mnt/19/old/"
- "/mnt/20/old/"


# Threshold in MiB for files to delete, any files larger than this will be deleted
deletionThreshHold: 70_000 # In MiB
overWriteExisting: true # Will overwrite existing file

```




## Client Config

***Example for sending to multiple servers as either instances on a host or separate. If only sending to a single instance just remove the multiple entries, the default config is setup for only one host, this provides an example for two***

```YAML
#####################
### Client Config ###
#####################


separateThreadForReading: true

# Address and port of server receiving the files
serverAddresses:
- "192.168.10.2"
- "192.168.1.177" # Remove this line if only sending to one server

serverPorts:
- 9988
- 9999 # Remove this line if only sending to one server


# How many transfer to have open at once
maxTransfers:
- 2
- 1 # Remove this line if only sending to one server

# How often to check for new files in seconds
fileCheckInterval: 10

# Size out send buffer, best to leave this alone unless you know what your a doing
socketBufferSize: 32768

# Use a separate thread for reading, can increase transfer speed some, best left on
separateThreadForReading: true

# Length of block queue when using separateThreadForReading, 8 is a good amount
readQueueSize: 8

# Directories search at fileCheckInterval, indifferent to tailing slash, may need to escape backslash on windows
monitoredDirectories:
- "/mnt/final"
- "/mnt/buffer/"

# File types that are monitored for to send
monitoredFileTypes:
- "plot"

# Amount to stream off disk as one time, per transfer, best left alone
chunkSize: 4194304

# Amount of data to send to out at once, per transfer
# Ideally should be be a power of 2 and a divisor of the server's blockBufferSize, best left alone
blockSize: 32768

# Delete local files after successful transfers
# Server will only relay a successful transfer once last write is completed
deleteAfterTransfer: true
```


## Notes

Most of the defaults can be left as they are, increase them at best will most of the time just increase memory usage, at worst they will just make things slower. Being based in java memory consumption is going to be in the 128-512mb range for most use cases. Lowering the read/write queues can help limit this by reducing garbage, though the jvm seems to settle around the same after it picks up the usage pattern.



