package lsfusion.server.logics.classes.data;

import lsfusion.interop.classes.DataType;
import lsfusion.server.data.type.Type;

public class HTMLTextClass extends TextClass{

    public final static HTMLTextClass instance = new HTMLTextClass();

    public HTMLTextClass() {
        super("html");
    }

    @Override
    public byte getTypeID() {
        return DataType.HTMLTEXT;
    }

    @Override
    public String getSID() {
        return "HTMLTEXT";
    }

    @Override
    public DataClass getCompatible(DataClass compClass, boolean or) {
        return compClass instanceof HTMLTextClass ? this : super.getCompatible(compClass, or);
    }

    @Override
    public boolean useInputTag(boolean isPanel, boolean useBootstrap, Type changeType) {
        return false;
    }

    @Override
    public boolean markupHtml() {
        return true;
    }
}
