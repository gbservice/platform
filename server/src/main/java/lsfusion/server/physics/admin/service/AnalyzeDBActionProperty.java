package lsfusion.server.physics.admin.service;

import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.base.context.ThreadLocalContext;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.language.ScriptingAction;
import lsfusion.server.logics.action.ExecutionContext;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;

import java.sql.SQLException;

public class AnalyzeDBActionProperty extends ScriptingAction {
    public AnalyzeDBActionProperty(ServiceLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        context.getDbManager().analyzeDB(context.getSession().sql);
        context.delayUserInterfaction(new MessageClientAction(ThreadLocalContext.localize("{logics.vacuum.analyze.was.completed}"), ThreadLocalContext.localize("{logics.vacuum.analyze}")));
    }
}