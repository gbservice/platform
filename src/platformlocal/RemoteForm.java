/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package platformlocal;

import java.sql.SQLException;
import java.util.Map.Entry;
import java.util.*;
import java.io.Serializable;

// здесь многие подходы для оптимизации неструктурные, то есть можно было структурно все обновлять но это очень медленно

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;
import net.sf.jasperreports.engine.design.JasperDesign;

import javax.swing.*;


// на самом деле нужен collection но при extend'е нужна конкретная реализация
class ObjectImplement {

    ObjectImplement(int iID, Class iBaseClass, String iCaption, GroupObjectImplement groupObject) {
        this(iID, iBaseClass, iCaption);

        groupObject.addObject(this);
    }

    ObjectImplement(int iID, Class iBaseClass, String iCaption) {
        ID = iID;
        BaseClass = iBaseClass;
        GridClass = BaseClass;
        caption = iCaption;
    }

    ObjectImplement(int iID, Class iBaseClass) {
        this(iID, iBaseClass, "");
    }

    // выбранный объект, класс выбранного объекта
    Integer idObject = null;
    Class Class = null;

    Class BaseClass;
    // выбранный класс
    Class GridClass;

    // 0 !!! - изменился объект, 1 !!! - класс объекта, 3 !!! - класса, 4 - классовый вид

    static int UPDATED_OBJECT = (1 << 0);
    static int UPDATED_CLASS = (1 << 1);
    static int UPDATED_GRIDCLASS = (1 << 3);

    int Updated = UPDATED_GRIDCLASS;

    GroupObjectImplement GroupTo;

    String caption = "";

    public String toString() {
        return caption;
    }

    // идентификатор (в рамках формы)
    int ID = 0;

    // символьный идентификатор, нужен для обращению к свойствам в печатных формах
    String sID;
    public String getSID() {
        if (sID != null) return sID; else return "obj" + ID;
    }

    SourceExpr getSourceExpr(Set<GroupObjectImplement> ClassGroup, Map<ObjectImplement, ? extends SourceExpr> ClassSource) {
        return (ClassGroup!=null && ClassGroup.contains(GroupTo)?ClassSource.get(this):new ValueExpr(idObject,Type.Object));
    }
}

class GroupObjectMap<T> extends LinkedHashMap<ObjectImplement,T> {

}

class GroupObjectValue extends GroupObjectMap<Integer> {

    GroupObjectValue() {}
    GroupObjectValue(Map<ObjectImplement,Integer> iValue) {
        putAll(iValue);
    }
}

class GroupObjectImplement extends ArrayList<ObjectImplement> {

    // глобальный идентификатор чтобы писать во ViewTable
    public final int ID;
    GroupObjectImplement(int iID) {

        if (iID >= RemoteForm.GID_SHIFT)
            throw new RuntimeException("sID must be less than " + RemoteForm.GID_SHIFT);

        ID = iID;
    }

    public void addObject(ObjectImplement object) {
        add(object);
        object.GroupTo = this;
    }

    Integer Order = 0;

    // классовый вид включен или нет
    Boolean gridClassView = true;
    Boolean singleViewType = false;

    // закэшированные

    // вообще все фильтры
    Set<Filter> MapFilters = new HashSet();
    List<PropertyView> MapOrders = new ArrayList();

    // с активным интерфейсом
    Set<Filter> Filters = new HashSet();
    LinkedHashMap<PropertyObjectImplement,Boolean> Orders = new LinkedHashMap();

    boolean UpKeys, DownKeys;
    List<GroupObjectValue> Keys = null;
    // какие ключи активны
    Map<GroupObjectValue,Map<PropertyObjectImplement,Object>> KeyOrders = null;

    // 0 !!! - изменился объект, 1 - класс объекта, 2 !!! - отбор, 3 !!! - хоть один класс, 4 !!! - классовый вид

    static int UPDATED_OBJECT = (1 << 0);
    static int UPDATED_KEYS = (1 << 2);
    static int UPDATED_GRIDCLASS = (1 << 3);
    static int UPDATED_CLASSVIEW = (1 << 4);

    int Updated = UPDATED_GRIDCLASS | UPDATED_CLASSVIEW;

    int PageSize = 12;

    GroupObjectValue GetObjectValue() {
        GroupObjectValue Result = new GroupObjectValue();
        for(ObjectImplement Object : this)
            Result.put(Object,Object.idObject);

        return Result;
    }

    // получает Set группы
    Set<GroupObjectImplement> GetClassGroup() {

        Set<GroupObjectImplement> Result = new HashSet();
        Result.add(this);
        return Result;
    }

    void fillSourceSelect(JoinQuery<ObjectImplement, ?> Query, Set<GroupObjectImplement> ClassGroup, TableFactory TableFactory, DataSession Session) {

        // фильтры первыми потому как ограничивают ключи
        for(Filter Filt : Filters) Filt.fillSelect(Query,ClassGroup,Session);

        // докинем Join ко всем классам, те которых не было FULL JOIN'ом остальные Join'ом
        for(ObjectImplement Object : this) {

            if (Object.BaseClass instanceof IntegralClass) continue;

            // не было в фильтре
            // если есть remove'классы или новые объекты их надо докинуть
            JoinQuery<KeyField,PropertyField> ObjectQuery = TableFactory.ObjectTable.getClassJoin(Object.GridClass);
            if(Session!=null && Session.Changes.AddClasses.contains(Object.GridClass)) {
                // придется UnionQuery делать, ObjectTable'а Key и AddClass Object'а
                UnionQuery<KeyField,PropertyField> ResultQuery = new UnionQuery<KeyField,PropertyField>(ObjectQuery.Keys,2);

                ResultQuery.add(ObjectQuery,1);

                // придется создавать запрос чтобы ключи перекодировать
                JoinQuery<KeyField,PropertyField> AddQuery = new JoinQuery<KeyField, PropertyField>(ObjectQuery.Keys);
                Join<KeyField,PropertyField> AddJoin = new Join<KeyField,PropertyField>(TableFactory.AddClassTable.getClassJoin(Session,Object.GridClass));
                AddJoin.Joins.put(TableFactory.AddClassTable.Object,AddQuery.MapKeys.get(TableFactory.ObjectTable.Key));
                AddQuery.and(AddJoin.InJoin);
                ResultQuery.add(AddQuery,1);

                ObjectQuery = ResultQuery;
            }

            Join<KeyField,PropertyField> ObjectJoin = new Join<KeyField,PropertyField>(ObjectQuery);
            ObjectJoin.Joins.put(TableFactory.ObjectTable.Key,Query.MapKeys.get(Object));
            Query.and(ObjectJoin.InJoin);

            if(Session!=null && Session.Changes.RemoveClasses.contains(Object.GridClass))
                TableFactory.RemoveClassTable.excludeJoin(Query,Session,Object.GridClass,Query.MapKeys.get(Object));
        }
    }
}

class PropertyObjectImplement<P extends PropertyInterface> extends PropertyImplement<ObjectImplement,P> {

    PropertyObjectImplement(PropertyObjectImplement<P> iProperty) { super(iProperty); }
    PropertyObjectImplement(Property<P> iProperty) {super(iProperty);}

    // получает Grid в котором рисоваться
    GroupObjectImplement GetApplyObject() {
        GroupObjectImplement ApplyObject=null;
        for(ObjectImplement IntObject : Mapping.values())
            if(ApplyObject==null || IntObject.GroupTo.Order>ApplyObject.Order) ApplyObject = IntObject.GroupTo;

        return ApplyObject;
    }

    // получает класс значения
    ClassSet getValueClass(GroupObjectImplement ClassGroup) {
        InterfaceClass<P> ClassImplement = new InterfaceClass<P>();
        for(P Interface : Property.Interfaces) {
            ObjectImplement IntObject = Mapping.get(Interface);
            ClassSet ImpClass;
            if(IntObject.GroupTo==ClassGroup)
                if(IntObject.GridClass==null)
                    throw new RuntimeException("надо еще думать");
                else
                    ImpClass = new ClassSet(IntObject.GridClass);//ClassSet.getUp(IntObject.GridClass);
            else
                if(IntObject.Class==null)
                    return new ClassSet();
                else
                    ImpClass = new ClassSet(IntObject.Class);
            ClassImplement.put(Interface,ImpClass);
        }

        return Property.getValueClass(ClassImplement);
    }

    // в интерфейсе
    boolean isInInterface(GroupObjectImplement ClassGroup) {
        return !getValueClass(ClassGroup).isEmpty();
    }

    // проверяет на то что изменился верхний объект
    boolean ObjectUpdated(GroupObjectImplement ClassGroup) {
        for(ObjectImplement IntObject : Mapping.values())
            if(IntObject.GroupTo!=ClassGroup && ((IntObject.Updated & ObjectImplement.UPDATED_OBJECT)!=0)) return true;

        return false;
    }

    // изменился хоть один из классов интерфейса (могло повлиять на вхождение в интерфейс)
    boolean ClassUpdated(GroupObjectImplement ClassGroup) {
        for(ObjectImplement IntObject : Mapping.values())
            if(((IntObject.Updated & ((IntObject.GroupTo==ClassGroup)?ObjectImplement.UPDATED_CLASS:ObjectImplement.UPDATED_CLASS)))!=0) return true;

        return false;
    }

