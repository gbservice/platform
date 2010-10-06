package platform.client.logics.classes;

import platform.client.form.ClientFormController;
import platform.client.form.PropertyEditorComponent;
import platform.client.form.cell.CellView;
import platform.client.form.cell.TableCellView;
import platform.client.logics.ClientPropertyDraw;
import platform.client.logics.ClientGroupObjectValue;
import platform.interop.ComponentDesign;

import java.awt.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.text.Format;

public abstract class ClientDataClass extends ClientClass implements ClientType {

    ClientDataClass(DataInputStream inStream) throws IOException {
        super(inStream);
    }

    public abstract byte getTypeId();

    @Override
    public void serialize(DataOutputStream outStream) throws IOException {
        outStream.writeByte(getTypeId());
    }

    public boolean hasChildren() {
        return false;
    }

    public int getMinimumWidth(FontMetrics fontMetrics) {
        return fontMetrics.stringWidth(getMinimumMask()) + 8;
    }

    public int getPreferredWidth(FontMetrics fontMetrics) {
        return fontMetrics.stringWidth(getPreferredMask()) + 8;
    }

    public int getMaximumWidth(FontMetrics fontMetrics) {
        return Integer.MAX_VALUE;
    }

    public int getPreferredHeight(FontMetrics fontMetrics){
        return fontMetrics.getHeight() + 1;
    }

    public String getMinimumMask() {
        return getPreferredMask();
    }

    abstract public String getPreferredMask();

    protected abstract PropertyEditorComponent getComponent(Object value, Format format, ComponentDesign design);

    public CellView getPanelComponent(ClientPropertyDraw key, ClientGroupObjectValue columnKey, ClientFormController form) {
        return new TableCellView(key, columnKey, form);
    }

    public PropertyEditorComponent getEditorComponent(ClientFormController form, ClientPropertyDraw property, Object value, Format format, ComponentDesign design) throws IOException, ClassNotFoundException {
        return getComponent(value, format, design);
    }

    public PropertyEditorComponent getClassComponent(ClientFormController form, ClientPropertyDraw property, Object value, Format format) throws IOException, ClassNotFoundException {
        return getComponent(value, format, null);
    }

    public boolean shouldBeDrawn(ClientFormController form) {
        return true;
    }

    public String getConformedMessage(){
        return "Вы действительно хотите редактировать свойство";
    }
}
