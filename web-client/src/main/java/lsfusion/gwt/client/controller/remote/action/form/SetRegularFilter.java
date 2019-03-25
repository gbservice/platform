package lsfusion.gwt.client.controller.remote.action.form;

public class SetRegularFilter extends FormRequestIndexCountingAction<ServerResponseResult> {
    public int groupId;
    public int filterId;

    public SetRegularFilter() {
    }

    public SetRegularFilter(int groupId, int filterId) {
        this.groupId = groupId;
        this.filterId = filterId;
    }
}