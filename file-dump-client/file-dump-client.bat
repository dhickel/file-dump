:: Might need to increase Xmx to Xmx256M
:: If using a large queue and separate write thread

start /B /WAIT java -Xms32M -Xmx128M -jar file-dump-client.jar
exit /B 0
