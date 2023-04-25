package com.unfbx.chatgptsteamoutput.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 *
 * @author qinchuan
 * @date 2023/4/25 11:10.
 */
@Getter
@Setter
@ToString
public class ResponseError {

    private Error error;

    @Getter
    @Setter
    @ToString
    public static class Error {

        private String message;
        private String type;
        private String param;
        private String code;
    }
}
