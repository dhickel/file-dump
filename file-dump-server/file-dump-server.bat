:: Xmx could get by with Xmx256
:: just watch out for out of memory and agressive GC might cause slow downs
ava -jar -Xms64M -Xmx512M  file-dump-server.jar

