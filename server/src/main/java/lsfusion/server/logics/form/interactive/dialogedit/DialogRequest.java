package lsfusion.server.logics.form.interactive.dialogedit;

import lsfusion.server.data.ObjectValue;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.form.interactive.instance.FormInstance;

import java.sql.SQLException;

public interface DialogRequest {

    FormInstance createDialog() throws SQLException, SQLHandledException;

    ObjectValue getValue();
}