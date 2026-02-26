package com.ecamt35.messageservice.constant;

public class PacketTypeConstant {

    // from client
    public static final int CLIENT_REQUEST_SENT = 100;
    public static final int CLIENT_ACK_READ = 101;
    public static final int CLIENT_PULL_SUMMARY = 102;

    // to client
    public static final int SERVER_REQUEST_SENT = 200;
    public static final int SERVER_ACK_SENT = 201;
    public static final int SERVER_ACK_READ = 202;
    public static final int SERVER_SUMMARY = 203;

    // others
    public static final int INVALID_MESSAGE_FORMAT = 400;
    public static final int INSUFFICIENT_PERMISSIONS = 401;
}
