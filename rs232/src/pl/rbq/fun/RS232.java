package pl.rbq.fun;

import java.util.*;
import gnu.io.*;
import iwsk.IWSKView;
import java.awt.event.*;
import java.io.*;
import javax.swing.Timer;
import org.jdesktop.application.*;



/** Klasa realizujaca komunikacje poprzez port szeregowy RS232.
 *
 * @author Mateusz Chrobok
 * @version 1.0
 * @since 23.02.2010
 */
public class RS232 implements SerialPortEventListener
{
    // <editor-fold defaultstate="collapsed" desc="Fields">
    /**  */
    protected CommPortIdentifier portId;
    /**  */
    protected SerialPort port;
    /**  Informuje o stosowanym terminatorze w transmisji (CRLF, CR, LF, brak)*/
    protected String terminator;


    /**  Strumien wejsciowy powiazany z portem.*/
    protected InputStream inputStream;
    /**  Strumien wyjsciowy powiazany z portem.*/
    protected OutputStream outputStream;


    /**  Informuje czy w danej chwili jest sprawdzany ping.*/
    protected boolean checkingPing;
    /**  Czas pingowania w [ms].*/
    protected long ping;
    /**  Czas, po którym następuje timeout pingowania.*/
    protected int timeout;


    /**  Odebrana wiadomość*/
    protected String message;


    /**  Dostęp do zasobów aplikacji.*/
    protected ResourceMap resourceMap;


    /**  false - wyświetlanie ASCII, true - wyświetlanie w HEX*/
    protected boolean display;


    /**  */
    static Enumeration portList;
    /**  */
    static ArrayList<String> portNameList;


    /**  Flaga informujaca, ze port jest w uzyciu [dot. metody openPort].*/
    public final static int PORT_IN_USE = 0;
    /**  Flaga informujaca, ze wybrany port nie istnieje [dot. metody openPort].*/
    public final static int PORT_NO_EXISTS = 1;
    /**  Flaga informujaca o wystapieniu problemu z nasluchiwaniem zdarzen [dot. metody openPort].*/
    public final static int TOO_MANY_LISTENERS = 2;
    /**  Flaga informujaca o bledzie z pobraniem strumienia wejscia lub wyjscia [dot. metody openPort].*/
    public final static int STREAM_ERR = 3;
    /**  Flaga informujaca o sukcesie opepracji [dot. metody openPort].*/
    public final static int OK = 4;


    /**  Flaga informujaca o wyświetlaniu wiadomosci jako ASCII [dot. pola display].*/
    public final static boolean ASCII = false;
    /**  Flaga informujaca o wyświetlaniu wiadomosci jako HEX [dot. pola display].*/
    public final static boolean HEX = true;

    /**  Odmierza czas timeoutu i informuje o nim po jego przekroczeniu.*/
    protected Timer timer;
    // </editor-fold>


    
    /** Klasa watku wysylajacego wiadomosc.
     * 
     */
    public class SendThread implements Runnable
    {
        String toSend;

        public SendThread(String message)
        {
             this.toSend = message;
        }

        public void run()
        {
            IWSKView.lbOB.setIcon(resourceMap.getIcon("false.icon"));
            sendMessage(toSend);
            IWSKView.lbOB.setIcon(resourceMap.getIcon("true.icon"));
        }
    }




    /** Klasa watku odbierajaca wiadomosc.
     *
     */
    public class GetMessage implements Runnable
    {
        public void run()
        {
            IWSKView.lbDA.setIcon(resourceMap.getIcon("true.icon"));
            getMessage();
            IWSKView.lbDA.setIcon(resourceMap.getIcon("false.icon"));
        }
    }




    public RS232()
    {
        resourceMap = Application.getInstance(iwsk.IWSKApp.class).getContext().getResourceMap(IWSKView.class);
    }

    /** Tworzy liste dostępnych portów szeregowych i umieszcza ją w zmiennej portNameList
     *
     */
    public static void createSerialPortList()
    {
        portList = null;
        portNameList = new ArrayList<String>();
        CommPortIdentifier portIdTemp;

        portList = CommPortIdentifier.getPortIdentifiers();
        while (portList.hasMoreElements())
        {
            portIdTemp = (CommPortIdentifier)portList.nextElement();
            if (portIdTemp.getPortType() == CommPortIdentifier.PORT_SERIAL)
                portNameList.add(portIdTemp.getName());
        }
        portIdTemp = null;
    }







