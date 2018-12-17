package lsfusion.gwt.server.form;

import lsfusion.gwt.server.form.provider.FormSessionObject;
import lsfusion.gwt.server.LSFusionDispatchServlet;
import lsfusion.gwt.server.convert.ClientActionToGwtConverter;
import lsfusion.gwt.shared.actions.form.FormAction;
import lsfusion.gwt.shared.actions.form.ServerResponseResult;
import lsfusion.gwt.shared.view.actions.GAction;
import lsfusion.gwt.shared.view.actions.GThrowExceptionAction;
import lsfusion.interop.form.ServerResponse;

import java.io.IOException;

public abstract class FormServerResponseActionHandler<A extends FormAction<ServerResponseResult>> extends FormActionHandler<A, ServerResponseResult> {
    private static ClientActionToGwtConverter clientActionConverter = ClientActionToGwtConverter.getInstance();

    protected FormServerResponseActionHandler(LSFusionDispatchServlet servlet) {
        super(servlet);
    }

    protected ServerResponseResult getServerResponseResult(FormSessionObject form, ServerResponse serverResponse) throws IOException {
        return getServerResponseResult(form, serverResponse, servlet);
    }

    public static ServerResponseResult getServerResponseResult(FormSessionObject form, ServerResponse serverResponse, LSFusionDispatchServlet servlet) throws IOException {
        GAction[] resultActions;
        if (serverResponse.actions == null) {
            resultActions = null;
        } else {
            resultActions = new GAction[serverResponse.actions.length];
            for (int i = 0; i < serverResponse.actions.length; i++) {
                try {
                    resultActions[i] = clientActionConverter.convertAction(serverResponse.actions[i], form, servlet);
                } catch (Exception e) {
                    resultActions[i] = new GThrowExceptionAction(new IllegalStateException("Can't convert server action: " + e.getMessage(), e));
                }
            }
        }

        return new ServerResponseResult(resultActions, serverResponse.resumeInvocation);
    }

}