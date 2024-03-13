call mvn clean
call mvn compile 
call mvn exec:java -Dexec.mainClass="bgu.spl.net.impl.tftp.TftpClient" -Dexec.args="127.0.0.1 7777"
