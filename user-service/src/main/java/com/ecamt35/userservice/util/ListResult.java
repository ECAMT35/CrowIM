package com.ecamt35.userservice.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ListResult {

    private List<?> list;

    private Long selectTotal;

    public static ListResult success(List<?> list, Long selectTotal) {
        return new ListResult(list, selectTotal);
    }
}