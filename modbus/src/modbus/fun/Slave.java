package modbus.fun;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;



public class Slave implements Runnable {

    private final long timeout;

    private final byte[] msg = new byte[ModbusFrameBuilder.ASCII_MAX_FRAME_SIZE];
    private int msgIndex;
    
    private SlaveModbusExecutor slaveExecutor;
    private RS232 rs;


    public Slave(long timeout) {
        this.timeout = timeout;
    }

    public RS232 getRs() {
        msgIndex = 0;
        return rs;
    }

    public void setRs(RS232 rs) {
        this.rs = rs;
    }

    public SlaveModbusExecutor getSlaveExecutor() {
        return slaveExecutor;
    }

    public void setSlaveExecutor(SlaveModbusExecutor slaveExecutor) {
        this.slaveExecutor = slaveExecutor;
    }



    public void run() {
        long start = 0;
        while (true) {
            try {
                // TODO implement Slave between-chars timeout logic
                if (rs.isDataAvailable()) {
                    start = System.currentTimeMillis();
                    msg[msgIndex] = rs.getChar();
                    if (isFrameCollected()) {
                        byte[] data = new byte[msgIndex+1];
                        System.arraycopy(msg, 0, data, 0, msgIndex+1);
                        ModbusFrame request = ModbusFrameBuilder.deserialize(data);
                        // check if not null
                        ModbusFrame response =  slaveExecutor.execute(request);
                        if (response != null) {
                            rs.sendMessage(ModbusFrameBuilder.serialize(response));
                        }
                        msgIndex = 0;
                    } else {
                        msgIndex++;
                        if (msgIndex >= ModbusFrameBuilder.ASCII_MAX_FRAME_SIZE) {
                            msgIndex = 0;
                        }
                    }
                } else if (msgIndex > 0) {
                    if ((System.currentTimeMillis() - start) > timeout) {
                        msgIndex = 0;
                    }
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(Slave.class.getName()).log(Level.SEVERE, null, ex);
                    }
                } else {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(Slave.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

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
