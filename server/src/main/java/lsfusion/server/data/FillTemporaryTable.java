package lsfusion.server.data;

import java.sql.SQLException;

public interface FillTemporaryTable {

    Integer fill(String name) throws SQLException, SQLHandledException;
}