    ChangeValue getChangeProperty(DataSession Session, ChangePropertySecurityPolicy securityPolicy) {
        Map<P,ObjectValue> Interface = new HashMap<P,ObjectValue>();
        for(Entry<P, ObjectImplement> Implement : Mapping.entrySet())
            Interface.put(Implement.getKey(),new ObjectValue(Implement.getValue().idObject,Implement.getValue().Class));

        return Property.getChangeProperty(Session,Interface,1,securityPolicy);
    }

    SourceExpr getSourceExpr(Set<GroupObjectImplement> ClassGroup, Map<ObjectImplement, ? extends SourceExpr> ClassSource, DataSession Session) {

        Map<P,SourceExpr> JoinImplement = new HashMap<P,SourceExpr>();
        for(P Interface : Property.Interfaces)
            JoinImplement.put(Interface,Mapping.get(Interface).getSourceExpr(ClassGroup,ClassSource));

        InterfaceClass<P> JoinClasses = new InterfaceClass<P>();
        for(Entry<P, ObjectImplement> Implement : Mapping.entrySet()) {
            ClassSet Classes;
            if(ClassGroup!=null && ClassGroup.contains(Implement.getValue().GroupTo)) {
                Class ImplementClass = Implement.getValue().GridClass;
                Classes = ClassSet.getUp(ImplementClass);
                ClassSet AddClasses = Session.AddChanges.get(ImplementClass);
                if(AddClasses!=null)
                    Classes.or(AddClasses);
            } else {
                Class ImplementClass = Session.BaseClasses.get(Implement.getValue().idObject);
                if(ImplementClass==null) ImplementClass = Implement.getValue().Class;
                // чего не должно быть
                if(ImplementClass==null)
                    Classes = new ClassSet();
                else
                    Classes = new ClassSet(ImplementClass);
            }
            JoinClasses.put(Implement.getKey(),Classes);
        }

        // если есть не все интерфейсы и есть изменения надо с Full Join'ить старое с новым
        // иначе как и было
        return Session.getSourceExpr(Property,JoinImplement,new InterfaceClassSet<P>(JoinClasses));
    }
}

// представление св-ва
class PropertyView<P extends PropertyInterface> {
    PropertyObjectImplement<P> View;

    // в какой "класс" рисоваться, ессно одмн из Object.GroupTo должен быть ToDraw
    GroupObjectImplement ToDraw;

    PropertyView(int iID,PropertyObjectImplement<P> iView,GroupObjectImplement iToDraw) {
        View = iView;
        ToDraw = iToDraw;
        ID = iID;
    }

    public PropertyView(PropertyView<P> navigatorProperty) {

        ID = navigatorProperty.ID;
        View = navigatorProperty.View;
        ToDraw = navigatorProperty.ToDraw;
    }

    public String toString() {
        return View.toString();
    }

    // идентификатор (в рамках формы)
    int ID = 0;

    // символьный идентификатор, нужен для обращению к свойствам в печатных формах
    String sID;
    public String getSID() {
        if (sID != null) return sID; else return "prop" + ID;
    }
}

class AbstractFormChanges<T,V,Z> {

    Map<T,Boolean> ClassViews = new HashMap();
    Map<T,V> Objects = new HashMap();
    Map<T,List<V>> GridObjects = new HashMap();
    Map<Z,Map<V,Object>> GridProperties = new HashMap();
    Map<Z,Object> PanelProperties = new HashMap();
    Set<Z> DropProperties = new HashSet();
}

// класс в котором лежит какие изменения произошли
// появляется по сути для отделения клиента, именно он возвращается назад клиенту
class FormChanges extends AbstractFormChanges<GroupObjectImplement,GroupObjectValue,PropertyView> {

    void Out(RemoteForm bv) {
        System.out.println(" ------- GROUPOBJECTS ---------------");
        for(GroupObjectImplement Group : (List<GroupObjectImplement>)bv.Groups) {
            List<GroupObjectValue> GroupGridObjects = GridObjects.get(Group);
            if(GroupGridObjects!=null) {
                System.out.println(Group.ID +" - Grid Changes");
                for(GroupObjectValue Value : GroupGridObjects)
                    System.out.println(Value);
            }

            GroupObjectValue Value = Objects.get(Group);
            if(Value!=null)
                System.out.println(Group.ID +" - Object Changes "+Value);
        }

        System.out.println(" ------- PROPERTIES ---------------");
        System.out.println(" ------- Group ---------------");
        for(PropertyView Property : GridProperties.keySet()) {
            Map<GroupObjectValue,Object> PropertyValues = GridProperties.get(Property);
            System.out.println(Property+" ---- property");
            for(GroupObjectValue gov : PropertyValues.keySet())
                System.out.println(gov+" - "+PropertyValues.get(gov));
        }

        System.out.println(" ------- Panel ---------------");
        for(PropertyView Property : PanelProperties.keySet())
            System.out.println(Property+" - "+PanelProperties.get(Property));

        System.out.println(" ------- Drop ---------------");
        for(PropertyView Property : DropProperties)
            System.out.println(Property);
    }
}


class Filter<P extends PropertyInterface> {

    static int EQUALS = CompareWhere.EQUALS;
    static int GREATER = CompareWhere.GREATER;
    static int LESS = CompareWhere.LESS;
    static int GREATER_EQUALS = CompareWhere.GREATER_EQUALS;
    static int LESS_EQUALS = CompareWhere.LESS_EQUALS;
    static int NOT_EQUALS = CompareWhere.NOT_EQUALS;

    PropertyObjectImplement<P> Property;
    ValueLink Value;
    int Compare;

    Filter(PropertyObjectImplement<P> iProperty,int iCompare,ValueLink iValue) {
        Property=iProperty;
        Compare = iCompare;
        Value = iValue;
    }


    GroupObjectImplement GetApplyObject() {
        return Property.GetApplyObject();
    }

    boolean DataUpdated(Collection<Property> ChangedProps) {
        return ChangedProps.contains(Property.Property);
    }

    boolean IsInInterface(GroupObjectImplement ClassGroup) {
        ClassSet ValueClass = Value.getValueClass(ClassGroup);
        if(ValueClass==null)
            return Property.isInInterface(ClassGroup);
        else
            return Property.getValueClass(ClassGroup).intersect(ValueClass);
    }

    boolean ClassUpdated(GroupObjectImplement ClassGroup) {
        return Property.ClassUpdated(ClassGroup) || Value.ClassUpdated(ClassGroup);
    }

    boolean ObjectUpdated(GroupObjectImplement ClassGroup) {
        return Property.ObjectUpdated(ClassGroup) || Value.ObjectUpdated(ClassGroup);
    }

    void fillSelect(JoinQuery<ObjectImplement, ?> Query, Set<GroupObjectImplement> ClassGroup, DataSession Session) {
        Query.and(new CompareWhere(Property.getSourceExpr(ClassGroup,Query.MapKeys,Session),Value.getValueExpr(ClassGroup,Query.MapKeys,Session,Property.Property.getType()),Compare));
    }

    public Collection<? extends Property> getProperties() {
        Collection<Property<P>> Result = Collections.singletonList(Property.Property);
        if(Value instanceof PropertyValueLink)
            Result.add(((PropertyValueLink)Value).Property.Property);
        return Result;
    }
}

abstract class ValueLink {

    ClassSet getValueClass(GroupObjectImplement ClassGroup) {return null;}

    boolean ClassUpdated(GroupObjectImplement ClassGroup) {return false;}

    boolean ObjectUpdated(GroupObjectImplement ClassGroup) {return false;}

    abstract SourceExpr getValueExpr(Set<GroupObjectImplement> ClassGroup, Map<ObjectImplement, ? extends SourceExpr> ClassSource, DataSession Session, Type DBType);
}


class UserValueLink extends ValueLink {

    Object Value;

    UserValueLink(Object iValue) {Value=iValue;}

    SourceExpr getValueExpr(Set<GroupObjectImplement> ClassGroup, Map<ObjectImplement, ? extends SourceExpr> ClassSource, DataSession Session, Type DBType) {
        return new ValueExpr(Value,DBType);
    }
}

class ObjectValueLink extends ValueLink {

    ObjectValueLink(ObjectImplement iObject) {Object=iObject;}

    ObjectImplement Object;

    @Override
    ClassSet getValueClass(GroupObjectImplement ClassGroup) {
        if(Object.Class==null)
            return new ClassSet();
        else
            return new ClassSet(Object.Class);
    }

    @Override
    boolean ClassUpdated(GroupObjectImplement ClassGroup) {
        return ((Object.Updated & ObjectImplement.UPDATED_CLASS)!=0);
    }

    @Override
    boolean ObjectUpdated(GroupObjectImplement ClassGroup) {
        return ((Object.Updated & ObjectImplement.UPDATED_OBJECT)!=0);
    }

    SourceExpr getValueExpr(Set<GroupObjectImplement> ClassGroup, Map<ObjectImplement, ? extends SourceExpr> ClassSource, DataSession Session, Type DBType) {
        return Object.getSourceExpr(ClassGroup,ClassSource);
    }
}

class RegularFilter implements Serializable {

    int ID;
    transient Filter filter;
    String name = "";
    KeyStroke key;

    RegularFilter(int iID, Filter ifilter, String iname, KeyStroke ikey) {
        ID = iID;
        filter = ifilter;
        name = iname;
        key = ikey;
    }

