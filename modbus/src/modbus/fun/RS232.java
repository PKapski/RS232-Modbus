package modbus.fun;

import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import gnu.io.SerialPortEventListener;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class RS232 {

    protected CommPortIdentifier portId;
    protected SerialPort port;
    
    protected InputStream inputStream;
    protected OutputStream outputStream;

    
    public static List<String> createSerialPortList()
    {
        Enumeration portList = null;
        List<String> portNameList = new ArrayList<String>();
        CommPortIdentifier portIdTemp;

        portList = CommPortIdentifier.getPortIdentifiers();
        while (portList.hasMoreElements())
        {
            portIdTemp = (CommPortIdentifier)portList.nextElement();
            if (portIdTemp.getPortType() == CommPortIdentifier.PORT_SERIAL)
                portNameList.add(portIdTemp.getName());
        }
        portIdTemp = null;
        return portNameList;
    }

    // NOTE actually specific event handling should be implemented in View class logic
    synchronized public boolean openPort(String portName, SerialPortEventListener eventListener,
            int speed, int dataBits, int stopBits, int parity,
            int flowControl) {
        if (portId != null) {
            closePort();
            portId = null;
        }
        
        try {
            portId = CommPortIdentifier.getPortIdentifier(portName);
            port = (SerialPort)portId.open("RS232", 2000);

            port.addEventListener(eventListener);
            port.notifyOnDataAvailable(true);
            port.notifyOnOutputEmpty(true);
            port.notifyOnCarrierDetect(true);
            port.notifyOnCTS(true);
            port.notifyOnDSR(true);
            port.notifyOnRingIndicator(true);
            port.setDTR(true);
            port.setRTS(true);

            inputStream = port.getInputStream();
            outputStream = port.getOutputStream();
            port.setFlowControlMode(flowControl);
            port.setSerialPortParams(speed, dataBits, stopBits, parity);

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    synchronized public void closePort() {
        if (portId == null) {
            return;
        }
        try {
            if (inputStream != null)
                inputStream.close();
            if (outputStream != null)
                outputStream.close();

            if (port != null)
                port.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            port   = null;
            portId = null;
        }
    }

    synchronized public int getAvailableBytes() throws IOException {
        return inputStream.available();
    }

    synchronized public  boolean isDataAvailable() throws IOException {
        if (inputStream.available() > 0) {
            return true;
        } else {
            return false;
        }
    }

    synchronized public void sendMessage(byte[] message) throws IOException {
        outputStream.write(message);
        outputStream.flush();
    }

    synchronized public byte getChar() throws IOException {
        return getMessage(1)[0];
    }

    synchronized public byte[] getMessage(int numberOfChars) throws IOException {
        byte[] readBuffer = new byte[numberOfChars];

        inputStream.read(readBuffer, 0, numberOfChars);
        return readBuffer;
    }
}
