package bgu.spl.net.impl.tftp;

import java.util.Arrays;

import bgu.spl.net.api.MessageEncoderDecoder;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
    //TODO: Implement here the TFTP encoder and decoder

    private int maxLen       = -1; 
    private int len          = 0;
    private int pastFlags    = 2;
    private byte[] mess      = new byte[1 << 10]; //start with 1k
    private byte delimiter   = -1;
    private boolean data = false;
    static final short RRQ   = 0x01;
    static final short WRQ   = 0x02;
    static final short DATA  = 0x03;
    static final short ACK   = 0x04;
    static final short ERROR = 0x05;
    static final short DIRQ  = 0x06;
    static final short LOGRQ = 0x07;
    static final short DELRQ = 0x08;
    static final short BCAST = 0x09;
    static final short DISC  = 0x0a;

    @Override
    public byte[] decodeNextByte(byte nextByte) {
        if ( !(delimiter != -1 && delimiter == nextByte) || len <= pastFlags)
            pushByte(nextByte);
        if (len == 2)
            classify();

        // if its a data packet, get the amount of bytes of data
        if (data && len == 4){
            data =false;
            maxLen = ( short ) ((( short ) mess [2]) << 8 | ( short ) ( mess [3]& 0xff) );
            maxLen += 6;//adds the pre data bytes of the packet 
        }

        if (maxLen == len || (len >= pastFlags && delimiter != -1 && delimiter == nextByte) ) {
            mess = Arrays.copyOfRange(mess, 0, len); 
            len = 0;
            delimiter = -1; 
            maxLen = -1;
            pastFlags = 2;
            return mess;
        }
        
        return null; //not a line yet
    }

    @Override
    public byte[] encode(byte[] message) {
        //TODO: implement this
        short opcode = ( short ) ((( short ) message [0]) << 8 | ( short ) ( message [1]) );
        if (opcode != DATA && opcode != ACK && opcode != DIRQ){
            message = Arrays.copyOf(message, message.length + 1);
            message[message.length - 1] = '\0';
        }
        return message;
    }

    private void pushByte(byte nextByte) {
        if (len >= mess.length) {
            mess = Arrays.copyOf(mess, len * 2);
        }

        mess[len++] = nextByte;
    }

    private void classify(){
        short opcode = ( short ) ((( short ) mess [0]) << 8 | ( short ) ( mess [1]) );
        switch (opcode) {
            case RRQ:
                delimiter = '\0';
                break;
            
            case WRQ:
                delimiter = '\0';
                break;

            case DATA:
                data = true;
                break;    
            
            case ACK:
                maxLen = 4;
                break;
            
            case ERROR:{
                pastFlags = 5;
                delimiter = '\0';
                break;
            }

            case DIRQ:
                delimiter = 0x06;
                break;   
            
            case LOGRQ:
                delimiter = '\0';
                break; 

            case DELRQ:
                delimiter = '\0';
                break;

            case BCAST:{
                pastFlags = 4;
                delimiter = '\0';
                break;  
            }
            case DISC:
                delimiter = 0x0a;
                break;     
            
            default:
                break;

        }
    }
}