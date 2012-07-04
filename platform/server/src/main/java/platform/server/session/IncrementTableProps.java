package platform.server.session;

import platform.base.QuickSet;
import platform.server.data.SQLSession;
import platform.server.logics.property.CalcProperty;
import platform.server.logics.property.PropertyInterface;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class IncrementTableProps extends IncrementProps {

    private final TableProps tableProps = new TableProps();

    public IncrementTableProps() {
    }
    
    public boolean isEmpty() {
        return getProperties().isEmpty();
    }

    public <P extends PropertyInterface> IncrementTableProps(CalcProperty<P> property, SinglePropertyTableUsage<P> table) {
        add(property, table);
    }

    public QuickSet<CalcProperty> getProperties() {
        return tableProps.getProperties();
    }

    public boolean contains(CalcProperty property) {
        return tableProps.contains(property);
    }

    public <P extends PropertyInterface> SinglePropertyTableUsage<P> getTable(CalcProperty<P> property) {
        return tableProps.getTable(property);
    }

    public <P extends PropertyInterface> PropertyChange<P> getPropertyChange(CalcProperty<P> property) {
        return tableProps.getPropertyChange(property);
    }

    public <P extends PropertyInterface> void add(CalcProperty<P> property, SinglePropertyTableUsage<P> changeTable) {
        tableProps.add(property, changeTable);

        eventChange(property);
    }

    public void clear(SQLSession session) throws SQLException {
        eventChanges(tableProps.getProperties());

        tableProps.clear(session);
    }
}
