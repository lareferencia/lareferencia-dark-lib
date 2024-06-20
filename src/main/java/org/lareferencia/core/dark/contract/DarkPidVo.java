package org.lareferencia.core.dark.contract;

import org.web3j.utils.Numeric;

import java.nio.charset.StandardCharsets;

public class DarkPidVo {

    public String pidHash;
    public String pidHex;
    public DarkPidVo(String pidHash) {
        this.pidHash = pidHash;
        this.pidHex = Numeric.toHexString(pidHash.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String toString() {
        return "DarkPidVo{" +
                "pidHash='" + pidHash + '\'' +
                ", pidHex='" + pidHex + '\'' +
                '}';
    }
}
