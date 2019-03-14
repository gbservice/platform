package lsfusion.server.logics.constraint;

import lsfusion.base.Pair;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.server.data.ObjectValue;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.BaseLogicsModule;
import lsfusion.server.logics.action.session.DataSession;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.form.open.FormSelector;
import lsfusion.server.logics.form.open.ObjectSelector;
import lsfusion.server.logics.form.struct.FormEntity;
import lsfusion.server.logics.form.struct.object.ObjectEntity;
import lsfusion.server.logics.property.Property;
import lsfusion.server.logics.property.oraction.PropertyInterface;

import java.sql.SQLException;

public class OutFormSelector<P extends PropertyInterface> implements FormSelector<ObjectSelector> {

    private final Property<P> property;
    private final Property messageProperty;
    
    public OutFormSelector(Property<P> property, Property messageProperty) {
        this.property = property;
        this.messageProperty = messageProperty;
    }

    @Override
    public ValueClass getBaseClass(ObjectSelector object) {
        throw new UnsupportedOperationException();
    }

    @Override
    public FormEntity getStaticForm() {
        return null;
    }

    @Override
    public Pair<FormEntity, ImRevMap<ObjectEntity, ObjectSelector>> getForm(BaseLogicsModule LM, DataSession session, ImMap<ObjectSelector, ? extends ObjectValue> mapObjectValues) throws SQLException, SQLHandledException {
        return new Pair<>((FormEntity)LM.getLogForm(property, messageProperty), MapFact.<ObjectEntity, ObjectSelector>EMPTYREV());
    }
}