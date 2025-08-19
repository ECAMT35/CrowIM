package com.ecamt35.userservice.model.vo;

import lombok.Data;

@Data
public class PublicKeysVo {

    String IK;
    String SPK;
    String OPK;

    public PublicKeysVo() {
    }

    public PublicKeysVo(String IK, String SPK, String OPK) {
        this.IK = IK;
        this.SPK = SPK;
        this.OPK = OPK;
    }

}