    public String toString() {
        return name;
    }
}

class RegularFilterGroup implements Serializable {

    int ID;
    RegularFilterGroup(int iID) {
        ID = iID;
    }

    List<RegularFilter> filters = new ArrayList();
    void addFilter(RegularFilter filter) {
        filters.add(filter);
    }

    RegularFilter getFilter(int filterID) {
        for (RegularFilter filter : filters)
            if (filter.ID == filterID)
                return filter;
        return null;
    }
}

class PropertyValueLink extends ValueLink {

    PropertyValueLink(PropertyObjectImplement iProperty) {Property=iProperty;}

    PropertyObjectImplement Property;

    @Override
    ClassSet getValueClass(GroupObjectImplement ClassGroup) {
        return Property.getValueClass(ClassGroup);
    }

    @Override
    boolean ClassUpdated(GroupObjectImplement ClassGroup) {
        return Property.ClassUpdated(ClassGroup);
    }

    @Override
    boolean ObjectUpdated(GroupObjectImplement ClassGroup) {
        return Property.ObjectUpdated(ClassGroup);
    }

    SourceExpr getValueExpr(Set<GroupObjectImplement> ClassGroup, Map<ObjectImplement, ? extends SourceExpr> ClassSource, DataSession Session, Type DBType) {
        return Property.getSourceExpr(ClassGroup,ClassSource,Session);
    }
}


// нужен какой-то объект который разделит клиента и серверную часть кинув каждому свои данные
// так клиента волнуют панели на форме, список гридов в привязке, дизайн и порядок представлений
// сервера колышет дерево и св-ва предст. с привязкой к объектам

class RemoteForm<T extends BusinessLogics<T>> implements PropertyUpdateView {

    public static int GID_SHIFT = 1000;

    // используется для записи в сессии изменений в базу - требуется глобально уникальный идентификатор
    private final int GID;
    public int getGID() { return GID; }

    private int getGroupObjectGID(GroupObjectImplement group) { return GID * GID_SHIFT + group.ID; }

    private final int ID;
    int getID() { return ID; }

    T BL;

    DataSession Session;

    SecurityPolicy securityPolicy;

    RemoteForm(int iID, T iBL, DataSession iSession, SecurityPolicy isecurityPolicy) throws SQLException {

        ID = iID;
        BL = iBL;
        Session = iSession;
        securityPolicy = isecurityPolicy;

        StructUpdated = true;

        GID = BL.tableFactory.idTable.GenerateID(Session, IDTable.FORM);
    }

    List<GroupObjectImplement> Groups = new ArrayList();
    // собсно этот объект порядок колышет столько же сколько и дизайн представлений
    List<PropertyView> Properties = new ArrayList();

    // карта что сейчас в интерфейсе + карта в классовый\объектный вид
    Map<PropertyView,Boolean> InterfacePool = new HashMap();

    // --------------------------------------------------------------------------------------- //
    // --------------------- Фасад для работы с примитивными данными ------------------------- //
    // --------------------------------------------------------------------------------------- //

    // ----------------------------------- Инициализация ------------------------------------- //

    public byte[] getRichDesignByteArray() {
        return ByteArraySerializer.serializeClientFormView(GetRichDesign());
    }

    public byte[] getReportDesignByteArray() {
        return ByteArraySerializer.serializeReportDesign(GetReportDesign());
    }

    public byte[] getReportDataByteArray() throws SQLException {
        return ByteArraySerializer.serializeReportData(getReportData());
    }


    // ----------------------------------- Получение информации ------------------------------ //

    public int getObjectClassID(Integer objectID) {
        return getObjectImplement(objectID).Class.ID;
    }

    public byte[] getBaseClassByteArray(int objectID) {
        return ByteArraySerializer.serializeClass(getObjectImplement(objectID).BaseClass);
    }

    public byte[] getChildClassesByteArray(int objectID, int classID) {
        return ByteArraySerializer.serializeListClass(getObjectImplement(objectID).BaseClass.findClassID(classID).Childs);
    }

    public byte[] getPropertyEditorObjectValueByteArray(int propertyID, boolean externalID) {
        return ByteArraySerializer.serializeChangeValue(getPropertyEditorObjectValue(getPropertyView(propertyID), externalID));
    }

    // ----------------------------------- Навигация ----------------------------------------- //

    public void ChangeGroupObject(Integer groupID, int changeType) throws SQLException {
        ChangeGroupObject(getGroupObjectImplement(groupID), changeType);
    }

    public void ChangeGroupObject(Integer groupID, byte[] value) throws SQLException {
        GroupObjectImplement groupObject = getGroupObjectImplement(groupID);
        ChangeGroupObject(groupObject, ByteArraySerializer.deserializeGroupObjectValue(value, groupObject));
    }

    public void ChangeObject(Integer objectID, Integer value) throws SQLException {
        ChangeObject(getObjectImplement(objectID), value);
    }

    public void ChangeGridClass(int objectID,int idClass) throws SQLException {
        ChangeGridClass(getObjectImplement(objectID), idClass);
    }

    public void SwitchClassView(Integer groupID) throws SQLException {
        switchClassView(getGroupObjectImplement(groupID));
    }

    // Фильтры

    public void addFilter(byte[] state) {
        addUserFilter(ByteArraySerializer.deserializeFilter(state, this));
    }

    public void setRegularFilter(int groupID, int filterID) {

        RegularFilterGroup filterGroup = getRegularFilterGroup(groupID);
        setRegularFilter(filterGroup, filterGroup.getFilter(filterID));
    }

    // Порядки

    public void ChangeOrder(int propertyID, int modiType) {
        ChangeOrder(getPropertyView(propertyID), modiType);
    }

    // -------------------------------------- Изменение данных ----------------------------------- //

    public void AddObject(int objectID, int classID) throws SQLException {
        ObjectImplement object = getObjectImplement(objectID);
        AddObject(object, (classID == -1) ? null : object.BaseClass.findClassID(classID));
    }

    public void ChangeClass(int objectID, int classID) throws SQLException {

        ObjectImplement object = getObjectImplement(objectID);
        changeClass(object, (classID == -1) ? null : object.BaseClass.findClassID(classID));
    }

    public void ChangePropertyView(Integer propertyID, byte[] object, boolean externalID) throws SQLException {
        ChangePropertyView(getPropertyView(propertyID), ByteArraySerializer.deserializeObject(object), externalID);
    }

    // ----------------------- Применение изменений ------------------------------- //
    public void runEndApply() throws SQLException {
        endApply();
    }

    public byte[] getFormChangesByteArray() throws SQLException {
        return ByteArraySerializer.serializeFormChanges(endApply());
    }

    // --------------------------------------------------------------------------------------- //
    // ----------------------------------- Управляющий интерфейс ----------------------------- //
    // --------------------------------------------------------------------------------------- //

    // ----------------------------------- Поиск объектов по sID ------------------------------ //

    GroupObjectImplement getGroupObjectImplement(int groupID) {
        for (GroupObjectImplement groupObject : Groups)
            if (groupObject.ID == groupID)
                return groupObject;
        return null;
    }

    ObjectImplement getObjectImplement(int objectID) {
        for (GroupObjectImplement groupObject : Groups)
            for (ObjectImplement object : groupObject)
                if (object.ID == objectID)
                    return object;
        return null;
    }

    PropertyView getPropertyView(int propertyID) {
        for (PropertyView property : Properties)
            if (property.ID == propertyID)
                return property;
        return null;
    }

    private ChangeValue getPropertyEditorObjectValue(PropertyView propertyView, boolean externalID) {

        ChangeValue changeValue = propertyView.View.getChangeProperty(Session, securityPolicy.property.change);
        if (!externalID) return changeValue;

        if (changeValue == null) return null;
        DataProperty propertyID = changeValue.Class.getExternalID();
        if (propertyID == null) return null;

        return new ChangeObjectValue(propertyID.Value, null);
    }

    private RegularFilterGroup getRegularFilterGroup(int groupID) {
        for (RegularFilterGroup filterGroup : regularFilterGroups)
            if (filterGroup.ID == groupID)
                return filterGroup;
        return null;
    }

    // ----------------------------------- Инициализация ------------------------------------- //

    public ClientFormView richDesign;
    // возвращает клиентские настройки формы
    private ClientFormView GetRichDesign() {
        return richDesign;
    }

    public JasperDesign reportDesign;
    // возвращает структуру печатной формы
    public JasperDesign GetReportDesign() {
        return reportDesign;
    }

    // ----------------------------------- Навигация ----------------------------------------- //

    // поиски по свойствам\объектам
    public Map<PropertyObjectImplement,Object> UserPropertySeeks = new HashMap();
    public Map<ObjectImplement,Integer> UserObjectSeeks = new HashMap();

    public static int CHANGEGROUPOBJECT_FIRSTROW = 0;
    public static int CHANGEGROUPOBJECT_LASTROW = 1;

    private Map<GroupObjectImplement, Integer> pendingGroupChanges = new HashMap();

    public void ChangeGroupObject(GroupObjectImplement group, int changeType) throws SQLException {
        pendingGroupChanges.put(group, changeType);
    }

    private void ChangeGroupObject(GroupObjectImplement group,GroupObjectValue value) throws SQLException {
        // проставим все объектам метки изменений
        for(ObjectImplement object : group) {
            Integer idObject = value.get(object);
            if(object.idObject != idObject) {
                ChangeObject(object, idObject);
            }
        }
    }

