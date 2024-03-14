package bgu.spl.net.impl.tftp;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;
import java.util.function.Supplier;

import bgu.spl.net.api.MessageEncoderDecoder;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class TftpClient {
    //TODO: implement the main logic of the client, when using a thread per client the main logic goes here

    public Map <String,Short> comToOp = new HashMap<String,Short>();
    private boolean connected = false;
    private boolean loggedIn = false;
    private static final Supplier<MessageEncoderDecoder<byte[]>> encdecFactory =TftpEncoderDecoder::new ;
    private boolean download = false;//used to see if data should be printed because its names or if a file needs to be written
    private boolean upload = false;//used to see if data should be printed because its names or if a file needs to be written 
    private String dname = null; //file that is being downloaded
    private String uname = null; //file that is being uploaded;
    private BufferedOutputStream out;
    private String opcode;
    private MessageEncoderDecoder<byte[]> encdec;
    public static void main(String[] args) throws IOException {
        TftpClient keyboard = new TftpClient();
        keyboard.run(args);
    }

    public TftpClient(){
        comToOp.put("LOGRQ", (short)7);
        comToOp.put("RRQ", (short)1);
        comToOp.put("WRQ", (short)2);
        comToOp.put("DIRQ", (short)6);
        comToOp.put("DELRQ", (short)8);
        comToOp.put("DISC", (short)10);
    }

    public void run(String[] args)throws IOException{
        if (args.length == 0) {
            args = new String[]{"localhost", "7777"};
        }
        encdec = encdecFactory.get()  ;
        if (args.length < 2) {
            System.out.println("you must supply two arguments: host, port");
            System.exit(1);
        }

        // Create a Scanner object to read input from the console
        Scanner scanner = new Scanner(System.in);
        //BufferedReader and BufferedWriter automatically using UTF-8 encoding
        try (Socket sock = new Socket(args[0], Integer.parseInt(args[1]))) {
            System.out.println("connected to the server!");
            connected = true;
            out = new BufferedOutputStream(sock.getOutputStream());
            Thread listen = new Thread( new listening(sock,encdec,this));
            listen.start();
            while(connected){                
                // Read the input provided by the user
                String command = scanner.nextLine();                    
                // Find the index of the first space character
                int spaceIndex = command.indexOf(" ");
                // Check if a space character was found
                if ( spaceIndex != -1)
                    opcode =  command.substring(0,spaceIndex);
                else
                    opcode = command;
                router(command, opcode);
            }
        }
        scanner.close();
        
    }

    private void router(String command, String opcode){
        switch (opcode) {
            case "LOGRQ":
                login(command);
                break;
            
            case "DELRQ":
                del(command);
                break;

            case "RRQ":
                download(command);
                break;

            case "WRQ":
                upload(command);
                break;

            case "DIRQ":
                getNames();
                break;

            case "DISC":
                disconnect();
                break;
            default:
                break;
        }
    }


    private void login(String msg){
        // gets the username
        byte[] name = parsetNameInBytes(msg);
        if (name == null)
            return;
        byte[] pack = new byte[name.length + 2];
        byte[] code = shortToArr(comToOp.get(opcode));
        System.arraycopy(code, 0, pack, 0, code.length);
        System.arraycopy(name, 0, pack, code.length, name.length);
        try {
            out.write(encdec.encode(pack));
            out.flush();
        } catch (IOException e) {}
        synchronized(this){
            try {
                wait();
            } catch (InterruptedException e) {}
        }
    }

    public void del(String command){
        // gets the file name
        byte[] fname = parsetNameInBytes(command);
        if (fname == null)
            return;
        byte[] pack = new byte[fname.length + 2];
        byte[] code = shortToArr(comToOp.get(opcode));
        System.arraycopy(code, 0, pack, 0, code.length);
        System.arraycopy(fname, 0, pack, code.length, fname.length);

        synchronized(this){
            try {
                out.write(encdec.encode(pack));
                out.flush();
            } catch (IOException e) {}
            try {
                wait();
            } catch (InterruptedException e) {}
        }
    }

    private void download(String msg){
        // gets the file name
        String fname = getFileName(msg);
        if (fname == ""){
            System.out.println("improper use");
            return;
        }

        File file = new File(fname);
        if (file.exists()){
            System.out.println("file already exists");
            return;
        }
        dname = fname;
        byte[] name = fname.getBytes();
        byte[] pack = new byte[name.length + 2];
        byte[] code = shortToArr(comToOp.get(opcode));
        System.arraycopy(code, 0, pack, 0, code.length);
        System.arraycopy(name, 0, pack, code.length, name.length);
        synchronized(this){
            try {
                out.write(encdec.encode(pack));
                out.flush();
                download = true;
            } catch (IOException e) {}
            try {
                wait();
            } catch (InterruptedException e) {

            }
        }
    }

    private void upload(String msg){
        // gets the file name
        String fname = getFileName(msg);
        if( fname == ""){
            System.out.println("improper use");
            return;
        }   
        
        File file = new File(fname);
        if (!file.exists()){
            System.out.println("file does not exists");
            return;
        }
        uname = fname;   
        byte[] name = fname.getBytes();
        byte[] pack = new byte[name.length + 2];
        byte[] code = shortToArr(comToOp.get(opcode));
        System.arraycopy(code, 0, pack, 0, code.length);
        System.arraycopy(name, 0, pack, code.length, name.length);

        synchronized(this){
            try {
                out.write(encdec.encode(pack));
                out.flush();
                upload = true;
            } catch (IOException e) {}
            try {
                wait();
            } catch (InterruptedException e) {

            }
        }
    }

    private void getNames(){
        byte[] pack = shortToArr(comToOp.get("DIRQ"));
        synchronized(this){
            try {
                out.write(encdec.encode(pack));
                out.flush();
            } catch (IOException e) {}
            try {
                wait();
            } catch (InterruptedException e) {

            }
        }
    }

    private void disconnect(){
        byte[] pack = shortToArr(comToOp.get("DISC"));
        connected = false;
        synchronized(this){
            try {
                out.write(encdec.encode(pack));
                out.flush();
            } catch (IOException e) {}
            try {
                wait();
            } catch (InterruptedException e) {}
        }
    }
    
    private byte[] parsetNameInBytes(String command){
        // gets the name
        String name = getFileName(command);
        return name.getBytes();
    }

    private byte[] shortToArr(short num){
        return new byte []{( byte ) (num >> 8) , ( byte ) (num & 0xff)};
    }

    public static String getFileName(String input) {
        // Trim leading and trailing whitespace
        input = input.trim();
        
        // Split the input string by whitespace
        String[] words = input.split("\\s+");
        
        // Check if there are at least two words
        if (words.length >= 2) {
            // Concatenate the second word and everything after it
            StringBuilder remainingText = new StringBuilder();
            for (int i = 1; i < words.length; i++) {
                remainingText.append(words[i]).append(" ");
            }
            return remainingText.toString().trim();
        } else {
            // Return an empty string or handle the case as needed
            return "";
        }
    }

    public String getUname(){
        return uname;
    }
    
    public String getDname(){
        return dname;
    }

    public boolean isConnected(){
        return connected;
    }

    public boolean getDownloadStatus(){
        return download;
    }

    public boolean getLogInStatus(){
        return loggedIn;
    }

    public boolean getUploadStatus(){
        return upload;
    }

    public void setUploadStatus(boolean updated){
        upload = updated;
    }


    public void setDownloadStatus(boolean updated){
        download = updated;
    }

    public void setConnectionStatus(boolean updated){
        connected = updated;
    }

    public void setLogInStatus(boolean updated){
        loggedIn = updated;
    }

}
