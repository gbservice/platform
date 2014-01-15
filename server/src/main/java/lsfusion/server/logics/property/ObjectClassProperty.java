package lsfusion.server.logics.property;

import lsfusion.base.col.MapFact;
import lsfusion.base.col.SetFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.interop.ClassViewType;
import lsfusion.server.caches.IdentityInstanceLazy;
import lsfusion.server.classes.BaseClass;
import lsfusion.server.classes.CustomClass;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.Expr;
import lsfusion.server.data.where.WhereBuilder;
import lsfusion.server.form.entity.FormEntity;
import lsfusion.server.form.entity.ObjectEntity;
import lsfusion.server.form.entity.PropertyDrawEntity;
import lsfusion.server.form.entity.PropertyObjectInterfaceEntity;
import lsfusion.server.logics.ServerResourceBundle;
import lsfusion.server.logics.property.actions.ChangeClassActionProperty;
import lsfusion.server.session.Modifier;
import lsfusion.server.session.PropertyChanges;

import java.sql.SQLException;

public class ObjectClassProperty extends AggregateProperty<ClassPropertyInterface> {

    private final BaseClass baseClass;

    public ObjectClassProperty(String SID, BaseClass baseClass) {
        super(SID, ServerResourceBundle.getString("classes.object.class"), IsClassProperty.getInterfaces(new ValueClass[]{baseClass}));

        this.baseClass = baseClass;

        finalizeInit();
    }

    protected Expr calculateExpr(ImMap<ClassPropertyInterface, ? extends Expr> joinImplement, CalcType calcType, PropertyChanges propChanges, WhereBuilder changedWhere) {
        return joinImplement.singleValue().classExpr(baseClass);
    }
    
    public Expr getExpr(Expr expr, Modifier modifier) throws SQLException, SQLHandledException {
        return getExpr(MapFact.singleton(getInterface(), expr), modifier);
    }

    @Override
    protected boolean useSimpleIncrement() {
        return true;
    }

    private ClassPropertyInterface getInterface() {
        return interfaces.single();
    }

    @Override
    @IdentityInstanceLazy
    public ActionPropertyMapImplement<?, ClassPropertyInterface> getDefaultEditAction(String editActionSID, CalcProperty filterProperty) {
        return ChangeClassActionProperty.create(null, false, baseClass).getImplement(SetFact.singletonOrder(getInterface()));
    }

    @Override
    public void proceedDefaultDraw(PropertyDrawEntity<ClassPropertyInterface> entity, FormEntity<?> form) {
        super.proceedDefaultDraw(entity, form);
        PropertyObjectInterfaceEntity mapObject = entity.propertyObject.mapping.singleValue();
        if(mapObject instanceof ObjectEntity && !((CustomClass)((ObjectEntity)mapObject).baseClass).hasChildren())
            entity.forceViewType = ClassViewType.HIDE;
    }
}