    void ChangeObject(ObjectImplement object, Integer value) throws SQLException {

        if (object.idObject == value) return;

        object.idObject = value;

        // запишем класс объекта
        Class objectClass = null;
        if (value != null) {
            if(object.BaseClass instanceof ObjectClass)
                objectClass = Session.getObjectClass(value);
            else
                objectClass = object.BaseClass;
        }

        if(object.Class != objectClass) {

            object.Class = objectClass;

            object.Updated = object.Updated | ObjectImplement.UPDATED_CLASS;
        }

        object.Updated = object.Updated | ObjectImplement.UPDATED_OBJECT;
        object.GroupTo.Updated = object.GroupTo.Updated | GroupObjectImplement.UPDATED_OBJECT;

        // сообщаем всем, кто следит
        // если object.Class == null, то значит объект удалили
        if (object.Class != null)
            objectChanged(object.Class, value);
    }

    private void ChangeGridClass(ObjectImplement Object,Integer idClass) throws SQLException {

        Class GridClass = BL.objectClass.findClassID(idClass);
        if(Object.GridClass == GridClass) return;

        if(GridClass==null) throw new RuntimeException();
        Object.GridClass = GridClass;

        // расставляем пометки
        Object.Updated = Object.Updated | ObjectImplement.UPDATED_GRIDCLASS;
        Object.GroupTo.Updated = Object.GroupTo.Updated | GroupObjectImplement.UPDATED_GRIDCLASS;

    }

    private void switchClassView(GroupObjectImplement Group) {
        changeClassView(Group, !Group.gridClassView);
    }

    private void changeClassView(GroupObjectImplement Group,Boolean Show) {

        if(Group.gridClassView == Show || Group.singleViewType) return;
        Group.gridClassView = Show;

        // расставляем пометки
        Group.Updated = Group.Updated | GroupObjectImplement.UPDATED_CLASSVIEW;

        // на данный момент ClassView влияет на фильтры
        StructUpdated = true;
    }

    // Фильтры

    // флаги изменения фильтров\порядков чисто для ускорения
    private boolean StructUpdated = true;
    // фильтры !null (св-во), св-во - св-во, св-во - объект, класс св-ва (для < > в том числе)?,

    public Set<Filter> fixedFilters = new HashSet();
    public List<RegularFilterGroup> regularFilterGroups = new ArrayList();
    private Set<Filter> userFilters = new HashSet();

    public void clearUserFilters() {

        userFilters.clear();
        StructUpdated = true;
    }

    private void addUserFilter(Filter addFilter) {

        userFilters.add(addFilter);
        StructUpdated = true;
    }

    private Map<RegularFilterGroup, RegularFilter> regularFilterValues = new HashMap();
    private void setRegularFilter(RegularFilterGroup filterGroup, RegularFilter filter) {

        if (filter == null || filter.filter == null)
            regularFilterValues.remove(filterGroup);
        else
            regularFilterValues.put(filterGroup, filter);

        StructUpdated = true;
    }

    // Порядки

    public static int ORDER_REPLACE = 1;
    public static int ORDER_ADD = 2;
    public static int ORDER_REMOVE = 3;
    public static int ORDER_DIR = 4;

    private LinkedHashMap<PropertyView,Boolean> Orders = new LinkedHashMap();

    private void ChangeOrder(PropertyView propertyView, int modiType) {

        if (modiType == ORDER_REMOVE)
            Orders.remove(propertyView);
        else
        if (modiType == ORDER_DIR)
            Orders.put(propertyView,!Orders.get(propertyView));
        else {
            if (modiType == ORDER_REPLACE) {
                for (PropertyView propView : Orders.keySet())
                    if (propView.ToDraw == propertyView.ToDraw)
                        Orders.remove(propView);
            }
            Orders.put(propertyView,false);
        }

        StructUpdated = true;
    }

    // -------------------------------------- Изменение данных ----------------------------------- //

    // пометка что изменились данные
    private boolean DataChanged = false;

    private void AddObject(ObjectImplement Object, Class cls) throws SQLException {
        // пока тупо в базу

        if (!securityPolicy.cls.edit.add.checkPermission(cls)) return;

        Integer AddID = BL.AddObject(Session, cls);

        boolean foundConflict = false;

        // берем все текущие CompareFilter на оператор 0(=) делаем ChangeProperty на ValueLink сразу в сессию
        // тогда добавляет для всех других объектов из того же GroupObjectImplement'а, значение ValueLink, GetValueExpr
        for(Filter<?> Filter : Object.GroupTo.Filters) {
            if(Filter.Compare==0) {
                JoinQuery<ObjectImplement,String> SubQuery = new JoinQuery<ObjectImplement,String>(Filter.Property.Mapping.values());
                Map<ObjectImplement,Integer> FixedObjects = new HashMap();
                for(ObjectImplement SibObject : Filter.Property.Mapping.values()) {
                    if(SibObject.GroupTo!=Object.GroupTo) {
                        FixedObjects.put(SibObject,SibObject.idObject);
                    } else {
                        if(SibObject!=Object) {
                            Join<KeyField,PropertyField> ObjectJoin = new Join<KeyField,PropertyField>(BL.tableFactory.ObjectTable.getClassJoin(SibObject.GridClass));
                            ObjectJoin.Joins.put(BL.tableFactory.ObjectTable.Key,SubQuery.MapKeys.get(SibObject));
                            SubQuery.and(ObjectJoin.InJoin);
                        } else
                            FixedObjects.put(SibObject,AddID);
                    }
                }

                SubQuery.putKeyWhere(FixedObjects);

                SubQuery.Properties.put("newvalue", Filter.Value.getValueExpr(Object.GroupTo.GetClassGroup(),SubQuery.MapKeys,Session, Filter.Property.Property.getType()));

                LinkedHashMap<Map<ObjectImplement,Integer>,Map<String,Object>> Result = SubQuery.compile().executeSelect(Session, false);
                // изменяем св-ва
                for(Entry<Map<ObjectImplement,Integer>,Map<String,Object>> Row : Result.entrySet()) {
                    Property ChangeProperty = Filter.Property.Property;
                    Map<PropertyInterface,ObjectValue> Keys = new HashMap();
                    for(PropertyInterface Interface : (Collection<PropertyInterface>)ChangeProperty.Interfaces) {
                        ObjectImplement ChangeObject = Filter.Property.Mapping.get(Interface);
                        Keys.put(Interface,new ObjectValue(Row.getKey().get(ChangeObject),ChangeObject.GridClass));
                    }
                    ChangeProperty.changeProperty(Keys,Row.getValue().get("newvalue"), false, Session, null);
                }
            } else {
                if (Object.GroupTo.equals(Filter.GetApplyObject())) foundConflict = true;
            }
        }

        for (PropertyView prop : Orders.keySet()) {
            if (Object.GroupTo.equals(prop.ToDraw)) foundConflict = true;
        }

        ChangeObject(Object, AddID);

        // меняем вид, если при добавлении может получиться, что фильтр не выполнится
        if (foundConflict) {
            changeClassView(Object.GroupTo, false);
        }

        DataChanged = true;
    }

    public void changeClass(ObjectImplement object,Class cls) throws SQLException {

        // проверка, что разрешено удалять объекты
        if (cls == null) {
            if (!securityPolicy.cls.edit.remove.checkPermission(object.Class)) return;
        } else {
            if (!(securityPolicy.cls.edit.remove.checkPermission(object.Class) || securityPolicy.cls.edit.change.checkPermission(object.Class))) return;
            if (!(securityPolicy.cls.edit.add.checkPermission(cls) || securityPolicy.cls.edit.change.checkPermission(cls))) return;
        }

        BL.ChangeClass(Session, object.idObject,cls);

        // Если объект удалили, то сбрасываем текущий объект в null
        if (cls == null) {
            ChangeObject(object, null);
        }

        object.Updated = object.Updated | ObjectImplement.UPDATED_CLASS;

        DataChanged = true;
    }

    private void ChangePropertyView(PropertyView property, Object value, boolean externalID) throws SQLException {
        ChangeProperty(property.View, value, externalID);
    }

    private void ChangeProperty(PropertyObjectImplement property, Object value, boolean externalID) throws SQLException {

        // изменяем св-во
        property.Property.changeProperty(fillPropertyInterface(property), value, externalID, Session, securityPolicy.property.change);

        DataChanged = true;
    }

    // Обновление данных
    public void refreshData() {

        for(GroupObjectImplement Group : Groups) {
            Group.Updated |= GroupObjectImplement.UPDATED_GRIDCLASS;
        }
    }

    // Применение изменений
    public String SaveChanges() throws SQLException {
        return BL.Apply(Session);
    }

    public void CancelChanges() throws SQLException {
        Session.restart(true);

        DataChanged = true;
    }

    // ------------------ Через эти методы сообщает верхним объектам об изменениях ------------------- //

    // В дальнейшем наверное надо будет переделать на Listener'ы...
    protected void objectChanged(Class cls, Integer objectID) {}
    protected void gainedFocus() {
        DataChanged = true;
    }

    void Close() throws SQLException {

        Session.IncrementChanges.remove(this);
        for(GroupObjectImplement Group : Groups) {
            ViewTable DropTable = BL.tableFactory.ViewTables.get(Group.size()-1);
            DropTable.DropViewID(Session, getGroupObjectGID(Group));
        }
    }

