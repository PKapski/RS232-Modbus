package modbus.fun;

import java.io.IOException;

public class Master {

    private final byte[] msg = new byte[ModbusFrameBuilder.ASCII_MAX_FRAME_SIZE];
    private int msgIndex;

    private MasterModbusExecutor masterExecutor = new MasterModbusExecutor();
    private RS232 rs = new RS232();

    private int timesToResend;
    private long cycleTimeout;
    private long timeout;

    private long startCycle;

    private Master() {

    }
    
    public Master(int timesToResend, long cycleTimeout, long timeout) {
        msgIndex = 0;
        this.timesToResend = timesToResend;
        this.cycleTimeout = cycleTimeout;
        this.timeout = timeout;
    }

    public MasterModbusExecutor getMasterExecutor() {
        return masterExecutor;
    }

    public void setMasterExecutor(MasterModbusExecutor masterExecutor) {
        this.masterExecutor = masterExecutor;
    }

    public RS232 getRs() {
        return rs;
    }

    public void setRs(RS232 rs) {
        this.rs = rs;
    }

    public boolean send(ModbusFrame frame) {
        try {
            masterExecutor.setRequest(frame);
            byte[] data = ModbusFrameBuilder.serialize(frame);
            for (int i = 0; i < timesToResend; ++i) {
                // TODO implement Master between-chars & cycle timeout logic
                rs.sendMessage(data);

                // TODO wait for output buffer empty & start cycle timer
                startCycle = System.currentTimeMillis();

                if (masterExecutor.hasResponse(frame)) {
                    msgIndex = 0;
                    if (collectAndProcessSlaveResponse()) {
                        return true;
                    } else {
                        continue;
                    }
                } else {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } 
    }

    private boolean collectAndProcessSlaveResponse() {
        try {
            long start = 0;
            while (true) {
                // TODO implement between-chars timeout
                if (rs.isDataAvailable()) {
                    msg[msgIndex] = rs.getChar();
                    start = System.currentTimeMillis();
                    if (isFrameCollected()) {
                        byte[] data = new byte[msgIndex + 1];
                        System.arraycopy(msg, 0, data, 0, msgIndex + 1);
                        msgIndex = 0;
                        if (masterExecutor.execute(ModbusFrameBuilder.deserialize(data)) != null) {
                            return true;
                        } else {
                            return false;
                        }
                    } else {
                        msgIndex++;
                        if (msgIndex >= ModbusFrameBuilder.ASCII_MAX_FRAME_SIZE) {
                            return false;
                        }
                    }
                } else if (msgIndex > 0) {
                    if ((System.currentTimeMillis() - start) > timeout) {
                        msgIndex = 0;
                    }
                }

                if ((System.currentTimeMillis() - startCycle) > cycleTimeout) {
                    return false;
                }
            }
       } catch (IOException e) {
           e.printStackTrace();
           return false;
       }
    }

    private boolean isFrameCollected() {
        if (msgIndex > 0 && msg[msgIndex] == ModbusFrameBuilder.LF && msg[msgIndex-1] == ModbusFrameBuilder.CR) {
            return true;
        } else {
            return false;
        }
    }
}
