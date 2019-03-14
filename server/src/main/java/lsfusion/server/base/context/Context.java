package lsfusion.server.base.context;

import lsfusion.base.col.interfaces.immutable.ImList;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.interop.action.ClientAction;
import lsfusion.interop.form.ModalityType;
import lsfusion.server.data.ObjectValue;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.LogicsInstance;
import lsfusion.server.logics.action.session.DataSession;
import lsfusion.server.logics.classes.CustomClass;
import lsfusion.server.logics.classes.DataClass;
import lsfusion.server.logics.form.interactive.ManageSessionType;
import lsfusion.server.logics.form.interactive.dialogedit.DialogRequest;
import lsfusion.server.logics.form.interactive.instance.FormInstance;
import lsfusion.server.logics.form.interactive.listener.CustomClassListener;
import lsfusion.server.logics.form.interactive.listener.FocusListener;
import lsfusion.server.logics.form.struct.FormEntity;
import lsfusion.server.logics.form.struct.filter.ContextFilter;
import lsfusion.server.logics.form.struct.object.ObjectEntity;
import lsfusion.server.logics.navigator.LogInfo;
import lsfusion.server.logics.property.derived.PullChangeProperty;
import lsfusion.server.physics.dev.i18n.LocalizedString;
import lsfusion.server.remote.RemoteForm;

import java.sql.SQLException;
import java.util.Locale;

public interface Context {

    LogicsInstance getLogicsInstance();

    FormInstance getFormInstance();

    FormInstance createFormInstance(FormEntity formEntity, ImMap<ObjectEntity, ? extends ObjectValue> mapObjects, DataSession session, boolean isModal, Boolean noCancel, ManageSessionType manageSession, ExecutionStack stack, boolean checkOnOk, boolean showDrop, boolean interactive, ImSet<ContextFilter> contextFilters, ImSet<PullChangeProperty> pullProps, boolean readonly) throws SQLException, SQLHandledException;
    RemoteForm createRemoteForm(FormInstance formInstance, ExecutionStack stack);

    void requestFormUserInteraction(FormInstance formInstance, ModalityType modalityType, boolean forbidDuplicate, ExecutionStack stack) throws SQLException, SQLHandledException;
    
    ObjectValue requestUserObject(DialogRequest dialogRequest, ExecutionStack stack) throws SQLException, SQLHandledException;
    ObjectValue requestUserData(DataClass dataClass, Object oldValue);
    ObjectValue requestUserClass(CustomClass baseClass, CustomClass defaultValue, boolean concrete);

    void pushLogMessage();
    ImList<AbstractContext.LogMessage> popLogMessage();
    LogInfo getLogInfo();
    void delayUserInteraction(ClientAction action);
    Object requestUserInteraction(ClientAction action);
    boolean canBeProcessed();
    Object[] requestUserInteraction(ClientAction... actions);

    // для создания форм
    FocusListener getFocusListener();
    CustomClassListener getClassListener();
    Long getCurrentComputer();
    Long getCurrentUser();
    Long getCurrentUserRole();

    String localize(LocalizedString s);
    String localize(LocalizedString s, Locale locale);
    Locale getLocale();
}