    public int openPort(String portName, int speed, int dataBits, int stopBits, int flowControl,
            String terminator, int parity, int timeout)
    {
        if (portId != null)
        {
            closePort();
            portId = null;
        }

        try
        {
            message = new String("");
            ping = 0;
            checkingPing = false;
            portId = CommPortIdentifier.getPortIdentifier(portName);
            port = (SerialPort)portId.open("RS232", 2000);
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
            port.setSerialPortParams(speed, dataBits, stopBits, parity);
            this.terminator = terminator;
            this.timeout = timeout;
            return RS232.OK;
        }
        catch (NoSuchPortException e)
        {
            return RS232.PORT_NO_EXISTS;
        }
        catch (PortInUseException e)
        {
            return RS232.PORT_IN_USE;
        }
        catch (TooManyListenersException e)
        {
            return RS232.TOO_MANY_LISTENERS;
        }
        catch (IOException e)
        {
            return RS232.STREAM_ERR;
        }
        catch (UnsupportedCommOperationException e)
        {
            return RS232.STREAM_ERR;
        }
    }
    public void closePort()
    {
        if (portId != null)
        {
            try
            {
                if (inputStream != null)
                    inputStream.close();
                if (outputStream != null)
                    outputStream.close();

                if (port != null)
                    port.close();
            }
            catch (IOException e)
            {
            }
            finally
            {
                port = null;
                portId = null;
            }
        }
    }
    synchronized public int sendMessage(String s)
    {
        String hex = "";
        if (!terminator.equals(""))
        {
            if (!s.equals(String.valueOf('\2')))
            {
                if (!s.equals(String.valueOf('\3')))
                {
                    if (display)
                    {
                        hex = stringToHex(s);
                        IWSKView.tfMessageWindow.setText(IWSKView.tfMessageWindow.getText() + "[out]: " + hex + "\r\n");
                    }
                    else
                        IWSKView.tfMessageWindow.setText(IWSKView.tfMessageWindow.getText() + "[out]: " + s + "\r\n");
                    
                    IWSKView.tfMessage.setText("");
                }
                
                s += terminator;
                try
                {
                    outputStream.write(s.getBytes());
                    outputStream.flush();
                    return RS232.OK;
                }
                catch (IOException e)
                {
                    return RS232.STREAM_ERR;
                }
            }
            else
            {
                ping();
                return RS232.OK;
            }
        }
        else
        {
            if (!s.equals(String.valueOf('\2')))
            {
                if (!s.equals(String.valueOf('\3')))
                {
                    if (display)
                    {
                        hex = stringToHex(s);
                        IWSKView.tfMessageWindow.setText(IWSKView.tfMessageWindow.getText() + hex);
                    }
                    else
                        IWSKView.tfMessageWindow.setText(IWSKView.tfMessageWindow.getText() + s);
                }
                IWSKView.tfMessage.setText("");
                try
                {
                    outputStream.write(s.getBytes());
                    outputStream.flush();
                    return RS232.OK;
                }
                catch (IOException e)
                {
                    return RS232.STREAM_ERR;
                }
            }
            else
            {
                ping();
                return RS232.OK;
            }
        }
    }
    public void getMessage()
    {
        byte[] readBuffer = new byte[256];
        int availableBytes = -1;
        String tempMessage = "";

        try
        {
            availableBytes = inputStream.available();
            if (availableBytes > 0)
                inputStream.read(readBuffer, 0, availableBytes);
        }
        catch (IOException e)
        {
        }


        if (!terminator.equals(""))
        {
            boolean isPingMessage = false;
            int index = -1;
            tempMessage = new String(readBuffer, 0, availableBytes);
            if ((index = tempMessage.indexOf(terminator)) != -1)
                message += tempMessage.substring(0, index);

            if (index != -1)
            {
                if (message.equals(String.valueOf('\2')))
                {
                    message = "";
                    SendThread send = new SendThread(String.valueOf('\3'));
                    new Thread(send).start();                    
                    return;
                }
                if ((message.equals(String.valueOf('\3'))) && (checkingPing == true))
                {
                    checkingPing = false;
                    timer.stop();
                    ping = System.currentTimeMillis() - ping;
                    message = "Ping: " + ping + "ms";
                    ping = 0;
                    isPingMessage = true;
                }

                if (!(message.equals(String.valueOf('\2')) || (message.equals(String.valueOf('\3')))))
                {
                    if (display && !isPingMessage)
                        message = stringToHex(message);
                    String text = IWSKView.tfMessageWindow.getText() + "[in]: " + message + "\r\n";
                    IWSKView.tfMessageWindow.setText(text);
                }
                else if (message.equals(String.valueOf('\3')))
                {
                    String text = IWSKView.tfMessageWindow.getText() + message + "\r\n";
                    IWSKView.tfMessageWindow.setText(text);
                }
                message = "";
            }
            else
                message += tempMessage;
        }
        else
        {
            message = new String(readBuffer, 0, availableBytes);
            for (int i=0; i<message.length(); i++)
            {
                if (message.charAt(i) == '\2')
                {
                    SendThread send = new SendThread(String.valueOf('\3'));
                    new Thread(send).start();
                }
                else if (message.charAt(i) == '\3' && checkingPing == true)
                {
                    checkingPing = false;
                    timer.stop();
                    ping = System.currentTimeMillis() - ping;
                    String newLine = "";
                    if (!(String.valueOf(IWSKView.tfMessageWindow.getText().charAt(IWSKView.tfMessageWindow.getText().length()-1)).equals("\n")))
                        newLine = "\r\n";
                    message = newLine + "Ping: " + ping + "ms\r\n";
                    ping = 0;
                    IWSKView.tfMessageWindow.setText(IWSKView.tfMessageWindow.getText() + message);
                    break;
                }
                else
                {
                    if(display)
                        IWSKView.tfMessageWindow.setText(IWSKView.tfMessageWindow.getText() + stringToHex(String.valueOf(message.charAt(i))));
                    else
                        IWSKView.tfMessageWindow.setText(IWSKView.tfMessageWindow.getText() + String.valueOf(message.charAt(i)));
                }

            }
            message = "";
        }
    }


