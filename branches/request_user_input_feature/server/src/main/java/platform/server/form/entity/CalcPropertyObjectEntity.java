package platform.server.form.entity;

import platform.server.form.instance.CalcPropertyObjectInstance;
import platform.server.form.instance.InstanceFactory;
import platform.server.logics.property.CalcProperty;
import platform.server.logics.property.PropertyInterface;
import platform.server.session.DataSession;
import platform.server.session.Modifier;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class CalcPropertyObjectEntity<P extends PropertyInterface> extends PropertyObjectEntity<P, CalcProperty<P>> implements OrderEntity<CalcPropertyObjectInstance<P>> {

    public CalcPropertyObjectEntity(CalcProperty<P> property, Map<P, PropertyObjectInterfaceEntity> mapping, String creationScript) {
        super(property, mapping, creationScript);
    }

    @Override
    public CalcPropertyObjectInstance<P> getInstance(InstanceFactory instanceFactory) {
        return instanceFactory.getInstance(this);
    }

    public CalcPropertyObjectEntity<P> getRemappedEntity(ObjectEntity oldObject, ObjectEntity newObject, InstanceFactory instanceFactory) {
        Map<P, PropertyObjectInterfaceEntity> nmapping = new HashMap<P, PropertyObjectInterfaceEntity>();    
        for (P iFace : property.interfaces) {
            nmapping.put(iFace, mapping.get(iFace).getRemappedEntity(oldObject, newObject, instanceFactory));
        }
        return new CalcPropertyObjectEntity<P>(property, nmapping, creationScript);
    }

    @Override
    public Object getValue(InstanceFactory factory, DataSession session, Modifier modifier) throws SQLException {
        return factory.getInstance(this).read(session, modifier);
    }
}
