package modbus.fun;

import iwsk.IWSKView;

public class MasterModbusExecutor implements ModbusExecutor {

    private ModbusFrame request;

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
        if ((frame.getAddress() == request.getAddress()) &&
            frame.getFunction() == request.getFunction()) {
            return true;
        } else {
            return false;
        }
    }

    private ModbusFrame processGetRequest(ModbusFrame frame) {
        IWSKView.tfMessageWindow.setText(IWSKView.tfMessageWindow.getText()
            + new String(frame.getData()) + "\r\n");
        IWSKView.tfGetFrame.setText(ModbusFrameBuilder.stringToHex(ModbusFrameBuilder.serialize(frame)));
        IWSKView.tfSendFrame.setText(ModbusFrameBuilder.stringToHex(ModbusFrameBuilder.serialize(request)));
        return new ModbusFrame();
    }

    private ModbusFrame processSendRequest(ModbusFrame frame) {
        IWSKView.tfMessageWindow.setText(IWSKView.tfMessageWindow.getText()
            + "Potwierdzono operacje." + "\r\n");
        IWSKView.tfGetFrame.setText(ModbusFrameBuilder.stringToHex(ModbusFrameBuilder.serialize(frame)));
        IWSKView.tfSendFrame.setText(ModbusFrameBuilder.stringToHex(ModbusFrameBuilder.serialize(request)));
        return new ModbusFrame();
    }

    public boolean hasResponse(ModbusFrame frame) {
        if (frame.getAddress() == ModbusFrameBuilder.BROADCAST) {
            return false;
        } else {
            return true;
        }
    }

    public ModbusFrame getRequest() {
        return request;
    }

    public void setRequest(ModbusFrame request) {
        this.request = request;
    }
}