    // --------------------------------------------------------------------------------------- //
    // --------------------- Общение в обратную сторону с ClientForm ------------------------- //
    // --------------------------------------------------------------------------------------- //

    private Map<PropertyInterface,ObjectValue> fillPropertyInterface(PropertyObjectImplement<?> property) {

        Property changeProperty = property.Property;
        Map<PropertyInterface,ObjectValue> keys = new HashMap();
        for(PropertyInterface Interface : (Collection<PropertyInterface>)changeProperty.Interfaces) {
            ObjectImplement object = property.Mapping.get(Interface);
            keys.put(Interface,new ObjectValue(object.idObject,object.Class));
        }

        return keys;
    }

    // рекурсия для генерации порядка
    private IntraWhere GenerateOrderWheres(List<SourceExpr> OrderSources,List<Object> OrderWheres,List<Boolean> OrderDirs,boolean Down,int Index) {

        SourceExpr OrderExpr = OrderSources.get(Index);
        Object OrderValue = OrderWheres.get(Index);
        boolean Last = !(Index+1<OrderSources.size());

        int CompareIndex;
        if (OrderDirs.get(Index)) {
            if (Down) {
                if (Last)
                    CompareIndex = CompareWhere.LESS_EQUALS;
                else
                    CompareIndex = CompareWhere.LESS;
            } else
                CompareIndex = CompareWhere.GREATER;
        } else {
            if (Down) {
                if (Last)
                    CompareIndex = CompareWhere.GREATER_EQUALS;
                else
                    CompareIndex = CompareWhere.GREATER;
            } else
                CompareIndex = CompareWhere.LESS;
        }
        IntraWhere OrderWhere = new CompareWhere(OrderExpr,new ValueExpr(OrderValue,OrderExpr.getType()),CompareIndex);

        if(!Last) {
            // >A OR (=A AND >B)
            OuterWhere ResultWhere = new OuterWhere();
            ResultWhere.out(OrderWhere);
            ResultWhere.out(new CompareWhere(OrderExpr,new ValueExpr(OrderValue,OrderExpr.getType()),CompareWhere.EQUALS).
                    in(GenerateOrderWheres(OrderSources,OrderWheres,OrderDirs,Down,Index+1)));
            return ResultWhere;
        } else
            return OrderWhere;
    }

    public Collection<Property> getUpdateProperties() {

        Set<Property> Result = new HashSet();
        for(PropertyView PropView : Properties)
            Result.add(PropView.View.Property);
        for(Filter Filter : fixedFilters)
            Result.addAll(Filter.getProperties());
        return Result;
    }

    Collection<Property> hintsNoUpdate = new HashSet<Property>();
    public Collection<Property> getNoUpdateProperties() {
        return hintsNoUpdate;
    }

    Collection<Property> hintsSave = new HashSet<Property>();
    public boolean toSave(Property Property) {
        return hintsSave.contains(Property);
    }

    public boolean hasSessionChanges() {
        return Session.hasChanges();
    }

    private static int DIRECTION_DOWN = 0;
    private static int DIRECTION_UP = 1;
    private static int DIRECTION_CENTER = 2;

