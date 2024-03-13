package bgu.spl.net.impl.tftp;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;

import bgu.spl.net.api.MessageEncoderDecoder;

public class listening implements Runnable{

    private final short DATA   = 0x03;
    private final short ACK    = 0x04;
    private final short ERROR  = 0x05;
    private final short BCAST  = 0x09;
    private byte[] data ;
    private boolean first = true;
    private int reader = 0;
    private int maxDataPack = 512;
    private BufferedOutputStream out;
    private BufferedInputStream in;
    private MessageEncoderDecoder<byte[]> encdec;
    private TftpClient main;


    public listening(Socket sock, MessageEncoderDecoder<byte[]> Resencdec, TftpClient keyboard )throws IOException{
        out = new BufferedOutputStream(sock.getOutputStream());
        in = new BufferedInputStream(sock.getInputStream());
        encdec = Resencdec;
        main = keyboard;
    }

    public void run(){
        try {
            int read;
            while(main.isConnected() | (read = in.read()) >= 0){
                byte[] message = encdec.decodeNextByte((byte) read);
                if ( message != null){
                    System.out.println("recieved " + Arrays.toString(message));
                    short opcode = ( short ) ((( short ) message [0]) << 8 | ( short ) ( message[1] & 0xff) );
                    router(message, opcode);
                }
            }
        } catch (Exception e) {}
    }


    private void router(byte[] message, short opcode){
        switch (opcode) {
            case DATA:
                handleData(message);
                break;
            
            case ACK:{
                if (main.getUploadStatus())
                    handleAck(message);
                else   
                    printAck(message);
                break;
            }

            case ERROR:
                handleEror(message);
                break;

            case BCAST:
                doBcast(message);
                break;
           
            default:
                doneProccessing();
                break;
        }
    }

    private void handleData(byte[] message){
        if (first){
            // max number that can be get with a short * 512
            data = new byte[16777344];
            reader = 0;
            first = false;
        }

        // gets the length of the data
        byte[] dataSize = new byte[2];
        dataSize[0] = message[2];
        dataSize[1] = message[3];
        short len = arrToShort(dataSize);

        // extracts the actual data
        byte[] toWrite = new byte[len]; 
        System.arraycopy(message, 6, toWrite, 0, message.length - 6);
        
        // write into the right place the new data
        System.arraycopy(toWrite, 0, data, reader*maxDataPack, len);
        int size = len + reader*maxDataPack;
        reader++;


        // all data was received
        if (len != maxDataPack){
            first = true;
            data = Arrays.copyOf(toWrite, size);
            if (main.getDownloadStatus())
                writeFile();
            else   
                printNames();
        }
        else{
            // sends back a ACK packet
            try{
                out.write(encdec.encode((sendAck((short) reader))));
                out.flush();
            }catch(IOException err){}
        }

    }

    private void printNames(){
        String name ="";
        for (int i =0; i < data.length; i++){
            if (data[i] != '\0')
                name += new String(new byte[] {data[i]});
            else{
                System.out.println(name);
                name = "";
            }
        }
        //prints the last name
        System.out.println(name);
        doneProccessing();
    }

    private void writeFile(){
        File file = new File(main.getDname());
        try{
            file.createNewFile();
            //writes the file
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(data);
                System.out.println("RRQ " + main.getDname() + " complete");
            } catch (IOException e) {}
        }catch(IOException err){}
        main.setDownloadStatus(false);

        doneProccessing();
    }

    private void printAck(byte[] message){
        String msg = "ACK ";
        short blockNum =  (short ) ((( short ) message [2]) << 8 | ( short ) ( message[3] & 0xff) );//block number
        msg += blockNum;
        System.out.println(msg);
        if (!main.getLogInStatus()){
            main.setLogInStatus(true);
            doneProccessing();
        }
        else{
            if (blockNum == 0 && !main.getUploadStatus())
                doneProccessing();
        }

    }

    private void handleAck(byte[] message){
        printAck(message);
        if (first){
            reader = 0;
            first = false;
            try {
                File file = new File(main.getUname());
                FileInputStream fis = new FileInputStream(file);

                // Get the length of the file
                long fileSize = file.length();

                // Create a byte array to store the file content
                data = new byte[(int) fileSize];

                // Read the file content into the byte array
                fis.read(data);
                // Close the FileInputStream
                fis.close();

            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }

        long left = data.length - reader*maxDataPack;
        byte[] packData;
        //checks if right ack
        short ack =  ( short ) ((( short ) message [2]) << 8 | ( short ) ( message [3] & 0xff) );
        if(ack != reader)
            reader = ack;

        // everything has been read
        if(left < 0){
            main.setUploadStatus(false);
            first = true;
            System.out.println("WRQ " + main.getUname() + " complete");
            doneProccessing();
            return;
        }

        // done reading the file 
        if(left < maxDataPack){
            packData = Arrays.copyOfRange(data, reader*maxDataPack, data.length);
            main.setUploadStatus(false);
            System.out.println("WRQ " + main.getUname() + " complete");
            doneProccessing();
            first = true;
        }
        else
            packData = Arrays.copyOfRange(data, reader*maxDataPack,reader*maxDataPack + maxDataPack);

        reader++;
        try {
            out.write(encdec.encode(dataPack(packData, (short)reader)));
            out.flush();
        } catch (Exception e) {}

    }

    private void doBcast(byte[] message){
        String msg = "Bcast ";
        if (message[2] == 1)
            msg += "added: ";
        else
            msg += "deleted: ";
        msg += new String(Arrays.copyOfRange(message, 3, message.length));
        System.out.println(msg);
    }

    private void handleEror(byte[] message){
        String msg = "Error ";
        msg +=  (short ) ((( short ) message [2]) << 8 | ( short ) ( message[3] & 0xff) ) + " ";//error type
        msg += new String(Arrays.copyOfRange(message, 4, message.length));
        System.out.println(msg);
        if (!main.isConnected())
            main.setConnectionStatus(true);
        main.setDownloadStatus(false);
        main.setUploadStatus(false);
        first = true;
        doneProccessing();
    }

    private short arrToShort(byte[] arr){
        return ( short ) ((( short ) arr [0]) << 8 | ( short ) ( arr [1] & 0xff) );
    }

    private byte[] shortToArr(short num){
        return new byte []{( byte ) (num >> 8) , ( byte ) (num & 0xff)};
    }

    private byte[] sendAck(short num){
        byte[] ackNum = shortToArr(num);
        byte[] opcode = new byte[]{0x00,0x04};
        byte[] pack   = new byte[opcode.length + ackNum.length];
        System.arraycopy(opcode, 0, pack, 0, opcode.length);
        System.arraycopy(ackNum, 0, pack, opcode.length, ackNum.length);
        return pack;
    }

    private byte[] dataPack(byte[] data, short num){
        short len       = (short)(data.length);
        byte[] packLen  = shortToArr(len);  
        byte[] opcode   = shortToArr(DATA);
        byte[] blockNum = shortToArr(num); 
        byte[] pack     = new byte[len + 6];
        System.arraycopy(opcode, 0, pack, 0, opcode.length);
        System.arraycopy(packLen, 0, pack, opcode.length, packLen.length);
        System.arraycopy(blockNum, 0, pack, opcode.length + packLen.length, blockNum.length);
        System.arraycopy(data, 0, pack, opcode.length + packLen.length + blockNum.length, data.length);
        return pack;
    }

    private void doneProccessing(){
        synchronized(main){
            main.notify();
        }
    }
}
