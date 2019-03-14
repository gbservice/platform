package lsfusion.server.physics.admin.authentication;

import lsfusion.base.BaseUtils;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.language.ScriptingAction;
import lsfusion.server.logics.action.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;

import java.sql.SQLException;
import java.util.Iterator;

public class CalculateBase64HashActionProperty extends ScriptingAction {
    AuthenticationLogicsModule LM;
    private final ClassPropertyInterface algorithmInterface;
    private final ClassPropertyInterface passwordInterface;

    public CalculateBase64HashActionProperty(AuthenticationLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
        this.LM = LM;
        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        algorithmInterface = i.next();
        passwordInterface = i.next();
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        String algorithm = String.valueOf(context.getDataKeyValue(algorithmInterface).getValue());
        String password = String.valueOf(context.getDataKeyValue(passwordInterface).getValue()).trim();

        LM.calculatedHash.change(BaseUtils.calculateBase64Hash(algorithm, password, UserInfo.salt), context.getSession());
    }
}