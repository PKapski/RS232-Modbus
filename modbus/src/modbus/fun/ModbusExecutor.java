package modbus.fun;

public interface ModbusExecutor {
    public static final byte SEND = 1;
    public static final byte GET = 2;

    ModbusFrame execute(ModbusFrame frame);
}
