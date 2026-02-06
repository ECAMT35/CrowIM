package com.ecamt35.messageservice.constant;

public class PacketTypeConstant {

    // from client
    public final static int CLIENT_REQUEST_SENT = 100;
    public final static int CLIENT_ACK_READ = 101;

    // to client
    public final static int SERVER_REQUEST_SENT = 200;
    public final static int SERVER_ACK_SENT = 201;
    public final static int SERVER_ACK_READ = 202;

    // others
    public final static int INVALID_MESSAGE_FORMAT = 400;
    public final static int INSUFFICIENT_PERMISSIONS = 401;
}
