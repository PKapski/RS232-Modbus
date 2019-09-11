package iwsk;

import java.util.ArrayList;
import java.util.List;
import modbus.fun.ModbusFrame;
import modbus.fun.ModbusFrameBuilder;
import org.junit.Test;
import static org.junit.Assert.*;

public class ModbusFrameBuilderTest {

    @Test
    public void testSerialization() {
        List<ModbusFrame> senderList = new ArrayList<ModbusFrame>();
        senderList.add(new ModbusFrame(1, 3, new byte[] {}));
        senderList.add(new ModbusFrame(0, 0, null));
        senderList.add(new ModbusFrame());
        senderList.add(new ModbusFrame(-5, 1, new byte[] {1}));
        senderList.add(new ModbusFrame(1024, 1, new byte[] {127, 1, -10}));
        senderList.add(new ModbusFrame(1, 3, new byte[] {1, 2, 3}));

        for (ModbusFrame send : senderList) {
            byte[] data = ModbusFrameBuilder.serialize(send);
            assertEquals(data[0], ModbusFrameBuilder.SOF);
            assertEquals(data[data.length - 2], ModbusFrameBuilder.CR);
            assertEquals(data[data.length - 1], ModbusFrameBuilder.LF);

            ModbusFrame receive = ModbusFrameBuilder.deserialize(data);

            assertNotNull(receive);
            assertEquals(receive, send);
            assertEquals(receive.getAddress(), send.getAddress());
            assertEquals(receive.getFunction(), send.getFunction());
            assertArrayEquals(receive.getData(), send.getData());
        }
    }
}
