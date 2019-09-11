package pl.polsl.iwsk;

import gnu.io.*;
import iwsk.IWSKView;
import org.jdesktop.application.Application;
import org.jdesktop.application.ResourceMap;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.TooManyListenersException;


/**
 * RS232 communication
 *
 * @author GKiO1/3
 */
public class RS232 implements SerialPortEventListener {

    public enum DisplayType {ASCII, HEX}

    public enum ResultStatus {IN_USE, NOT_FOUND, EXCESSIVE_LISTENERS, STREAM_ERROR, OK}

    private CommPortIdentifier portId;
    private SerialPort port;
    private String lineTerminator; //CRLF/CR/LF/None
    private InputStream inputStream;
    private OutputStream outputStream;
    private boolean isCheckingPing;
    private long ping;//in ms
    private int timeUntilTimeout;
    private String receivedMessage;
    private ResourceMap resourceMap;
    private DisplayType displayType; //ASCII/HEX
    private static Enumeration serialPortsArray;
    private static ArrayList<String> portNamesArray;
    private Timer timeoutTimer;


    public RS232() {
        resourceMap = Application.getInstance(iwsk.IWSKApp.class).getContext().getResourceMap(IWSKView.class);
    }

    /**
     * Thread sending message
     */
    public class SendMessageThread implements Runnable {
        String toSend;

        SendMessageThread(String message) {
            this.toSend = message;
        }

        public void run() {
            IWSKView.lbOB.setIcon(resourceMap.getIcon("false.icon"));
            sendMessage(toSend);
            IWSKView.lbOB.setIcon(resourceMap.getIcon("true.icon"));
        }
    }


    /**
     * Thread getting the message.
     */
    public class GetMessageThread implements Runnable {
        public void run() {
            IWSKView.lbDA.setIcon(resourceMap.getIcon("true.icon"));
            getMessage();
            IWSKView.lbDA.setIcon(resourceMap.getIcon("false.icon"));
        }
    }


    /**
     * Fills serialPortArray
     */
    public static void createSerialPortsArray() {
        portNamesArray = new ArrayList<>();
        CommPortIdentifier portIdTemp;
        serialPortsArray = CommPortIdentifier.getPortIdentifiers();

        while (serialPortsArray.hasMoreElements()) {
            portIdTemp = (CommPortIdentifier) serialPortsArray.nextElement();
            if (portIdTemp.getPortType() == CommPortIdentifier.PORT_SERIAL)
                portNamesArray.add(portIdTemp.getName());
        }
    }


    public ResultStatus openSerialPort(String terminator,
                                       int parity,
                                       int timeout,
                                       String portName,
                                       int boundRate,
                                       int dataBits,
                                       int stopBits,
                                       int flowControl) {

        if (portId != null) {
            closeSerialPort();
            portId = null;
        }

        try {
            receivedMessage = "";
            ping = 0;
            isCheckingPing = false;
            portId = CommPortIdentifier.getPortIdentifier(portName);
            port = (SerialPort) portId.open("RS232", 2000);
            port.addEventListener(this);
            port.notifyOnDataAvailable(true);
            port.notifyOnCarrierDetect(true);
            port.notifyOnCTS(true);
            port.notifyOnDSR(true);
            port.notifyOnRingIndicator(true);
            port.setDTR(true);
            port.setRTS(true);

            inputStream = port.getInputStream();
            outputStream = port.getOutputStream();
            port.setFlowControlMode(flowControl);
            port.setSerialPortParams(boundRate, dataBits, stopBits, parity);
            this.lineTerminator = terminator;
            this.timeUntilTimeout = timeout;
            return ResultStatus.OK;
        } catch (NoSuchPortException e) {
            return ResultStatus.NOT_FOUND;
        } catch (PortInUseException e) {
            return ResultStatus.IN_USE;
        } catch (TooManyListenersException e) {
            return ResultStatus.EXCESSIVE_LISTENERS;
        } catch (IOException | UnsupportedCommOperationException e) {
            return ResultStatus.STREAM_ERROR;
        }
    }

