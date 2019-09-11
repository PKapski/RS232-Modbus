package modbus.fun;

import java.util.Arrays;

public class ModbusFrame {
    
    private byte address;
    private byte function;
    private byte[] data;

    
    public ModbusFrame() {
        data = new byte[] {};
    }

    public ModbusFrame(int address, int function, byte[] data) {
        if (data == null) {
            this.data = new byte[] {};
        } else {
            this.data = data;
        }
        this.address = (byte) address;
        this.function = (byte) function;
    }

    public byte getAddress() {
        return address;
    }

    public void setAddress(byte address) {
        this.address = address;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public byte getFunction() {
        return function;
    }

    public void setFunction(byte function) {
        this.function = function;
    }

    public byte[] toByteStream() {
        byte[] byteStream = new byte[getSize()];

        byteStream[0] = address;
        byteStream[1] = function;
        System.arraycopy(data, 0, byteStream, 2, data.length);

        return byteStream;
    }

    public int getSize() {
        return 2 + data.length;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ModbusFrame && obj != null) {
            ModbusFrame frame = (ModbusFrame) obj;
            if (address == frame.address && function == frame.function
                    && Arrays.equals(data, frame.data)) {
                return true;
            } else {
                return false;
            }
        }

        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 83 * hash + this.address;
        hash = 83 * hash + this.function;
        hash = 83 * hash + Arrays.hashCode(this.data);
        return hash;
    }
}