    /** Rozpoczyna obliczanie czasu pingowania.
     *
     * @return Zwraca informacje o sukcesie lub niepowodzeniu operacji.
     */
    public int ping()
    {
        try
        {
            checkingPing = true;
            outputStream.write((String.valueOf('\2') + terminator).getBytes());
            outputStream.flush();
            ping = System.currentTimeMillis();

            ActionListener taskPerformer = new ActionListener()
            {
                public void actionPerformed(ActionEvent evt)
                {
                    checkingPing = false;
                    ping = 0;
                    timer.stop();
                    String text = IWSKView.tfMessageWindow.getText() + "Ping timeout\r\n";
                    IWSKView.tfMessageWindow.setText(text);                    
                }
            };

            timer = new Timer(timeout, taskPerformer);
            timer.restart();

            return RS232.OK;
        }
        catch (IOException e)
        {
            return RS232.STREAM_ERR;
        }
    }











    /** Obsługa zdarzeń wywoływanych zmianą pinów na porcie.
     *
     * @param event opisuje otrzymane zdarzenie.
     */
    public void serialEvent(SerialPortEvent event)
    {        
        switch (event.getEventType())
        {
            /* Odbierz wiadomość */
            case SerialPortEvent.DATA_AVAILABLE:
            {
                GetMessage get = new GetMessage();
                new Thread(get).start();
                IWSKView.scrWindowMessage.getVerticalScrollBar().setValue(
                        IWSKView.scrWindowMessage.getVerticalScrollBar().getMaximum());
                break;
            }
            /* Zmień ikone etykiety pinu Carrier Detect na zieloną lub czerwoną w zależności od stanu pinu.*/
            case SerialPortEvent.CD:
            {
                if (event.getNewValue())
                    IWSKView.lbCD.setIcon(resourceMap.getIcon("true.icon"));
                else
                    IWSKView.lbCD.setIcon(resourceMap.getIcon("false.icon"));
                break;
            }
            /* Zmień ikone etykiety pinu Clear To Send na zieloną lub czerwoną w zależności od stanu pinu.*/
            case SerialPortEvent.CTS:
            {
                if (event.getNewValue())
                    IWSKView.lbCTS.setIcon(resourceMap.getIcon("true.icon"));
                else
                    IWSKView.lbCTS.setIcon(resourceMap.getIcon("false.icon"));
                break;
            }
            /* Zmień ikone etykiety pinu Data Send Ready na zieloną lub czerwoną w zależności od stanu pinu.*/
            case SerialPortEvent.DSR:
            {
                if (event.getNewValue())
                    IWSKView.lbDSR.setIcon(resourceMap.getIcon("true.icon"));
                else
                    IWSKView.lbDSR.setIcon(resourceMap.getIcon("false.icon"));
                break;
            }
            /* Zmień ikone etykiety pinu Ring Indicator na zieloną lub czerwoną w zależności od stanu pinu.*/
            case SerialPortEvent.RI:
            {
                if (event.getNewValue())
                    IWSKView.lbRI.setIcon(resourceMap.getIcon("true.icon"));
                else
                    IWSKView.lbRI.setIcon(resourceMap.getIcon("false.icon"));
                break;
            }
        }
    }




    /** Zamiana tekstu na HEX. Np "123" zamienia na "31h 32h 33h".
     *
     * @param message Wiadomość tekstowa mająca zostać przerobiona na HEX.
     * @return Wiadomość przekazana na wejściu w postaci HEX.
     */
    protected String stringToHex(String message)
    {
        String result = "";
        byte tab[] = message.getBytes();
        for (int i=0; i<tab.length; i++)
        {
            result += Integer.toHexString(tab[i])+"h ";
        }

        return result;
    }




    public static Enumeration getPortList() { return portList; }
    public static ArrayList<String> getPortNameList() { return portNameList; }
    public CommPortIdentifier getPortId() { return portId; }
    public SerialPort getPort() { return port; }
    public OutputStream getOutputStream() { return outputStream; }
    public InputStream getInputStream() { return inputStream; }
    public String getTerminator() { return terminator; }
    public int getTimeout() { return timeout; }
    public SendThread getSendThread(String s) { return new SendThread(s); }


    public void setDisplay(boolean display) { this.display = display; }

}
