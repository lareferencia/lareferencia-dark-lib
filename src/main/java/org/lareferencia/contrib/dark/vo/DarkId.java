package org.lareferencia.contrib.dark.vo;

import lombok.Getter;
import lombok.Setter;

@Getter
public class DarkId {

    private String pidHashAsString;
    private byte[] pidHashAsByteArray;
    @Setter
    private String formattedDarkId;

    public DarkId(String pidHashAsString, byte[] pidHashAsByteArray, String formattedDarkId) {
        this.pidHashAsString = pidHashAsString;
        this.pidHashAsByteArray = pidHashAsByteArray;
        this.formattedDarkId = formattedDarkId;
    }

    @Override
    public String toString() {
        return "DarkPidVo{" +
                "pidHash='" + pidHashAsString + '\'' +
                ", pidHex='" + pidHashAsByteArray + '\'' +
                '}';
    }
}
