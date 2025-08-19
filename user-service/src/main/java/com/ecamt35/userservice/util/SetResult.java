package com.ecamt35.userservice.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SetResult {

    private Set<?> set;

    private Long selectTotal;

    public static SetResult success(Set<?> set, Long selectTotal) {
        return new SetResult(set, selectTotal);
    }
}