package com.ecamt35.messageservice.constant;

public class PacketTypeConstant {

    // from client
    public final static int CLIENT_REQUEST_SENT = 1;
    public final static int CLIENT_ACK_RECEIVED = 2;
    public final static int CLIENT_ACK_READ = 3;
    public final static int CLIENT_REQUEST_READ_LIST = 4;
    public final static int CLIENT_REQUEST_FINISHED = 5;

    // to client
    public final static int SERVER_REQUEST_SENT = 6;
    public final static int SERVER_ACK_SENT = 7;
    public final static int SERVER_ACK_RECEIVED = 8;
    public final static int SERVER_ACK_READ = 9;
    public final static int SERVER_ACK_READ_LIST = 10;
    public final static int SERVER_ACK_FINISHED = 11;
}
