package modbus.fun;

public class ModbusFrameBuilder {
    public static final int ASCII_MAX_FRAME_SIZE = 513;
    public static final byte BROADCAST = 0;
    
    public static final byte SOF = (byte)0x3A;
    public static final byte CR = (byte)0x0A;
    public static final byte LF = (byte)0x0D;
    
    public static byte[] serialize(ModbusFrame frame) {
        byte[] output = new byte[getSizeInAscii(frame)];
        
        byte[] ascii = toAscii(frame.toByteStream());
        byte lrc = computeLRC(ascii);

        output[0] = ModbusFrameBuilder.SOF;
        System.arraycopy(ascii, 0, output, 1, ascii.length);
        output[output.length - 4] = (byte)((lrc >> 4) & (byte)0x0F);
        output[output.length - 3] = (byte)(lrc & (byte)0x0F);
        output[output.length - 2] = ModbusFrameBuilder.CR;
        output[output.length - 1] = ModbusFrameBuilder.LF;
        
        return output;
    }
    
    public static ModbusFrame deserialize(byte[] ascii) {
        if (!isValid(ascii)) {
            return null;
        }
        byte[] temp = new byte[ascii.length - 5];

        System.arraycopy(ascii, 1, temp, 0, temp.length);
        byte[] data = fromAscii(temp);

        byte[] actualData = new byte[data.length - 2];
        System.arraycopy(data, 2, actualData, 0, actualData.length);

        return new ModbusFrame(data[0], data[1], actualData);
    }

    private ModbusFrameBuilder() {
    }

    private static byte computeLRC(byte[] data) {
        byte sum = 0;
        for (int i = 0; i < data.length; i++)
            sum += data[i];

        return (byte) (~sum);
    }

    private static byte[] toAscii(byte[] data) {
        byte[] ascii = new byte[data.length << 1];
        for (int i = 0; i < data.length; ++i) {
            // TODO check out byte ordering - assuming big endian
            ascii[2*i]     = (byte)((data[i] >> 4) & 0x0F);
            ascii[2*i + 1] = (byte)(data[i] & 0x0F);
        }
        return ascii;
    }

    private static byte[] fromAscii(byte[] ascii) {
        byte[] data = new byte[ascii.length >> 1];
        for (int i = 0; i < data.length; ++i) {
            // TODO check out byte ordering - assuming big endian
            data[i] = (byte)((ascii[2*i] << 4) + ascii[2*i+1]);
        }
        return data;
    }

    private static boolean isValid(byte[] ascii) {
         if ( ascii == null ||
              ascii[0] != ModbusFrameBuilder.SOF ||
              ascii[ascii.length - 2] != ModbusFrameBuilder.CR ||
              ascii[ascii.length - 1] != ModbusFrameBuilder.LF) {
             return false;
        }

        byte lrc = (byte) ((ascii[ascii.length - 4] << 4) + ascii[ascii.length - 3]);
        ascii[0] = 0;
        ascii[ascii.length-4] = 0;
        ascii[ascii.length-3] = 0;
        ascii[ascii.length-2] = 0;
        ascii[ascii.length-1] = 0;
        
        if (lrc != computeLRC(ascii)) {
            return false;
        } else {
            return true;
        }
    }

    private static int getSizeInAscii(ModbusFrame frame) {
        return 2 * (4 + frame.getData().length) + 1;
    }

    protected static String stringToHex(byte[] message) {
        String result = new String();
        for (int i=0; i<message.length; i++) {
            result += Integer.toHexString((int)message[i]) + "h ";
        }
        return result;
    }
}
