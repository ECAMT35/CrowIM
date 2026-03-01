package com.ecamt35.messageservice.constant;

public class RelationStatusConstant {

    private RelationStatusConstant() {
    }

    public static final int APPLY_PENDING = 0;
    public static final int APPLY_ACCEPTED = 1;
    public static final int APPLY_REJECTED = 2;
    public static final int APPLY_CANCELED = 3;

    public static final int JOIN_POLICY_OPEN = 0;
    public static final int JOIN_POLICY_APPROVAL = 1;

    public static final int ROLE_MEMBER = 1;
    public static final int ROLE_ADMIN = 2;
    public static final int ROLE_OWNER = 3;
}
