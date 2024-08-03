package org.lareferencia.core.dark.vo;

import lombok.Getter;
import org.web3j.utils.Numeric;

@Getter
public class DarkId {

    private String pidHashAsString;
    private byte[] pidHashAsByteArray;
    public DarkId(String pidHashAsString) {
        this.pidHashAsString = pidHashAsString;
        this.pidHashAsByteArray = Numeric.hexStringToByteArray(pidHashAsString);
    }

    @Override
    public String toString() {
        return "DarkPidVo{" +
                "pidHash='" + pidHashAsString + '\'' +
                ", pidHex='" + pidHashAsByteArray + '\'' +
                '}';
    }
}