    private FormChanges endApply() throws SQLException {

        FormChanges Result = new FormChanges();

        // если изменились данные, применяем изменения
        Collection<Property> ChangedProps;
        Collection<Class> ChangedClasses = new HashSet();
        if(DataChanged)
            ChangedProps = Session.update(this,ChangedClasses);
        else
            ChangedProps = new ArrayList();

        // бежим по списку вниз
        if(StructUpdated) {
            // построим Map'ы
            // очистим старые

            for(GroupObjectImplement Group : Groups) {
                Group.MapFilters = new HashSet();
                Group.MapOrders = new ArrayList();
            }

            // фильтры
            Set<Filter> filters = new HashSet();
            filters.addAll(fixedFilters);
            for (RegularFilter regFilter : regularFilterValues.values()) filters.add(regFilter.filter);
            for (Filter filter : userFilters) {
                // если вид панельный, то фильтры не нужны
                if (!filter.Property.GetApplyObject().gridClassView) continue;
                filters.add(filter);
            }

            for(Filter Filt : filters)
                Filt.GetApplyObject().MapFilters.add(Filt);

            // порядки
            for(PropertyView Order : Orders.keySet())
                Order.View.GetApplyObject().MapOrders.add(Order);

        }

        for(GroupObjectImplement Group : Groups) {

            if ((Group.Updated & GroupObjectImplement.UPDATED_CLASSVIEW) != 0) {
                Result.ClassViews.put(Group, Group.gridClassView);
            }
            // если изменились :
            // хоть один класс из этого GroupObjectImplement'a - (флаг Updated - 3)
            boolean updateKeys = (Group.Updated & GroupObjectImplement.UPDATED_GRIDCLASS)!=0;

            // фильтр\порядок (надо сначала определить что в интерфейсе (верхних объектов Group и класса этого Group) в нем затем сравнить с теми что были до) - (Filters, Orders объектов)
            // фильтры
            // если изменилась структура или кто-то изменил класс, перепроверяем
            if(StructUpdated) {
                Set<Filter> NewFilter = new HashSet();
                for(Filter Filt : Group.MapFilters)
                    if(Filt.IsInInterface(Group)) NewFilter.add(Filt);

                updateKeys |= !NewFilter.equals(Group.Filters);
                Group.Filters = NewFilter;
            } else
                for(Filter Filt : Group.MapFilters)
                    if(Filt.ClassUpdated(Group))
                        updateKeys |= (Filt.IsInInterface(Group)?Group.Filters.add(Filt):Group.Filters.remove(Filt));

            // порядки
            boolean SetOrderChanged = false;
            Set<PropertyObjectImplement> SetOrders = new HashSet(Group.Orders.keySet());
            for(PropertyView Order : Group.MapOrders) {
                // если изменилась структура или кто-то изменил класс, перепроверяем
                if(StructUpdated || Order.View.ClassUpdated(Group))
                    SetOrderChanged = (Order.View.isInInterface(Group)?SetOrders.add(Order.View):Group.Orders.remove(Order));
            }
            if(StructUpdated || SetOrderChanged) {
                // переформирываваем порядок, если структура или принадлежность Order'у изменилась
                LinkedHashMap<PropertyObjectImplement,Boolean> NewOrder = new LinkedHashMap();
                for(PropertyView Order : Group.MapOrders)
                    if(SetOrders.contains(Order.View)) NewOrder.put(Order.View,Orders.get(Order));

                updateKeys |= SetOrderChanged || !(new ArrayList(Group.Orders.entrySet())).equals(new ArrayList(NewOrder.entrySet())); //Group.Orders.equals(NewOrder)
                Group.Orders = NewOrder;
            }

            // объекты задействованные в фильтре\порядке (по Filters\Orders верхних элементов GroupImplement'ов на флаг Updated - 0)
            if(!updateKeys)
                for(Filter Filt : Group.Filters)
                    if(Filt.ObjectUpdated(Group)) {updateKeys = true; break;}
            if(!updateKeys)
                for(PropertyObjectImplement Order : Group.Orders.keySet())
                    if(Order.ObjectUpdated(Group)) {updateKeys = true; break;}
            // проверим на изменение данных
            if(!updateKeys)
                for(Filter Filt : Group.Filters)
                    if(DataChanged && Filt.DataUpdated(ChangedProps)) {updateKeys = true; break;}
            if(!updateKeys)
                for(PropertyObjectImplement Order : Group.Orders.keySet())
                    if(DataChanged && ChangedProps.contains(Order.Property)) {updateKeys = true; break;}
            // классы удалились\добавились
            if(!updateKeys && DataChanged) {
                for(ObjectImplement Object : Group)
                    if(ChangedClasses.contains(Object.GridClass)) {updateKeys = true; break;}
            }

            // по возврастанию (0), убыванию (1), центру (2) и откуда начинать
            Map<PropertyObjectImplement,Object> PropertySeeks = new HashMap();

            // объект на который будет делаться активным после нахождения ключей
            GroupObjectValue currentObject = Group.GetObjectValue();

            // объект относительно которого будет устанавливаться фильтр
            GroupObjectValue ObjectSeeks = Group.GetObjectValue();
            int Direction;
            boolean hasMoreKeys = true;

            if (ObjectSeeks.containsValue(null)) {
                ObjectSeeks = new GroupObjectValue();
                Direction = DIRECTION_DOWN;
            } else
                Direction = DIRECTION_CENTER;

            // Различные переходы - в самое начало или конец
            Integer pendingChanges = pendingGroupChanges.get(Group);
            if (pendingChanges == null) pendingChanges = -1;

            if (pendingChanges == CHANGEGROUPOBJECT_FIRSTROW) {
                ObjectSeeks = new GroupObjectValue();
                currentObject = null;
                updateKeys = true;
                hasMoreKeys = false;
                Direction = DIRECTION_DOWN;
            }

            if (pendingChanges == CHANGEGROUPOBJECT_LASTROW) {
                ObjectSeeks = new GroupObjectValue();
                currentObject = null;
                updateKeys = true;
                hasMoreKeys = false;
                Direction = DIRECTION_UP;
            }

            // один раз читаем не так часто делается, поэтому не будем как с фильтрами
            for(PropertyObjectImplement Property : UserPropertySeeks.keySet()) {
                if(Property.GetApplyObject()==Group) {
                    PropertySeeks.put(Property,UserPropertySeeks.get(Property));
                    currentObject = null;
                    updateKeys = true;
                    Direction = DIRECTION_CENTER;
                }
            }
            for(ObjectImplement Object : UserObjectSeeks.keySet()) {
                if(Object.GroupTo==Group) {
                    ObjectSeeks.put(Object,UserObjectSeeks.get(Object));
                    currentObject.put(Object,UserObjectSeeks.get(Object));
                    updateKeys = true;
                    Direction = DIRECTION_CENTER;
                }
            }

            if(!updateKeys && (Group.Updated & GroupObjectImplement.UPDATED_CLASSVIEW) !=0) {
               // изменился "классовый" вид перечитываем св-ва
                ObjectSeeks = Group.GetObjectValue();
                updateKeys = true;
                Direction = DIRECTION_CENTER;
            }

            if(!updateKeys && Group.gridClassView && (Group.Updated & GroupObjectImplement.UPDATED_OBJECT)!=0) {
                // листание - объекты стали близки к краю (object не далеко от края - надо хранить список не базу же дергать) - изменился объект
                int KeyNum = Group.Keys.indexOf(Group.GetObjectValue());
                // если меньше PageSize осталось и сверху есть ключи
                if(KeyNum<Group.PageSize && Group.UpKeys) {
                    Direction = DIRECTION_UP;
                    updateKeys = true;

                    int lowestInd = Group.PageSize*2-1;
                    if (lowestInd >= Group.Keys.size()) {
                        ObjectSeeks = new GroupObjectValue();
                        hasMoreKeys = false;
                    } else {
                        ObjectSeeks = Group.Keys.get(lowestInd);
                        PropertySeeks = Group.KeyOrders.get(ObjectSeeks);
                    }

                } else {
                    // наоборот вниз
                    if(KeyNum>=Group.Keys.size()-Group.PageSize && Group.DownKeys) {
                        Direction = DIRECTION_DOWN;
                        updateKeys = true;

                        int highestInd = Group.Keys.size()-Group.PageSize*2;
                        if (highestInd < 0) {
                            ObjectSeeks = new GroupObjectValue();
                            hasMoreKeys = false;
                        } else {
                            ObjectSeeks = Group.Keys.get(highestInd);
                            PropertySeeks = Group.KeyOrders.get(ObjectSeeks);
                        }
                    }
                }
            }

            if(updateKeys) {
                // --- перечитываем источник (если "классовый" вид - 50, + помечаем изменения GridObjects, иначе TOP 1

                // проверим на интегральные классы в Group'e
                for(ObjectImplement Object : Group)
                    if(ObjectSeeks.get(Object)==null && Object.BaseClass instanceof IntegralClass && !Group.gridClassView)
                        ObjectSeeks.put(Object,1);

                // докидываем Join'ами (INNER) фильтры, порядки

                // уберем все некорректности в Seekах :
                // корректно если : PropertySeeks = Orders или (Orders.sublist(PropertySeeks.size) = PropertySeeks и ObjectSeeks - пустое)
                // если Orders.sublist(PropertySeeks.size) != PropertySeeks, тогда дочитываем ObjectSeeks полностью
                // выкидываем лишние PropertySeeks, дочитываем недостающие Orders в PropertySeeks
                // также если панель то тупо прочитаем объект
                boolean NotEnoughOrders = !(PropertySeeks.keySet().equals(Group.Orders.keySet()) || ((PropertySeeks.size()<Group.Orders.size() && (new HashSet((new ArrayList(Group.Orders.keySet())).subList(0,PropertySeeks.size()))).equals(PropertySeeks.keySet())) && ObjectSeeks.size()==0));
                boolean objectFound = true;
                if((NotEnoughOrders && ObjectSeeks.size()<Group.size()) || !Group.gridClassView) {
                    // дочитываем ObjectSeeks то есть на = PropertySeeks, ObjectSeeks
                    JoinQuery<ObjectImplement,Object> SelectKeys = new JoinQuery<ObjectImplement,Object>(Group);
                    SelectKeys.putKeyWhere(ObjectSeeks);
                    Group.fillSourceSelect(SelectKeys,Group.GetClassGroup(),BL.tableFactory,Session);
                    for(Entry<PropertyObjectImplement,Object> Property : PropertySeeks.entrySet())
                        SelectKeys.and(new CompareWhere(Property.getKey().getSourceExpr(Group.GetClassGroup(),SelectKeys.MapKeys,Session),
                                new ValueExpr(Property.getValue(),Property.getKey().Property.getType()),CompareWhere.EQUALS));

                    // докидываем найденные ключи
                    LinkedHashMap<Map<ObjectImplement,Integer>,Map<Object,Object>> ResultKeys = SelectKeys.compile().executeSelect(Session, false);
                    if(ResultKeys.size()>0)
                        for(ObjectImplement ObjectKey : Group)
                            ObjectSeeks.put(ObjectKey,ResultKeys.keySet().iterator().next().get(ObjectKey));
                    else
                        objectFound = false;
                }

                if(!Group.gridClassView) {

                    // если не нашли объект, то придется искать
                    if (!objectFound) {

                        JoinQuery<ObjectImplement,Object> SelectKeys = new JoinQuery<ObjectImplement,Object>(Group);
                        Group.fillSourceSelect(SelectKeys,Group.GetClassGroup(),BL.tableFactory,Session);
                        LinkedHashMap<Map<ObjectImplement,Integer>,Map<Object,Object>> ResultKeys = SelectKeys.compile(new LinkedHashMap<SourceExpr,Boolean>(),1).executeSelect(Session,false);
                        if(ResultKeys.size()>0)
                            for(ObjectImplement ObjectKey : Group)
                                ObjectSeeks.put(ObjectKey,ResultKeys.keySet().iterator().next().get(ObjectKey));
                    }

                    // если панель и ObjectSeeks "полный", то просто меняем объект и ничего не читаем
                    Result.Objects.put(Group,ObjectSeeks);
                    ChangeGroupObject(Group,ObjectSeeks);

                } else {
                    // выкидываем Property которых нет, дочитываем недостающие Orders, по ObjectSeeks то есть не в привязке к отбору
                    if(NotEnoughOrders && ObjectSeeks.size()==Group.size() && Group.Orders.size() > 0) {
                        JoinQuery<ObjectImplement,PropertyObjectImplement> OrderQuery = new JoinQuery<ObjectImplement,PropertyObjectImplement>(ObjectSeeks.keySet());
                        OrderQuery.putKeyWhere(ObjectSeeks);

                        for(PropertyObjectImplement Order : Group.Orders.keySet())
                            OrderQuery.Properties.put(Order, Order.getSourceExpr(Group.GetClassGroup(),OrderQuery.MapKeys,Session));

                        LinkedHashMap<Map<ObjectImplement,Integer>,Map<PropertyObjectImplement,Object>> ResultOrders = OrderQuery.compile().executeSelect(Session,false);
                        for(PropertyObjectImplement Order : Group.Orders.keySet())
                            PropertySeeks.put(Order,ResultOrders.values().iterator().next().get(Order));
                    }

                    LinkedHashMap<SourceExpr,Boolean> SelectOrders = new LinkedHashMap<SourceExpr, Boolean>();
                    JoinQuery<ObjectImplement,PropertyObjectImplement> SelectKeys = new JoinQuery<ObjectImplement,PropertyObjectImplement>(Group);
                    Group.fillSourceSelect(SelectKeys,Group.GetClassGroup(),BL.tableFactory,Session);

                    // складываются источники и значения
                    List<SourceExpr> OrderSources = new ArrayList();
                    List<Object> OrderWheres = new ArrayList();
                    List<Boolean> OrderDirs = new ArrayList();

                    // закинем порядки (с LEFT JOIN'ом)
                    for(Map.Entry<PropertyObjectImplement,Boolean> ToOrder : Group.Orders.entrySet()) {
                        SourceExpr OrderExpr = ToOrder.getKey().getSourceExpr(Group.GetClassGroup(),SelectKeys.MapKeys,Session);
                        SelectOrders.put(OrderExpr,ToOrder.getValue());
                        // надо закинуть их в запрос, а также установить фильтры на порядки чтобы
                        if(PropertySeeks.containsKey(ToOrder.getKey())) {
                            OrderSources.add(OrderExpr);
                            OrderWheres.add(PropertySeeks.get(ToOrder.getKey()));
                            OrderDirs.add(ToOrder.getValue());
                        } else //здесь надо что-то волшебное написать, чтобы null не было
                            SelectKeys.and(OrderExpr.getWhere());
                        // также надо кинуть в запрос ключи порядков, чтобы потом скроллить
                        SelectKeys.Properties.put(ToOrder.getKey(), OrderExpr);
                    }

                    // докинем в ObjectSeeks недостающие группы
                    for(ObjectImplement ObjectKey : Group)
                        if(!ObjectSeeks.containsKey(ObjectKey))
                            ObjectSeeks.put(ObjectKey,null);

                    // закинем объекты в порядок
                    for(ObjectImplement ObjectKey : ObjectSeeks.keySet()) {
                        // также закинем их в порядок и в запрос6
                        SourceExpr KeyExpr = SelectKeys.MapKeys.get(ObjectKey);
                        SelectOrders.put(KeyExpr,false);
                        Integer SeekValue = ObjectSeeks.get(ObjectKey);
                        if(SeekValue!=null) {
                            OrderSources.add(KeyExpr);
                            OrderWheres.add(SeekValue);
                            OrderDirs.add(false);
                        }
                    }

                    // выполняем запрос
                    // какой ряд выбранным будем считать
                    int ActiveRow = -1;
                    // результат
                    LinkedHashMap<Map<ObjectImplement,Integer>,Map<PropertyObjectImplement,Object>> KeyResult = new LinkedHashMap();

                    int ReadSize = Group.PageSize*3/(Direction==DIRECTION_CENTER?2:1);

                    IntraWhere BaseWhere = SelectKeys.Where;
                    // откопируем в сторону запрос чтобы еще раз потом использовать
                    // сначала Descending загоним
                    Group.DownKeys = false;
                    Group.UpKeys = false;
                    if(Direction==DIRECTION_UP || Direction==DIRECTION_CENTER) {
                        if(OrderSources.size()>0) {
                            SelectKeys.and(GenerateOrderWheres(OrderSources,OrderWheres,OrderDirs,false,0));
                            Group.DownKeys = hasMoreKeys;
                        }

                        LinkedHashMap<Map<ObjectImplement,Integer>,Map<PropertyObjectImplement,Object>> ExecResult = SelectKeys.compile(JoinQuery.reverseOrder(SelectOrders),ReadSize).executeSelect(Session, false);
                        ListIterator<Map<ObjectImplement,Integer>> ik = (new ArrayList(ExecResult.keySet())).listIterator();
                        while(ik.hasNext()) ik.next();
                        while(ik.hasPrevious()) {
                            Map<ObjectImplement,Integer> Row = ik.previous();
                            KeyResult.put(Row,ExecResult.get(Row));
                        }
                        Group.UpKeys = (KeyResult.size()==ReadSize);

                        // проверка чтобы не сбить объект при листании и неправильная (потому как после 2 поиска может получится что надо с 0 без Seek'а перечитывать)
//                        if(OrderSources.size()==0)
                        // сделано так, чтобы при ненайденном объекте текущий объект смещался вверх, а не вниз
                        ActiveRow = KeyResult.size()-1;

                    }
                    SelectKeys.Where = BaseWhere;
                    // потом Ascending
                    if(Direction==DIRECTION_DOWN || Direction==DIRECTION_CENTER) {
                        if(OrderSources.size()>0) {
                            SelectKeys.and(GenerateOrderWheres(OrderSources,OrderWheres,OrderDirs,true,0));
                            if(Direction!=DIRECTION_CENTER) Group.UpKeys = hasMoreKeys;
                        }

                        LinkedHashMap<Map<ObjectImplement,Integer>,Map<PropertyObjectImplement,Object>> ExecuteList = SelectKeys.compile(SelectOrders,ReadSize).executeSelect(Session,false);
//                        if((OrderSources.size()==0 || Direction==2) && ExecuteList.size()>0) ActiveRow = KeyResult.size();
                        KeyResult.putAll(ExecuteList);
                        Group.DownKeys = (ExecuteList.size()==ReadSize);

                        if ((Direction == DIRECTION_DOWN || ActiveRow == -1) && KeyResult.size() > 0)
                            ActiveRow = 0;
                    }

                    Group.Keys = new ArrayList();
                    Group.KeyOrders = new HashMap();

                    // параллельно будем обновлять ключи чтобы Join'ить

                    int groupGID = getGroupObjectGID(Group);
                    ViewTable InsertTable = BL.tableFactory.ViewTables.get(Group.size()-1);
                    InsertTable.DropViewID(Session, groupGID);

                    for(Entry<Map<ObjectImplement,Integer>,Map<PropertyObjectImplement,Object>> ResultRow : KeyResult.entrySet()) {
                        GroupObjectValue KeyRow = new GroupObjectValue();
                        Map<PropertyObjectImplement,Object> OrderRow = new HashMap();

                        // закинем сразу ключи для св-в чтобы Join'ить
                        Map<KeyField,Integer> ViewKeyInsert = new HashMap();
                        ViewKeyInsert.put(InsertTable.View,groupGID);
                        ListIterator<KeyField> ivk = InsertTable.Objects.listIterator();

                        // важен правильный порядок в KeyRow
                        for(ObjectImplement ObjectKey : Group) {
                            Integer KeyValue = ResultRow.getKey().get(ObjectKey);
                            KeyRow.put(ObjectKey,KeyValue);
                            ViewKeyInsert.put(ivk.next(), KeyValue);
                        }
                        Session.InsertRecord(InsertTable,ViewKeyInsert,new HashMap());

                        for(PropertyObjectImplement ToOrder : Group.Orders.keySet())
                            OrderRow.put(ToOrder,ResultRow.getValue().get(ToOrder));

                        Group.Keys.add(KeyRow);
                        Group.KeyOrders.put(KeyRow, OrderRow);
                    }

                    Result.GridObjects.put(Group,Group.Keys);

                    Group.Updated = (Group.Updated | GroupObjectImplement.UPDATED_KEYS);

                    // если ряд никто не подставил и ключи есть пробуем старый найти
//                    if(ActiveRow<0 && Group.Keys.size()>0)
//                        ActiveRow = Group.Keys.indexOf(Group.GetObjectValue());

                    // если есть в новых ключах старый ключ, то делаем его активным
                    if (Group.Keys.contains(currentObject))
                        ActiveRow = Group.Keys.indexOf(currentObject);

                    if(ActiveRow>=0 && ActiveRow < Group.Keys.size()) {
                        // нашли ряд его выбираем
                        GroupObjectValue newValue = Group.Keys.get(ActiveRow);
//                        if (!newValue.equals(Group.GetObjectValue())) {
                            Result.Objects.put(Group,newValue);
                            ChangeGroupObject(Group,newValue);
//                        }
                    } else
                        ChangeGroupObject(Group,new GroupObjectValue());
                }
            }
        }

        Collection<PropertyView> PanelProps = new ArrayList();
        Map<GroupObjectImplement,Collection<PropertyView>> GroupProps = new HashMap();

//        PanelProps.

        for(PropertyView<?> DrawProp : Properties) {

            // 3 признака : перечитать, (возможно класс изменился, возможно объектный интерфейс изменился - чисто InterfacePool)
            boolean Read = false;
            boolean CheckClass = false;
            boolean CheckObject = false;
            int InInterface = 0;

            if(DrawProp.ToDraw!=null) {
                // если рисуемся в какой-то вид и обновился источник насильно перечитываем все св-ва
                Read = ((DrawProp.ToDraw.Updated & (GroupObjectImplement.UPDATED_KEYS | GroupObjectImplement.UPDATED_CLASSVIEW))!=0);
                Boolean PrevPool = InterfacePool.get(DrawProp);
                InInterface = (PrevPool==null?0:(PrevPool?2:1));
            }

            for(ObjectImplement Object : DrawProp.View.Mapping.values())  {
                if(Object.GroupTo != DrawProp.ToDraw) {
                    // "верхние" объекты интересует только изменение объектов\классов
                    if((Object.Updated & ObjectImplement.UPDATED_OBJECT)!=0) {
                        // изменился верхний объект, перечитываем
                        Read = true;
                        if((Object.Updated & ObjectImplement.UPDATED_CLASS)!=0) {
                            // изменился класс объекта перепроверяем все
                            if(DrawProp.ToDraw!=null) CheckClass = true;
                            CheckObject = true;
                        }
                    }
                } else {
                    // изменился объект и св-во не было классовым
                    if((Object.Updated & ObjectImplement.UPDATED_OBJECT)!=0 && (InInterface!=2 || !DrawProp.ToDraw.gridClassView)) {
                        Read = true;
                        // изменися класс объекта
                        if((Object.Updated & ObjectImplement.UPDATED_CLASS)!=0) CheckObject = true;
                    }
                    // изменение общего класса
                    if((Object.Updated & ObjectImplement.UPDATED_GRIDCLASS)!=0) CheckClass = true;

                }
            }

            // обновим InterfacePool, было в InInterface
            if(CheckClass || CheckObject) {
                int NewInInterface=0;
                if(CheckClass)
                    NewInInterface = (DrawProp.View.isInInterface(DrawProp.ToDraw)?2:0);
                if((CheckObject && !(CheckClass && NewInInterface==2)) || (CheckClass && NewInInterface==0 )) // && InInterface==2))
                    NewInInterface = (DrawProp.View.isInInterface(null)?1:0);

                if(InInterface!=NewInInterface) {
                    InInterface = NewInInterface;

                    if(InInterface==0) {
                        InterfacePool.remove(DrawProp);
                        // !!! СЮДА НАДО ВКИНУТЬ УДАЛЕНИЕ ИЗ ИНТЕРФЕЙСА
                        Result.DropProperties.add(DrawProp);
                    }
                    else
                        InterfacePool.put(DrawProp,InInterface==2);
                }
            }

            if(!Read && (DataChanged && ChangedProps.contains(DrawProp.View.Property)))
                Read = true;

            if (!Read && DataChanged) {
                for (ObjectImplement object : DrawProp.View.Mapping.values()) {
                    if (ChangedClasses.contains(object.BaseClass)) {
                        Read = true;
                        break;
                    }
                }
            }

            if(InInterface>0 && Read) {
                if(InInterface==2 && DrawProp.ToDraw.gridClassView) {
                    Collection<PropertyView> PropList = GroupProps.get(DrawProp.ToDraw);
                    if(PropList==null) {
                        PropList = new ArrayList();
                        GroupProps.put(DrawProp.ToDraw,PropList);
                    }
                    PropList.add(DrawProp);
                } else
                    PanelProps.add(DrawProp);
            }
        }

        // погнали выполнять все собранные запросы и FormChanges

        // сначала PanelProps
        if(PanelProps.size()>0) {
            JoinQuery<Object,PropertyView> SelectProps = new JoinQuery<Object,PropertyView>(new ArrayList<Object>());
            for(PropertyView DrawProp : PanelProps)
                SelectProps.Properties.put(DrawProp, DrawProp.View.getSourceExpr(null,null,Session));

            Map<PropertyView,Object> ResultProps = SelectProps.compile().executeSelect(Session,false).values().iterator().next();
            for(PropertyView DrawProp : PanelProps)
                Result.PanelProperties.put(DrawProp,ResultProps.get(DrawProp));
        }

        for(Entry<GroupObjectImplement, Collection<PropertyView>> MapGroup : GroupProps.entrySet()) {
            GroupObjectImplement Group = MapGroup.getKey();
            Collection<PropertyView> GroupList = MapGroup.getValue();

            JoinQuery<ObjectImplement,PropertyView> SelectProps = new JoinQuery<ObjectImplement,PropertyView>(Group);

            ViewTable KeyTable = BL.tableFactory.ViewTables.get(Group.size()-1);
            Join<KeyField,PropertyField> KeyJoin = new Join<KeyField,PropertyField>(KeyTable);

            ListIterator<KeyField> ikt = KeyTable.Objects.listIterator();
            for(ObjectImplement Object : Group)
                KeyJoin.Joins.put(ikt.next(),SelectProps.MapKeys.get(Object));
            KeyJoin.Joins.put(KeyTable.View,new ValueExpr(getGroupObjectGID(Group),KeyTable.View.Type));
            SelectProps.and(KeyJoin.InJoin);

            for(PropertyView DrawProp : GroupList)
                SelectProps.Properties.put(DrawProp, DrawProp.View.getSourceExpr(Group.GetClassGroup(),SelectProps.MapKeys,Session));

//            System.out.println(Group);
//            SelectProps.outSelect(Session);
            LinkedHashMap<Map<ObjectImplement,Integer>,Map<PropertyView,Object>> ResultProps = SelectProps.compile().executeSelect(Session, false);

            for(PropertyView DrawProp : GroupList) {
                Map<GroupObjectValue,Object> PropResult = new HashMap();
                Result.GridProperties.put(DrawProp,PropResult);

                for(Entry<Map<ObjectImplement,Integer>,Map<PropertyView,Object>> ResultRow : ResultProps.entrySet())
                    PropResult.put(new GroupObjectValue(ResultRow.getKey()),ResultRow.getValue().get(DrawProp));
            }
        }

        UserPropertySeeks.clear();
        UserObjectSeeks.clear();

        pendingGroupChanges.clear();

        // сбрасываем все пометки
        StructUpdated = false;
        for(GroupObjectImplement Group : Groups) {
            Iterator<ObjectImplement> io = Group.iterator();
            while(io.hasNext()) io.next().Updated=0;
            Group.Updated = 0;
        }
        DataChanged = false;

//        Result.Out(this);

        return Result;
    }

