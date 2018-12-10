package lsfusion.gwt.shared.view.actions;

import lsfusion.gwt.shared.view.classes.GType;

import java.io.Serializable;

public class GRequestUserInputAction implements GAction {
    public GType readType;
    public Serializable oldValue;

    //needed for it to be gwt-serializable
    @SuppressWarnings("UnusedDeclaration")
    public GRequestUserInputAction() {}

    public GRequestUserInputAction(GType readType, Object oldValue) {
        this.readType = readType;
        this.oldValue = (Serializable) oldValue;
    }

    @Override
    public Object dispatch(GActionDispatcher dispatcher) throws Throwable {
        return dispatcher.execute(this);
    }
}