    public void closeSerialPort() {
        if (portId != null) {
            try {
                if (inputStream != null)
                    inputStream.close();
                if (outputStream != null)
                    outputStream.close();

                if (port != null)
                    port.close();
            } catch (IOException ignored) {
            } finally {
                port = null;
                portId = null;
            }
        }
    }

    private synchronized void sendMessage(String message) {
        if (!message.equals(String.valueOf('\2'))) {
            if (!message.equals(String.valueOf('\3'))) {
                if (displayType == DisplayType.HEX) {
                    if (!lineTerminator.equals("")) {
                        IWSKView.tfMessageWindow.setText(IWSKView.tfMessageWindow.getText() + "[out]: " + stringToHex(message) + "\r\n");
                    } else {
                        IWSKView.tfMessageWindow.setText(IWSKView.tfMessageWindow.getText() + stringToHex(message));
                    }
                } else {
                    if (!lineTerminator.equals("")) {
                        IWSKView.tfMessageWindow.setText(IWSKView.tfMessageWindow.getText() + "[out]: " + message + "\r\n");
                    } else {
                        IWSKView.tfMessageWindow.setText(IWSKView.tfMessageWindow.getText() + message);
                    }
                }
                IWSKView.tfMessage.setText("");
            }

            message += lineTerminator;
            try {
                outputStream.write(message.getBytes());
                outputStream.flush();
            } catch (IOException ignored) {
            }

        } else {
            startPingCalculation();
        }
    }

    /**
     * Starts calculating ping time
     */
    private void startPingCalculation() {
        try {
            isCheckingPing = true;
            outputStream.write(('\2' + lineTerminator).getBytes());
            outputStream.flush();
            ping = System.currentTimeMillis();

            ActionListener taskPerformer = evt -> {
                isCheckingPing = false;
                timeoutTimer.stop();
                String text = IWSKView.tfMessageWindow.getText() + "Ping timeout\r\n";
                IWSKView.tfMessageWindow.setText(text);
            };

            timeoutTimer = new Timer(timeUntilTimeout, taskPerformer);
            timeoutTimer.restart();

        } catch (IOException ignored) {
        }
    }

    private void getMessage() {
        byte[] readBuffer = new byte[256];
        int bytesLeft = -1;

        try {
            bytesLeft = inputStream.available();
            if (bytesLeft > 0)
                inputStream.read(readBuffer, 0, bytesLeft);
        } catch (IOException ignored) {
            return;
        }

        if (!lineTerminator.equals("")) {
            showMessageWithTerminator(readBuffer, bytesLeft);
        } else {
            showMessagesWithoutTerminator(readBuffer, bytesLeft);
        }
    }

    private void showMessageWithTerminator(byte[] readBuffer, int bytesLeft) {
        String tempMessage = new String(readBuffer, 0, bytesLeft);
        int index = tempMessage.indexOf(lineTerminator);

        if (index != -1) {
            receivedMessage += tempMessage.substring(0, index);
            if (receivedMessage.equals(String.valueOf('\2'))) {
                SendMessageThread send = new SendMessageThread(String.valueOf('\3'));
                new Thread(send).start();
            } else if ((receivedMessage.equals(String.valueOf('\3'))) && (isCheckingPing)) { //Check ping message
                isCheckingPing = false;
                timeoutTimer.stop();
                ping = System.currentTimeMillis() - ping;
                String text = IWSKView.tfMessageWindow.getText() + "Ping: " + ping + "ms" + "\r\n";
                IWSKView.tfMessageWindow.setText(text);
            } else {
                if (displayType == DisplayType.HEX) {
                    IWSKView.tfMessageWindow.setText(IWSKView.tfMessageWindow.getText() + "[in]: " + stringToHex(receivedMessage) + "\r\n");
                } else {
                    IWSKView.tfMessageWindow.setText(IWSKView.tfMessageWindow.getText() + "[in]: " + receivedMessage + "\r\n");
                }
            }
            receivedMessage = "";
        } else
            receivedMessage += tempMessage;
    }

