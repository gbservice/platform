package lsfusion.gwt.shared.actions.form;

import lsfusion.gwt.shared.view.GOrder;
import lsfusion.gwt.shared.view.changes.GGroupObjectValue;

public class ChangePropertyOrder extends FormRequestIndexCountingAction<ServerResponseResult> {
    public int propertyID;
    public GGroupObjectValue columnKey;
    public GOrder modiType;

    public ChangePropertyOrder() {}

    public ChangePropertyOrder(int propertyID, GGroupObjectValue columnKey, GOrder modiType) {
        this.propertyID = propertyID;
        this.columnKey = columnKey;
        this.modiType = modiType;
    }

}