    // возвращает какие объекты отчета фиксируются
    private Set<GroupObjectImplement> getReportObjects() {

        Set<GroupObjectImplement> reportObjects = new HashSet();
        for (GroupObjectImplement group : Groups) {
            if (group.gridClassView)
                reportObjects.add(group);
        }

        return reportObjects;
    }

    // считывает все данные (для отчета)
    private ReportData getReportData() throws SQLException {

        Set<GroupObjectImplement> ReportObjects = getReportObjects();

        Collection<ObjectImplement> ReadObjects = new ArrayList();
        for(GroupObjectImplement Group : ReportObjects)
            ReadObjects.addAll(Group);

        // пока сделаем тупо получаем один большой запрос

        JoinQuery<ObjectImplement,PropertyView> Query = new JoinQuery<ObjectImplement,PropertyView>(ReadObjects);
        LinkedHashMap<SourceExpr,Boolean> QueryOrders = new LinkedHashMap<SourceExpr, Boolean>();

        for (GroupObjectImplement group : Groups) {

            if (ReportObjects.contains(group)) {

                // не фиксированные ключи
                group.fillSourceSelect(Query,ReportObjects,BL.tableFactory,Session);

                // закинем Order'ы
                for(Map.Entry<PropertyObjectImplement,Boolean> Order : group.Orders.entrySet())
                    QueryOrders.put(Order.getKey().getSourceExpr(ReportObjects,Query.MapKeys,Session),Order.getValue());

                for(ObjectImplement Object : group)
                    QueryOrders.put(Object.getSourceExpr(ReportObjects,Query.MapKeys),false);
            }
        }

        ReportData Result = new ReportData();

        for (GroupObjectImplement group : Groups)
            for (ObjectImplement object : group)
                Result.objectsID.put(object.getSID(), object.ID);

        for(PropertyView Property : Properties) {
            Query.Properties.put(Property, Property.View.getSourceExpr(ReportObjects, Query.MapKeys,Session));

            Result.propertiesID.put(Property.getSID(), Property.ID);
            Result.properties.put(Property.ID,new HashMap());
        }

        LinkedHashMap<Map<ObjectImplement,Integer>,Map<PropertyView,Object>> ResultSelect = Query.compile(QueryOrders,0).executeSelect(Session, false);

        for(Entry<Map<ObjectImplement,Integer>,Map<PropertyView,Object>> Row : ResultSelect.entrySet()) {
            Map<Integer,Integer> GroupValue = new HashMap();
            for(GroupObjectImplement Group : Groups)
                for(ObjectImplement Object : Group) {
                    if (ReadObjects.contains(Object))
                        GroupValue.put(Object.ID,Row.getKey().get(Object));
                    else
                        GroupValue.put(Object.ID,Object.idObject);
                }

            Result.readOrder.add(GroupValue);

            for(PropertyView Property : Properties)
                Result.properties.get(Property.ID).put(GroupValue,Row.getValue().get(Property));
        }

//        Result.Out();

        return Result;
    }

}