    private void showMessagesWithoutTerminator(byte[] readBuffer, int bytesLeft) {
        receivedMessage = new String(readBuffer, 0, bytesLeft);
        for (int i = 0; i < receivedMessage.length(); i++) {
            if (receivedMessage.charAt(i) == '\2') {
                SendMessageThread send = new SendMessageThread(String.valueOf('\3'));
                new Thread(send).start();
            } else if (receivedMessage.charAt(i) == '\3' && isCheckingPing) {
                isCheckingPing = false;
                timeoutTimer.stop();
                ping = System.currentTimeMillis() - ping;
                String newLine = "";
                if (!(String.valueOf(IWSKView.tfMessageWindow.getText().charAt(IWSKView.tfMessageWindow.getText().length() - 1)).equals("\n")))
                    newLine = "\r\n";
                receivedMessage = newLine + "Ping: " + ping + "ms\r\n";
                IWSKView.tfMessageWindow.setText(IWSKView.tfMessageWindow.getText() + receivedMessage);
                break;
            } else {
                if (displayType == DisplayType.HEX)
                    IWSKView.tfMessageWindow.setText(IWSKView.tfMessageWindow.getText() + stringToHex(String.valueOf(receivedMessage.charAt(i))));
                else
                    IWSKView.tfMessageWindow.setText(IWSKView.tfMessageWindow.getText() + receivedMessage.charAt(i));
            }

        }
        receivedMessage = "";
    }

    private String stringToHex(String message) {
        StringBuilder result = new StringBuilder();
        byte[] tab = message.getBytes();
        for (byte b : tab) {
            result.append(Integer.toHexString(b)).append("h ");
        }

        return result.toString();
    }


    /**
     * Event handler if state on pin changes
     */
    public void serialEvent(SerialPortEvent event) {
        switch (event.getEventType()) {
            //Data is avaible, get it
            case SerialPortEvent.DATA_AVAILABLE: {
                GetMessageThread get = new GetMessageThread();
                new Thread(get).start();
                IWSKView.scrWindowMessage.getVerticalScrollBar().setValue(
                        IWSKView.scrWindowMessage.getVerticalScrollBar().getMaximum());
                break;
            }
            //All these cases change icons depending on the state
            case SerialPortEvent.CD: {
                if (event.getNewValue())
                    IWSKView.lbCD.setIcon(resourceMap.getIcon("true.icon"));
                else
                    IWSKView.lbCD.setIcon(resourceMap.getIcon("false.icon"));
                break;
            }
            case SerialPortEvent.CTS: {
                if (event.getNewValue())
                    IWSKView.lbCTS.setIcon(resourceMap.getIcon("true.icon"));
                else
                    IWSKView.lbCTS.setIcon(resourceMap.getIcon("false.icon"));
                break;
            }
            case SerialPortEvent.DSR: {
                if (event.getNewValue())
                    IWSKView.lbDSR.setIcon(resourceMap.getIcon("true.icon"));
                else
                    IWSKView.lbDSR.setIcon(resourceMap.getIcon("false.icon"));
                break;
            }
            case SerialPortEvent.RI: {
                if (event.getNewValue())
                    IWSKView.lbRI.setIcon(resourceMap.getIcon("true.icon"));
                else
                    IWSKView.lbRI.setIcon(resourceMap.getIcon("false.icon"));
                break;
            }
        }
    }


    public static Enumeration getSerialPortsArray() {
        return serialPortsArray;
    }

    public static ArrayList<String> getPortNamesArray() {
        return portNamesArray;
    }

    public CommPortIdentifier getPortId() {
        return portId;
    }

    public SerialPort getPort() {
        return port;
    }

    public OutputStream getOutputStream() {
        return outputStream;
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public String getLineTerminator() {
        return lineTerminator;
    }

    public int getTimeUntilTimeout() {
        return timeUntilTimeout;
    }

    public SendMessageThread getSendThread(String s) {
        return new SendMessageThread(s);
    }


    public void setDisplayType(DisplayType displayType) {
        this.displayType = displayType;
    }

}
