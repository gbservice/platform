package platform.gwt.form.server;

import net.customware.gwt.dispatch.server.InstanceActionHandlerRegistry;
import platform.gwt.base.server.LogicsDispatchServlet;
import platform.gwt.form.server.form.handlers.*;
import platform.gwt.form.server.navigator.handlers.GenerateIDHandler;
import platform.gwt.form.server.navigator.handlers.GetNavigatorInfoHandler;
import platform.interop.RemoteLogicsInterface;

public class RemoteServiceImpl extends LogicsDispatchServlet<RemoteLogicsInterface> {
    @Override
    protected void addHandlers(InstanceActionHandlerRegistry registry) {
        registry.addHandler(new GetNavigatorInfoHandler(this));
        registry.addHandler(new GenerateIDHandler(this));

        registry.addHandler(new ChangeClassViewHandler(this));
        registry.addHandler(new ChangePropertyOrderHandler(this));
        registry.addHandler(new ClearPropertyOrdersHandler(this));
        registry.addHandler(new ExpandGroupObjectHandler(this));
        registry.addHandler(new CollapseGroupObjectHandler(this));
        registry.addHandler(new GetFormHandler(this));
        registry.addHandler(new ChangeGroupObjectHandler(this));
        registry.addHandler(new ScrollToEndHandler(this));
        registry.addHandler(new GetRemoteChangesHandler(this));
        registry.addHandler(new SetRegularFilterHandler(this));
        registry.addHandler(new SetTabVisibleHandler(this));
        registry.addHandler(new ExecuteEditActionHandler(this));
        registry.addHandler(new ChangePropertyHandler(this));
        registry.addHandler(new ContinueInvocationHandler(this));
        registry.addHandler(new ThrowInInvocationHandler(this));
        registry.addHandler(new ClosePressedHandler(this));
    }

    public FormSessionManager getFormSessionManager() {
        return getSpringContext().getBean(FormSessionManager.class);
    }
}