// поле для отрисовки отчета
class ReportDrawField implements AbstractRowLayoutElement{

    String sID;
    String caption;
    java.lang.Class valueClass;

    int minimumWidth;
    int preferredWidth;
    byte alignment;

    String pattern;

    ReportDrawField(ClientCellView cellView) {
        cellView.fillReportDrawField(this);
    }

    int getCaptionWidth() {
        return caption.length() * 10;
    }

    public int getMinimumWidth() {
        return minimumWidth;
    }

    public int getPreferredWidth() {
        return preferredWidth;
    }

    int left;
    public void setLeft(int ileft) {
        left = ileft;
    }

    int width;
    public void setWidth(int iwidth) {
        width = iwidth;
    }

    int row;
    public void setRow(int irow) {
        row = irow;
    }
}

// считанные данные (должен быть интерфейс Serialize)
class ReportData implements JRDataSource, Serializable {

    List<Map<Integer,Integer>> readOrder = new ArrayList();
    Map<String,Integer> objectsID = new HashMap();
    Map<String,Integer> propertiesID = new HashMap();
    Map<Integer,Map<Map<Integer,Integer>,Object>> properties = new HashMap();

    void Out() {
        for(Integer Object : readOrder.get(0).keySet())
            System.out.print("obj"+Object+" ");
        for(Integer Property : properties.keySet())
            System.out.print("prop"+Property+" ");
        System.out.println();

        for(Map<Integer,Integer> Row : readOrder) {
            for(Integer Object : readOrder.get(0).keySet())
                System.out.print(Row.get(Object)+" ");
            for(Integer Property : properties.keySet())
                System.out.print(properties.get(Property).get(Row)+" ");
            System.out.println();
        }
    }

    int CurrentRow = -1;
    public boolean next() throws JRException {
        CurrentRow++;
        return CurrentRow< readOrder.size();
    }

    public Object getFieldValue(JRField jrField) throws JRException {

        String fieldName = jrField.getName();
        Object Value = null;
        if(objectsID.containsKey(fieldName))
            Value = readOrder.get(CurrentRow).get(objectsID.get(fieldName));
        else {
            Integer propertyID = propertiesID.get(fieldName);
            if (propertyID == null) throw new RuntimeException("Поле " + fieldName + " отсутствует в переданных данных");
            Value = properties.get(propertiesID.get(fieldName)).get(readOrder.get(CurrentRow));
        }

        if (Date.class.getName().equals(jrField.getValueClassName()) && Value != null) {
            Value = DateConverter.intToDate((Integer)Value);
        }

        if(Value instanceof String)
            Value = ((String)Value).trim();

/*        if(Value==null) {

            try {
                return BaseUtils.getDefaultValue(java.lang.Class.forName(jrField.getValueClassName()));
            } catch (InvocationTargetException e) {
            } catch (NoSuchMethodException e) {
            } catch (InstantiationException e) {
            } catch (IllegalAccessException e) {
            } catch (ClassNotFoundException e) {
            }
        } */
        
        return Value;
    }
}