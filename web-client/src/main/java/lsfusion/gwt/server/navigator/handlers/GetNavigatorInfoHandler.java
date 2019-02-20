package lsfusion.gwt.server.navigator.handlers;

import com.google.common.base.Throwables;
import lsfusion.client.logics.DeSerializer;
import lsfusion.client.navigator.ClientNavigatorWindow;
import lsfusion.gwt.server.navigator.NavigatorActionHandler;
import lsfusion.gwt.server.MainDispatchServlet;
import lsfusion.gwt.server.convert.ClientNavigatorToGwtConverter;
import lsfusion.gwt.shared.actions.navigator.GetNavigatorInfo;
import lsfusion.gwt.shared.actions.navigator.GetNavigatorInfoResult;
import lsfusion.gwt.shared.view.GNavigatorElement;
import lsfusion.gwt.shared.view.window.GAbstractWindow;
import lsfusion.gwt.shared.view.window.GNavigatorWindow;
import lsfusion.interop.navigator.RemoteNavigatorInterface;
import net.customware.gwt.dispatch.server.ExecutionContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GetNavigatorInfoHandler extends NavigatorActionHandler<GetNavigatorInfo, GetNavigatorInfoResult> {

    public GetNavigatorInfoHandler(MainDispatchServlet servlet) {
        super(servlet);
    }

    @Override
    public GetNavigatorInfoResult executeEx(GetNavigatorInfo action, ExecutionContext context) {
        ClientNavigatorToGwtConverter converter = new ClientNavigatorToGwtConverter(getLogicsName(action));

        RemoteNavigatorInterface remoteNavigator = getRemoteNavigator(action);
        DeSerializer.NavigatorData navigatorData = null;
        try {
            navigatorData = DeSerializer.deserializeListClientNavigatorElementWithChildren(remoteNavigator.getNavigatorTree());
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }

        GNavigatorElement root = converter.convertOrCast(navigatorData.root);

        ArrayList<GNavigatorWindow> navigatorWindows = new ArrayList<>();
        for (ClientNavigatorWindow window : navigatorData.windows.values()) {
            GNavigatorWindow gWindow = converter.convertOrCast(window);
            navigatorWindows.add(gWindow);
        }

        //getting common windows
        List<GAbstractWindow> windows = new ArrayList<>();
        windows.add((GAbstractWindow) converter.convertOrCast(navigatorData.logs));
        windows.add((GAbstractWindow) converter.convertOrCast(navigatorData.status));
        windows.add((GAbstractWindow) converter.convertOrCast(navigatorData.forms));

        return new GetNavigatorInfoResult(root, navigatorWindows, windows);
    }
}
