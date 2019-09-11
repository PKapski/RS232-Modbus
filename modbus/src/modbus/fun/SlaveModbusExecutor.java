package modbus.fun;

import iwsk.IWSKView;

public class SlaveModbusExecutor implements ModbusExecutor {
    
    private byte address;

    public SlaveModbusExecutor(byte address) {
        this.address = address;
    }

    public byte getAddress() {
        return address;
    }

    public void setAddress(byte address) {
        this.address = address;
    }
    
    public ModbusFrame execute(ModbusFrame frame) {
        if (isValid(frame)) {
            if (frame.getFunction() == SEND) {
                return processSendRequest(frame);
            } else if (frame.getFunction() == GET) {
                return processGetRequest(frame);
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    public boolean isValid(ModbusFrame frame) {
        if (frame.getAddress() == ModbusFrameBuilder.BROADCAST
                || frame.getAddress() == address) {
            return true;
        } else {
            return false;
        }
    }

    private ModbusFrame processGetRequest(ModbusFrame frame) {
        if (frame.getAddress() == ModbusFrameBuilder.BROADCAST) {
            return null;
        }
        ModbusFrame response = new ModbusFrame();
        response.setAddress(address);
        response.setFunction(frame.getFunction());
        response.setData(IWSKView.tfMessage.getText().getBytes());

        IWSKView.tfGetFrame.setText(ModbusFrameBuilder.stringToHex(ModbusFrameBuilder.serialize(frame)));
        IWSKView.tfSendFrame.setText(ModbusFrameBuilder.stringToHex(ModbusFrameBuilder.serialize(response)));
        return response;
    }

    private ModbusFrame processSendRequest(ModbusFrame frame) {
        if (frame.getAddress() == address ||
                frame.getAddress() == ModbusFrameBuilder.BROADCAST) {
            ModbusFrame response = new ModbusFrame();
            response.setAddress(address);
            response.setFunction(frame.getFunction());
            response.setData(new byte[]{});
            IWSKView.tfMessageWindow.setText(IWSKView.tfMessageWindow.getText()
                    + new String(frame.getData()) + "\r\n");
            IWSKView.tfGetFrame.setText(ModbusFrameBuilder.stringToHex(ModbusFrameBuilder.serialize(frame)));
            if (frame.getAddress() != ModbusFrameBuilder.BROADCAST) {
                IWSKView.tfSendFrame.setText(ModbusFrameBuilder.stringToHex(ModbusFrameBuilder.serialize(response)));
            } else {
                IWSKView.tfSendFrame.setText("");
            }
            return response;
        } else {
            return null;
        }
    }
}
