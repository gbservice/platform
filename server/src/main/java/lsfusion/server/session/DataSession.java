package lsfusion.server.session;

import com.google.common.base.Throwables;
import lsfusion.base.*;
import lsfusion.base.col.ListFact;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.SetFact;
import lsfusion.base.col.interfaces.immutable.*;
import lsfusion.base.col.interfaces.mutable.MFilterSet;
import lsfusion.base.col.interfaces.mutable.MOrderExclSet;
import lsfusion.base.col.interfaces.mutable.MSet;
import lsfusion.base.col.interfaces.mutable.add.MAddCol;
import lsfusion.base.col.interfaces.mutable.add.MAddSet;
import lsfusion.base.col.interfaces.mutable.mapvalue.GetExValue;
import lsfusion.base.col.interfaces.mutable.mapvalue.GetValue;
import lsfusion.base.col.interfaces.mutable.mapvalue.ImValueMap;
import lsfusion.interop.Compare;
import lsfusion.interop.action.ConfirmClientAction;
import lsfusion.interop.action.LogMessageClientAction;
import lsfusion.server.Message;
import lsfusion.server.ParamMessage;
import lsfusion.server.ServerLoggers;
import lsfusion.server.Settings;
import lsfusion.server.caches.ManualLazy;
import lsfusion.server.classes.*;
import lsfusion.server.classes.sets.UpClassSet;
import lsfusion.server.context.ThreadLocalContext;
import lsfusion.server.data.*;
import lsfusion.server.data.expr.*;
import lsfusion.server.data.expr.query.GroupExpr;
import lsfusion.server.data.query.Join;
import lsfusion.server.data.query.Query;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.data.type.*;
import lsfusion.server.data.where.Where;
import lsfusion.server.form.instance.ChangedData;
import lsfusion.server.form.instance.FormInstance;
import lsfusion.server.form.instance.PropertyObjectInterfaceInstance;
import lsfusion.server.form.navigator.ComputerController;
import lsfusion.server.form.navigator.IsServerRestartingController;
import lsfusion.server.form.navigator.TimeoutController;
import lsfusion.server.form.navigator.UserController;
import lsfusion.server.logics.*;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.*;
import lsfusion.server.logics.property.actions.SessionEnvEvent;
import lsfusion.server.logics.table.IDTable;
import lsfusion.server.logics.table.ImplementTable;
import org.apache.poi.util.SystemOutLogger;

import javax.swing.*;
import java.sql.SQLException;
import java.util.*;

import static lsfusion.base.BaseUtils.filterKeys;
import static lsfusion.server.logics.ServerResourceBundle.getString;

public class DataSession extends ExecutionEnvironment implements SessionChanges, SessionCreator {

    private Map<DataProperty, SinglePropertyTableUsage<ClassPropertyInterface>> data = MapFact.mAddRemoveMap();
    private SingleKeyPropertyUsage news = null;

    // оптимизационные вещи
    private Set<CustomClass> add = SetFact.mAddRemoveSet();
    private Set<CustomClass> remove = SetFact.mAddRemoveSet();
    private Set<ConcreteObjectClass> usedOldClasses = SetFact.mAddRemoveSet();
    private Set<ConcreteObjectClass> usedNewClasses = SetFact.mAddRemoveSet();
    private Map<CustomClass, DataObject> singleAdd = MapFact.mAddRemoveMap();
    private Map<CustomClass, DataObject> singleRemove = MapFact.mAddRemoveMap();
    private Map<DataObject, ConcreteObjectClass> newClasses = MapFact.mAddRemoveMap(); // просто lazy кэш для getCurrentClass

    public static Where isValueClass(Expr expr, CustomClass customClass, Set<ConcreteObjectClass> usedNewClasses) {
        return isValueClass(expr, customClass.getUpSet(), usedNewClasses);
    }

    public static Where isValueClass(Expr expr, ObjectValueClassSet classSet, Set<ConcreteObjectClass> usedNewClasses) {
        Where result = Where.FALSE;
        for(ConcreteObjectClass usedClass : usedNewClasses)
            if(usedClass instanceof ConcreteCustomClass) {
                ConcreteCustomClass customUsedClass = (ConcreteCustomClass) usedClass;
                if(customUsedClass.inSet(classSet)) // если изменяется на класс, у которого
                    result = result.or(expr.compare(customUsedClass.getClassObject(), Compare.EQUALS));
            }
        return result;
    }

    public ImSet<CalcProperty> getChangedProps(ImSet<CustomClass> add, ImSet<CustomClass> remove, ImSet<ConcreteObjectClass> old, ImSet<ConcreteObjectClass> newc, ImSet<DataProperty> data) {
        return SetFact.<CalcProperty>addExclSet(getClassChanges(add, remove, old, newc), data);
    }
    public ImSet<CalcProperty> getChangedProps() {
        return getChangedProps(SetFact.fromJavaSet(add), SetFact.fromJavaSet(remove), SetFact.fromJavaSet(usedOldClasses), SetFact.fromJavaSet(usedNewClasses), SetFact.fromJavaSet(data.keySet()));
    }

    private class DataModifier extends SessionModifier {

        public SQLSession getSQL() {
            return sql;
        }

        public BaseClass getBaseClass() {
            return baseClass;
        }

        public QueryEnvironment getQueryEnv() {
            return env;
        }

        protected <P extends PropertyInterface> ModifyChange<P> calculateModifyChange(CalcProperty<P> property, PrereadRows<P> preread, FunctionSet<CalcProperty> overrided) {
            PropertyChange<P> propertyChange = getPropertyChange(property);
            if(propertyChange!=null)
                return new ModifyChange<P>(propertyChange, false);
            if(!preread.isEmpty())
                return new ModifyChange<P>(property.getNoChange(), preread, false);
            return null;
        }

        public ImSet<CalcProperty> calculateProperties() {
            return getChangedProps();
        }
    }
    private final DataModifier dataModifier = new DataModifier();

    protected <P extends PropertyInterface> PropertyChange<P> getPropertyChange(CalcProperty<P> property) {
        if(property instanceof ObjectClassProperty)
            return (PropertyChange<P>) getObjectClassChange((ObjectClassProperty) property);

        if(property instanceof ClassDataProperty)
            return (PropertyChange<P>) getClassDataChange((ClassDataProperty) property);

        if(property instanceof IsClassProperty)
            return (PropertyChange<P>) getClassChange((IsClassProperty) property);

        if(property instanceof DataProperty)
            return (PropertyChange<P>) getDataChange((DataProperty) property);
        return null;
    }

    private class Transaction {
        private final Set<CustomClass> add;
        private final Set<CustomClass> remove;
        private final Set<ConcreteObjectClass> usedOldClases;
        private final Set<ConcreteObjectClass> usedNewClases;
        private final Map<CustomClass, DataObject> singleAdd;
        private final Map<CustomClass, DataObject> singleRemove;
        private final Map<DataObject, ConcreteObjectClass> newClasses;

        private final SessionData news;
        private final ImMap<DataProperty, SessionData> data;

        private Transaction() {
            assert sessionEventChangedOld.isEmpty(); // в транзакции никаких сессионных event'ов быть не может
//            assert applyModifier.getHintProps().isEmpty(); // равно как и хинт'ов, не факт, потому как транзакция не сразу создается

            add = new HashSet<CustomClass>(DataSession.this.add);
            remove = new HashSet<CustomClass>(DataSession.this.remove);
            usedOldClases = new HashSet<ConcreteObjectClass>(DataSession.this.usedOldClasses);
            usedNewClases = new HashSet<ConcreteObjectClass>(DataSession.this.usedNewClasses);
            singleAdd = new HashMap<CustomClass, DataObject>(DataSession.this.singleAdd);
            singleRemove = new HashMap<CustomClass, DataObject>(DataSession.this.singleRemove);
            newClasses = new HashMap<DataObject, ConcreteObjectClass>(DataSession.this.newClasses);

            data = SessionTableUsage.saveData(DataSession.this.data);
            if(DataSession.this.news!=null)
                news = DataSession.this.news.saveData();
            else
                news = null;
        }
        
        private void rollData() throws SQLException {
            Map<DataProperty, SinglePropertyTableUsage<ClassPropertyInterface>> rollData = MapFact.mAddRemoveMap();
            for(int i=0,size=data.size();i<size;i++) {
                DataProperty prop = data.getKey(i);

                SinglePropertyTableUsage<ClassPropertyInterface> table = DataSession.this.data.get(prop);
                if(table==null) {
                    table = prop.createChangeTable();
                    table.drop(sql);
                }

                table.rollData(sql, data.getValue(i));
                rollData.put(prop, table);
            }
            DataSession.this.data = rollData;
        }

        private void rollNews() throws SQLException {
            if(news!=null) {
                if(DataSession.this.news==null) {
                    DataSession.this.news = new SingleKeyPropertyUsage(ObjectType.instance, ObjectType.instance);
                    DataSession.this.news.drop(sql);
                }
                DataSession.this.news.rollData(sql, news);
            } else
                DataSession.this.news = null;
        }

        private void rollback() throws SQLException {
            assert sessionEventChangedOld.isEmpty(); // в транзакции никаких сессионных event'ов быть не может
            assert applyModifier.getHintProps().isEmpty(); // равно как и хинт'ов

            dropTables(SetFact.<SessionDataProperty>EMPTY()); // старые вернем, таблицу удалятся (но если нужны будут, rollback откатит эти изменения)

            // assert что новые включают старые
            DataSession.this.add = add;
            DataSession.this.remove = remove;
            DataSession.this.usedOldClasses = usedOldClases;
            DataSession.this.usedNewClasses = usedNewClases;
            DataSession.this.singleAdd = singleAdd;
            DataSession.this.singleRemove = singleRemove;
            DataSession.this.newClasses = newClasses;

            rollData();
            rollNews();
            
            dataModifier.eventDataChanges(getChangedProps(SetFact.fromJavaSet(add), SetFact.fromJavaSet(remove), SetFact.fromJavaSet(usedOldClasses), SetFact.fromJavaSet(usedNewClasses), data.keys()));
        }
    }
    private Transaction applyTransaction; // restore point
    private boolean isInTransaction;

    private void startTransaction(UpdateCurrentClasses update, BusinessLogics<?> BL) throws SQLException, SQLHandledException {
        assert !isInTransaction;
        sql.startTransaction(DBManager.SESSION_TIL);
        isInTransaction = true;
        if(applyFilter == ApplyFilter.ONLY_DATA)
            onlyDataModifier = new OverrideSessionModifier(new IncrementChangeProps(BL.getDataChangeEvents()), applyModifier);
    }
    
    private void cleanOnlyDataModifier() throws SQLException {
        if(onlyDataModifier != null) {
            assert applyFilter == ApplyFilter.ONLY_DATA;
            onlyDataModifier.clean(sql);
            onlyDataModifier = null;
        }
    }
    
    private void checkTransaction() {
        if(isInTransaction() && applyTransaction==null)
            applyTransaction = new Transaction();
    }
    public void rollbackTransaction() throws SQLException {
        for(Runnable info : rollbackInfo)
            info.run();
        
        try {
            if(applyTransaction!=null)
                applyTransaction.rollback();
        } finally {
            endTransaction();
            sql.rollbackTransaction();
        }
//        checkSessionTableMap();
    }

    private void endTransaction() throws SQLException {
        applyTransaction = null;
        isInTransaction = false;
        rollbackInfo.clear();
        cleanOnlyDataModifier();
    }
/*    private void checkSessionTableMap() {
        checkSessionTableMap(add);
        checkSessionTableMap(remove);
        checkSessionTableMap(data);
        checkSessionTableMap(news);
    }
    private void checkSessionTableMap(Map<?, ? extends SessionTableUsage> usages) {
        for(SessionTableUsage usage : usages.values())
            checkSessionTableMap(usage);
    }
    private void checkSessionTableMap(SessionTableUsage usage) {
        if(usage!=null && usage.table instanceof SessionDataTable)
            sql.checkSessionTableMap(((SessionDataTable)usage.table).getTable(), usage);
    }*/
    

    private void commitTransaction() throws SQLException {
        endTransaction();
        sql.commitTransaction();
    }

    private ImSet<CalcProperty<ClassPropertyInterface>> getClassChanges(ImSet<CustomClass> addClasses, ImSet<CustomClass> removeClasses, ImSet<ConcreteObjectClass> oldClasses, ImSet<ConcreteObjectClass> newClasses) {
        return SetFact.addExcl(CustomClass.getProperties(addClasses, removeClasses, oldClasses, newClasses), baseClass.getObjectClassProperty());
    }

    public boolean hasChanges() {
        return !data.isEmpty() || news!=null;
    }

    public boolean hasStoredChanges() {
        if (news != null)
            return true;

        for (DataProperty property : data.keySet())
            if (property.isStored())
                return true;

        return false;
    }

    private PropertyChange<ClassPropertyInterface> getObjectClassChange(ObjectClassProperty property) {
        if(news!=null)
            return SingleKeyPropertyUsage.getChange(news, property.interfaces.single());
        return null;
    }

    private boolean containsClassDataChange(ClassDataProperty property) {
        for(ConcreteCustomClass child : property.set.getSetConcreteChildren())
            if(usedOldClasses.contains(child) || usedNewClasses.contains(child))
                return true;
        return false;
    }
    private PropertyChange<ClassPropertyInterface> getClassDataChange(ClassDataProperty property) {
        if(news!=null && containsClassDataChange(property)) {
            ImRevMap<ClassPropertyInterface, KeyExpr> mapKeys = property.getMapKeys();
            KeyExpr keyExpr = mapKeys.singleValue();

            Join<String> join = news.join(keyExpr);
            Expr newClassExpr = join.getExpr("value");
            Where where = join.getWhere();

            Where newClass = isValueClass(newClassExpr, property.set, usedNewClasses);
/*            Where oldClass = keyExpr.isClass(property.set); // в общем то оптимизация (чтобы например не делать usedOldClasses)
            where = where.and(oldClass.or(newClass));*/

            return new PropertyChange<ClassPropertyInterface>(mapKeys, // на не null меняем только тех кто подходит по классу
                    newClassExpr.and(newClass), where);
        }
        return null;
    }

    private PropertyChange<ClassPropertyInterface> getClassChange(IsClassProperty property) { // важно чтобы совпадало с инкрементальным алгритмом в DataSession.aspectChangeClass
        ValueClass isClass = property.getInterfaceClass();
        if(isClass instanceof CustomClass) {
            CustomClass customClass = (CustomClass) isClass;
            boolean added = add.contains(customClass);
            boolean removed = remove.contains(customClass);
            if(added || removed) { // оптимизация в том числе
                ImRevMap<ClassPropertyInterface, KeyExpr> mapKeys = property.getMapKeys();
                KeyExpr key = mapKeys.singleValue();

                Join<String> join = news.join(key);
                Expr newClassExpr = join.getExpr("value");

                Where hasClass = isValueClass(newClassExpr, customClass, usedNewClasses);
                Where hadClass = key.isUpClass(isClass);

                Where changedWhere = Where.FALSE;
                Expr changeExpr;
                if(added) {
                    Where addWhere;
                    DataObject dataObject = singleAdd.get(customClass);
                    if(dataObject!=null) // оптимизация
                        addWhere = key.compare(dataObject, Compare.EQUALS);
                    else
                        addWhere = hasClass.and(hadClass.not());
                    changeExpr = ValueExpr.get(addWhere);
                    changedWhere = changedWhere.or(addWhere);
                } else
                    changeExpr = Expr.NULL;

                if(removed) {
                    Where removeWhere;
                    DataObject dataObject = singleRemove.get(customClass);
                    if(dataObject!=null)
                        removeWhere = key.compare(dataObject, Compare.EQUALS);
                    else
                        removeWhere = hadClass.and(join.getWhere().and(hasClass.not())); 
                    changedWhere = changedWhere.or(removeWhere); // был класс и изменился, но не на новый
                }
                return new PropertyChange<ClassPropertyInterface>(mapKeys, changeExpr, changedWhere);
            }
        }
        return null;
    }

    public PropertyChange<ClassPropertyInterface> getDataChange(DataProperty property) {
        SinglePropertyTableUsage<ClassPropertyInterface> dataChange = data.get(property);
        if(dataChange!=null)
            return SinglePropertyTableUsage.getChange(dataChange);
        return null;
    }

    public final SQLSession sql;
    public final SQLSession idSession;

    public void close() throws SQLException {
    }

    public static class UpdateChanges {

        public ImSet<CalcProperty> properties;

        public UpdateChanges() {
            properties = SetFact.EMPTY();
        }

        public UpdateChanges(DataSession session) {
            properties = session.getChangedProps();
        }

        public void add(ImSet<? extends CalcProperty> set) {
            properties = properties.merge(set);
        }
        public void add(UpdateChanges changes) {
            add(changes.properties);
        }
    }

    // формы, для которых с момента последнего update уже был restart, соотвественно в значениях - изменения от посл. update (prev) до посл. apply
    public IdentityHashMap<FormInstance, UpdateChanges> appliedChanges = new IdentityHashMap<FormInstance, UpdateChanges>();

    // формы для которых с момента последнего update не было restart, соответственно в значениях - изменения от посл. update (prev) до посл. изменения
    public IdentityHashMap<FormInstance, UpdateChanges> incrementChanges = new IdentityHashMap<FormInstance, UpdateChanges>();

    // assert что те же формы что и в increment, соответственно в значениях - изменения от посл. apply до посл. update (prev)
    public IdentityHashMap<FormInstance, UpdateChanges> updateChanges = new IdentityHashMap<FormInstance, UpdateChanges>();

    public final BaseClass baseClass;
    public final ConcreteCustomClass sessionClass;
    public final LCP<?> currentSession;

    // для отладки
    public static boolean reCalculateAggr = false;

    private final IsServerRestartingController isServerRestarting;
    public final TimeoutController timeout;
    public final ComputerController computer;
    public final UserController user;

    public DataObject applyObject = null;
    
    private final ImOrderMap<ActionProperty, SessionEnvEvent> sessionEvents;

    private ImOrderSet<ActionProperty> activeSessionEvents;
    @ManualLazy
    private ImOrderSet<ActionProperty> getActiveSessionEvents() {
        if(activeSessionEvents == null)
            activeSessionEvents = filterOrderEnv(sessionEvents);
        return activeSessionEvents;
    }

    private ImSet<OldProperty> sessionEventOldDepends;
    @ManualLazy
    private ImSet<OldProperty> getSessionEventOldDepends() { // assert что OldProperty, при этом у которых Scope соответствующий локальному событию
        if(sessionEventOldDepends==null) {
            MSet<OldProperty> mResult = SetFact.mSet();
            for(ActionProperty<?> action : getActiveSessionEvents())
                mResult.addAll(action.getSessionEventOldDepends());
            sessionEventOldDepends = mResult.immutable();
        }
        return sessionEventOldDepends;
    }

    public DataSession(SQLSession sql, final UserController user, final ComputerController computer, TimeoutController timeout, IsServerRestartingController isServerRestarting, BaseClass baseClass, ConcreteCustomClass sessionClass, LCP currentSession, SQLSession idSession, ImOrderMap<ActionProperty, SessionEnvEvent> sessionEvents) throws SQLException {
        this.sql = sql;
        this.isServerRestarting = isServerRestarting;

        this.baseClass = baseClass;
        this.sessionClass = sessionClass;
        this.currentSession = currentSession;

        this.user = user;
        this.computer = computer;
        this.timeout = timeout;

        this.sessionEvents = sessionEvents;

        this.idSession = idSession;
    }

    public DataSession createSession() throws SQLException {
        return new DataSession(sql, user, computer, timeout, isServerRestarting, baseClass, sessionClass, currentSession, idSession, sessionEvents);
    }

    public void restart(boolean cancel, ImSet<SessionDataProperty> keep) throws SQLException {

        // apply
        //      по кому был restart : добавляем changes -> applied
        //      по кому не было restart : to -> applied (помечая что был restart)

        // cancel
        //    по кому не было restart :  from -> в applied (помечая что был restart)

        if(!cancel)
            for(Map.Entry<FormInstance,UpdateChanges> appliedChange : appliedChanges.entrySet())
                appliedChange.getValue().add(new UpdateChanges(this));

        assert Collections.disjoint(appliedChanges.keySet(),(cancel?updateChanges:incrementChanges).keySet());
        appliedChanges.putAll(cancel?updateChanges:incrementChanges);
        incrementChanges = new IdentityHashMap<FormInstance, UpdateChanges>();
        updateChanges = new IdentityHashMap<FormInstance, UpdateChanges>();

        dropTables(keep);
        add.clear();
        remove.clear();
        usedOldClasses.clear();
        usedNewClasses.clear();
        singleAdd.clear();
        singleRemove.clear();
        newClasses.clear();

        BaseUtils.clearNotKeys(data, keep);
        news = null;

        assert dataModifier.getHintProps().isEmpty(); // hint'ы все должны также уйти

        if(cancel) {
            sessionEventChangedOld.clear(sql);
        } else
            assert sessionEventChangedOld.isEmpty();
        sessionEventNotChangedOld.clear();

        applyObject = null; // сбрасываем в том числе когда cancel потому как cancel drop'ает в том числе и добавление объекта
    }

    public DataObject addObject() throws SQLException {
        return new DataObject(IDTable.instance.generateID(idSession, IDTable.OBJECT),baseClass.unknown);
    }

    // с fill'ами addObject'ы
    public DataObject addObject(ConcreteCustomClass customClass, DataObject object) throws SQLException, SQLHandledException {
        if(object==null)
            object = addObject();

        // запишем объекты, которые надо будет сохранять
        changeClass(object, customClass);

        return object;
    }

    private static Pair<Integer, Integer>[] toZeroBased(Pair<Integer, Integer>[] shifts) {
        Pair<Integer, Integer>[] result = new Pair[shifts.length];
        for(int i=0;i<shifts.length;i++)
            result[i] = new Pair<Integer, Integer>(shifts[i].first - 1, shifts[i].second);
        return result;
    }

    public <T extends PropertyInterface> SinglePropertyTableUsage<T> addObjects(ConcreteCustomClass cls, PropertySet<T> set) throws SQLException, SQLHandledException {
        final Query<T, String> query = set.getAddQuery(baseClass); // query, который генерит номера записей (one-based)

        // сначала закидываем в таблицу set с номерами рядов (!!! нужно гарантировать однозначность)
        SinglePropertyTableUsage<T> table = new SinglePropertyTableUsage<T>(query.getMapKeys().keys().toOrderSet(), new Type.Getter<T>() {
            public Type getType(T key) {
                return query.getKeyType(key);
            }
        }, ObjectType.instance);
        table.modifyRows(sql, query, baseClass, Modify.ADD, env);

        if(table.isEmpty()) // оптимизация, не зачем генерить id и все такое
            return table;

        try {
            // берем количество рядов - резервируем ID'ки
            Pair<Integer, Integer>[] startFrom = IDTable.instance.generateIDs(table.getCount(), idSession, IDTable.OBJECT);
    
            // update'им на эту разницу ключи, чтобы сгенерить объекты
            table.updateAdded(sql, baseClass, toZeroBased(startFrom)); // так как не zero-based отнимаем 1
    
            // вообще избыточно, если compile'ить отдельно в for() + changeClass, который сам сгруппирует, но тогда currentClass будет unknown в свойстве что вообщем то не возможно
            KeyExpr keyExpr = new KeyExpr("keyExpr");
            changeClass(new ClassChange(keyExpr, GroupExpr.create(MapFact.singleton("key", table.join(query.mapKeys).getExpr("value")),
                    Where.TRUE, MapFact.singleton("key", keyExpr)).getWhere(), cls));
        } catch(Throwable t) {
            table.drop(sql);
            throw ExceptionUtils.propagate(t, SQLException.class, SQLHandledException.class);
        }
            
        // возвращаем таблицу
        return table;
    }

    public DataObject addObject(ConcreteCustomClass customClass) throws SQLException, SQLHandledException {
        return addObject(customClass, null);
    }

    public void changeClass(PropertyObjectInterfaceInstance objectInstance, DataObject dataObject, ConcreteObjectClass cls) throws SQLException, SQLHandledException {
        changeClass(dataObject, cls);
    }

    public void changeClass(DataObject change, ConcreteObjectClass toClass) throws SQLException, SQLHandledException {
        if(toClass==null) toClass = baseClass.unknown;

        changeClass(new ClassChange(change, toClass));
    }

    public <K> void updateCurrentClasses(Collection<SinglePropertyTableUsage<K>> tables) throws SQLException, SQLHandledException {
        for(SinglePropertyTableUsage<K> table : tables)
            table.updateCurrentClasses(this);
    }

    public <K, T extends ObjectValue> ImMap<K, T> updateCurrentClasses(ImMap<K, T> objectValues) throws SQLException, SQLHandledException {
        ImValueMap<K, T> mvResult = objectValues.mapItValues(); // exception кидает
        for(int i=0,size=objectValues.size();i<size;i++)
            mvResult.mapValue(i, (T) updateCurrentClass(objectValues.getValue(i)));
        return mvResult.immutableValue();
    }

    public ObjectValue updateCurrentClass(ObjectValue value) throws SQLException, SQLHandledException {
        if(value instanceof NullValue)
            return value;
        else {
            DataObject dataObject = (DataObject)value;
            return new DataObject(dataObject.object, getCurrentClass(dataObject));
        }
    }

    public <K> ImOrderSet<ImMap<K, ConcreteObjectClass>> readDiffClasses(Where where, ImMap<K, ? extends Expr> classExprs, ImMap<K, ? extends Expr> objectExprs) throws SQLException, SQLHandledException {

        final ValueExpr unknownExpr = new ValueExpr(-1, baseClass.unknown);

        ImRevMap<K,KeyExpr> keys = KeyExpr.getMapKeys(classExprs.keys().addExcl(objectExprs.keys()));
        ImMap<K, Expr> group = ((ImMap<K, Expr>)classExprs).mapValues(new GetValue<Expr, Expr>() {
                    public Expr getMapValue(Expr value) {
                        return value.nvl(unknownExpr);
            }}).addExcl(((ImMap<K, Expr>)objectExprs).mapValuesEx(new GetExValue<Expr, Expr, SQLException, SQLHandledException>() {
            public Expr getMapValue(Expr value) throws SQLException, SQLHandledException {
                return baseClass.getObjectClassProperty().getExpr(value, getModifier()).nvl(unknownExpr);
            }
        }));

        return new Query<K, String>(keys, GroupExpr.create(group, where, keys).getWhere()).execute(this).keyOrderSet().mapMergeOrderSetValues(new GetValue<ImMap<K, ConcreteObjectClass>, ImMap<K, Object>>() {
            public ImMap<K, ConcreteObjectClass> getMapValue(ImMap<K, Object> readClasses) {
                return readClasses.mapValues(new GetValue<ConcreteObjectClass, Object>() {
                    public ConcreteObjectClass getMapValue(Object id) {
                        return baseClass.findConcreteClassID(((Integer) id) != -1 ? (Integer) id : null);
                    }
                });
            }
        });
    }

    public void changeClass(ClassChange change) throws SQLException, SQLHandledException {
        if(change.isEmpty()) // оптимизация, важна так как во многих event'ах может учавствовать
            return;
        
        boolean hadStoredChanges = hasStoredChanges();

        SingleKeyPropertyUsage changeTable = null;
        FunctionSet<CalcProperty<ClassPropertyInterface>> updateSourceChanges;
        ImSet<CustomClass> addClasses; ImSet<CustomClass> removeClasses;
        ImSet<ConcreteObjectClass> changedOldClasses; ImSet<ConcreteObjectClass> changedNewClasses;
        ImSet<CalcProperty<ClassPropertyInterface>> updateChanges;

        try {
            MSet<CustomClass> mAddClasses = SetFact.mSet(); MSet<CustomClass> mRemoveClasses = SetFact.mSet();
            MSet<ConcreteObjectClass> mChangeOldClasses = SetFact.mSet(); MSet<ConcreteObjectClass> mChangeNewClasses = SetFact.mSet();
            if(change.keyValue !=null) { // оптимизация
                ConcreteObjectClass prevcl = (ConcreteObjectClass) getCurrentClass(change.keyValue);
                ConcreteObjectClass newcl = baseClass.findConcreteClassID((Integer) change.propValue.getValue());
                newcl.getDiffSet(prevcl, mAddClasses, mRemoveClasses);
                mChangeOldClasses.add(prevcl);
                mChangeNewClasses.add(newcl);
            } else {
                if(change.needMaterialize()) {
                    changeTable = change.materialize(sql, baseClass, env); // materialize'им изменение
                    change = changeTable.getChange();
                }
    
                if(change.isEmpty()) // оптимизация, важна так как во многих event'ах может учавствовать
                    return;
    
                // читаем варианты изменения классов
                for(ImMap<String, ConcreteObjectClass> diffClasses : readDiffClasses(change.where, MapFact.singleton("newcl", change.expr), MapFact.singleton("prevcl", change.key))) {
                    ConcreteObjectClass newcl = diffClasses.get("newcl");
                    ConcreteObjectClass prevcl = diffClasses.get("prevcl");
                    newcl.getDiffSet(prevcl, mAddClasses, mRemoveClasses);
                    mChangeOldClasses.add(prevcl);
                    mChangeNewClasses.add(newcl);
                }
            }
            addClasses = mAddClasses.immutable(); removeClasses = mRemoveClasses.immutable();
            changedOldClasses = mChangeOldClasses.immutable(); changedNewClasses = mChangeNewClasses.immutable();
    
            updateChanges = getClassChanges(addClasses, removeClasses, changedOldClasses, changedNewClasses);
    
            updateSessionEvents(updateChanges);
    
            updateSourceChanges = aspectChangeClass(addClasses, removeClasses, changedOldClasses, changedNewClasses, change);
        } finally {
            if(changeTable!=null)
                changeTable.drop(sql);
        }

        if(updateSourceChanges != null) {
            if(updateSourceChanges.isFull()) // не очень красиво, но иначе придется дополнительные параметры заводить
                dataModifier.eventSourceChanges(CustomClass.getProperties( // так как таблица news используется при определении изменений всех классов, то нужно обновить и их "источники" (если изменились)
                                    SetFact.fromJavaSet(add).remove(addClasses), SetFact.fromJavaSet(remove).remove(removeClasses),
                                    SetFact.fromJavaSet(usedOldClasses).remove(changedOldClasses), SetFact.fromJavaSet(usedNewClasses).remove(changedNewClasses)));

            updateProperties(updateChanges, updateSourceChanges);

            aspectAfterChange(hadStoredChanges);
        }
    }
    
    public void dropChanges(DataProperty property) throws SQLException, SQLHandledException {
        if(!data.containsKey(property)) // оптимизация, см. использование
            return;

        updateSessionEvents(SetFact.singleton(property));

        aspectDropChanges(property);

        updateProperties(SetFact.singleton(property), true); // уже соптимизировано выше
    }

    public void changeProperty(DataProperty property, PropertyChange<ClassPropertyInterface> change) throws SQLException, SQLHandledException {
        boolean hadStoredChanges = hasStoredChanges();

        SinglePropertyTableUsage<ClassPropertyInterface> changeTable = null;

        ImSet<DataProperty> updateChanges;
        ModifyResult changed;
        try {
            if(neededProps!=null && property.isStored() && property.event==null) { // если транзакция, нет change event'а, singleApply'им
                assert isInTransaction();
    
                changeTable = splitApplySingleStored(property, property.readFixChangeTable(sql, change, baseClass, getQueryEnv()), ThreadLocalContext.getBusinessLogics());
                change = SinglePropertyTableUsage.getChange(changeTable);
            } else {
                if(change.needMaterialize()) {
                    changeTable = change.materialize(property, sql, baseClass, getQueryEnv());
                    change = SinglePropertyTableUsage.getChange(changeTable);
                }
    
                if(change.isEmpty()) // оптимизация по аналогии с changeClass
                    return;
            }
    
            updateChanges = SetFact.singleton(property);
    
            updateSessionEvents(updateChanges);
    
            changed = aspectChangeProperty(property, change);
        } finally {
            if(changeTable!=null)
                changeTable.drop(sql);
        }

        if(changed.dataChanged()) {
            updateProperties(updateChanges, changed.sourceChanged());

            aspectAfterChange(hadStoredChanges);
        }
    }

    public static final SessionDataProperty isDataChanged = new SessionDataProperty("isDataChanged", "Is data changed", LogicalClass.instance);
    private void aspectAfterChange(boolean hadStoredChanges) throws SQLException, SQLHandledException {
        if(!hadStoredChanges && hasStoredChanges()) {
            ImSet<SessionDataProperty> updateChanges = SetFact.singleton(isDataChanged);
            updateSessionEvents(updateChanges);

            ModifyResult changed = aspectChangeProperty(isDataChanged, new PropertyChange<ClassPropertyInterface>(new DataObject(true, LogicalClass.instance)));
            if(changed.dataChanged())
                updateProperties(updateChanges, changed.sourceChanged());
        }
    }

    public void updateProperties(ImSet<? extends CalcProperty> changes, boolean sourceChanged) throws SQLException {
        updateProperties(changes, sourceChanged ? FullFunctionSet.<CalcProperty>instance() : SetFact.<CalcProperty>EMPTY());
    }

    public void updateProperties(ImSet<? extends CalcProperty> changes, FunctionSet<? extends CalcProperty> sourceChanges) throws SQLException {
        dataModifier.eventDataChanges(changes, sourceChanges);

        for(Map.Entry<FormInstance,UpdateChanges> incrementChange : incrementChanges.entrySet()) {
            incrementChange.getValue().add(changes);
        }

        for (FormInstance form : activeForms.keySet()) {
            form.dataChanged = true;
        }
    }

    // для OldProperty хранит изменения с предыдущего execute'а
    private IncrementTableProps sessionEventChangedOld = new IncrementTableProps(); // assert что OldProperty, при этом у которых Scope соответствующий локальному событию
    private IncrementChangeProps sessionEventNotChangedOld = new IncrementChangeProps(); // assert что OldProperty, при этом у которых Scope соответствующий локальному событию

    // потом можно было бы оптимизировать создание OverrideSessionModifier'а (в рамках getPropertyChanges) и тогда можно создавать modifier'ы непосредственно при запуске
    private boolean inSessionEvent;
    private OverrideSessionModifier sessionEventModifier = new OverrideSessionModifier(new OverrideIncrementProps(sessionEventChangedOld, sessionEventNotChangedOld), false, dataModifier);

    public <P extends PropertyInterface> void updateSessionEvents(ImSet<? extends CalcProperty> changes) throws SQLException, SQLHandledException {
        if(!isInTransaction())
            for(OldProperty<PropertyInterface> old : getSessionEventOldDepends())
                if(!sessionEventChangedOld.contains(old) && CalcProperty.depends(old.property, changes)) // если влияет на old из сессионного event'а и еще не читалось
                    sessionEventChangedOld.add(old, old.property.readChangeTable(sql, getModifier(), baseClass, getQueryEnv()));
    }

    private boolean isInSessionEvent() {
        return inSessionEvent;
    }

    public <T extends PropertyInterface> void executeSessionEvents(FormInstance form) throws SQLException, SQLHandledException {

        if(sessionEventChangedOld.getProperties().size() > 0) { // оптимизационная проверка

            ExecutionEnvironment env = (form != null ? form : this);

            inSessionEvent = true;

            try {
                for(ActionProperty<?> action : getActiveSessionEvents()) {
                    if(sessionEventChangedOld.getProperties().intersect(action.getSessionEventOldDepends())) { // оптимизация аналогичная верхней
                        executeSessionEvent(env, action);
                        if(!isInSessionEvent())
                            return;
                    }
                }
            } finally {
                inSessionEvent = false;
            }

            // закидываем старые изменения
            for(CalcProperty changedOld : sessionEventChangedOld.getProperties()) // assert что только old'ы
                sessionEventNotChangedOld.add(changedOld, ((OldProperty<PropertyInterface>)changedOld).property.getIncrementChange(env.getModifier()));
            sessionEventChangedOld.clear(sql);
        }
    }

    @LogTime
    private void executeSessionEvent(ExecutionEnvironment env, ActionProperty<?> action) throws SQLException, SQLHandledException {
        action.execute(env);
    }

    private OverrideSessionModifier resolveModifier = null;

    public <T extends PropertyInterface> void resolve(ActionProperty<?> action) throws SQLException, SQLHandledException {
        IncrementChangeProps changes = new IncrementChangeProps();
        for(SessionCalcProperty sessionCalcProperty : action.getSessionCalcDepends(false))
            if(sessionCalcProperty instanceof ChangedProperty) // именно так, OldProperty нельзя подменять, так как предполагается что SET и DROPPED требуют разные значения PREV
                changes.add(sessionCalcProperty, ((ChangedProperty)sessionCalcProperty).getFullChange(getModifier()));
        
        resolveModifier = new OverrideSessionModifier(changes, true, dataModifier);
        try {
            action.execute(this);
        } finally {
            resolveModifier.clean(sql);
            resolveModifier = null;
        }
    }

    public static String checkClasses(SQLSession sql, BaseClass baseClass) throws SQLException, SQLHandledException {

        // тут можно было бы использовать нижнюю конструкцию, но с учетом того что не все базы поддерживают FULL JOIN, на UNION'ах и их LEFT JOIN'ах с проталкиванием, запросы получаются мегабайтные и СУБД не справляется
//        KeyExpr key = new KeyExpr("key");
//        String incorrect = new Query<String,String>(MapFact.singletonRev("key", key), key.classExpr(baseClass, IsClassType.SUMCONSISTENT).compare(ValueExpr.COUNT, Compare.GREATER)).readSelect(sql, env);

        // пока не вытягивает определение, для каких конкретно классов образовалось пересечение, ни сервер приложение ни СУБД
        final KeyExpr key = new KeyExpr("key");
        final int threshold = 30;
        final ImOrderSet<ClassField> tables = baseClass.getUpTables().keys().toOrderSet();

        final MLinearOperandMap mSum = new MLinearOperandMap();
//        final MList<Expr> mAgg = ListFact.mList();
        final MAddCol<SingleKeyTableUsage<String>> usedTables = ListFact.mAddCol();
        for(ImSet<ClassField> group : tables.getSet().group(new BaseUtils.Group<Integer, ClassField>() {
            public Integer group(ClassField key) {
                return tables.indexOf(key) % threshold;
            }}).values()) {
            SingleKeyTableUsage<String> table = new SingleKeyTableUsage<String>(ObjectType.instance, SetFact.toOrderExclSet("sum"), new Type.Getter<String>() {
                public Type getType(String key) { // "agg"
                    return key.equals("sum") ? ValueExpr.COUNTCLASS : StringClass.getv(false, ExtInt.UNLIMITED);
                }});
            Expr sumExpr = IsClassExpr.create(key, group, IsClassType.SUMCONSISTENT);
//            Expr aggExpr = IsClassExpr.create(key, group, IsClassType.AGGCONSISTENT);
            table.writeRows(sql, new Query<String,String>(MapFact.singletonRev("key", key), MapFact.singleton("sum", sumExpr), sumExpr.getWhere()), baseClass, QueryEnvironment.empty); //, "agg", aggExpr

            Join<String> tableJoin = table.join(key);
            mSum.add(tableJoin.getExpr("sum"), 1);
//            mAgg.add(tableJoin.getExpr("agg"));

            usedTables.add(table);
        }

        // FormulaUnionExpr.create(new StringAggConcatenateFormulaImpl(","), mAgg.immutableList()) , "value",
        String incorrect = new Query<String,String>(MapFact.singletonRev("key", key), mSum.getExpr().compare(ValueExpr.COUNT, Compare.GREATER)).readSelect(sql, QueryEnvironment.empty);

        for(SingleKeyTableUsage<String> usedTable : usedTables.it())
            usedTable.drop(sql);

        if(!incorrect.isEmpty())
            return "---- Checking Classes Exclusiveness -----" + '\n' + incorrect;
        return "";
    }

    @Message("logics.checking.data.classes")
    public static String checkClasses(@ParamMessage StoredDataProperty property, SQLSession sql, BaseClass baseClass) throws SQLException, SQLHandledException {
        ImRevMap<ClassPropertyInterface, KeyExpr> mapKeys = property.getMapKeys();
        Where where = getIncorrectWhere(property, baseClass, mapKeys);
        Query<ClassPropertyInterface, String> query = new Query<ClassPropertyInterface, String>(mapKeys, where);

        String incorrect = query.readSelect(sql, QueryEnvironment.empty);
        if(!incorrect.isEmpty())
            return "---- Checking Classes for DataProperty : " + property + "-----" + '\n' + incorrect;
        return "";
    }

    public static Where getIncorrectWhere(StoredDataProperty property, BaseClass baseClass, ImRevMap<ClassPropertyInterface, KeyExpr> mapKeys) {
        Where correctClasses = Where.TRUE;
        Expr dataExpr = property.getInconsistentExpr(mapKeys, baseClass);
        for(int i=0,size= mapKeys.size();i<size;i++) {
            ValueClass interfaceClass = mapKeys.getKey(i).interfaceClass;
            if(interfaceClass instanceof CustomClass)
                correctClasses = correctClasses.and(mapKeys.getValue(i).isClass(((CustomClass) interfaceClass).getUpSet(), true));
        }
        if(property.value instanceof CustomClass)
            correctClasses = correctClasses.and(dataExpr.isClass(((CustomClass) property.value).getUpSet(), true));
        return dataExpr.getWhere().and(correctClasses.not());
    }

    // для оптимизации
    public DataChanges getUserDataChanges(DataProperty property, PropertyChange<ClassPropertyInterface> change) throws SQLException, SQLHandledException {
        Pair<ImMap<ClassPropertyInterface, DataObject>, ObjectValue> simple;
        if((simple = change.getSimple())!=null) {
            if(IsClassProperty.fitClasses(getCurrentClasses(simple.first), property.value,
                    simple.second instanceof DataObject ? getCurrentClass((DataObject) simple.second) : null))
                return new DataChanges(property, change);
            else
                return DataChanges.EMPTY;
        }
        return null;
    }

    public ConcreteClass getCurrentClass(DataObject value) throws SQLException, SQLHandledException {
        ConcreteObjectClass newClass = null;
        if(news!=null && value.objectClass instanceof ConcreteObjectClass) {
            if(newClasses.containsKey(value))
                newClass = newClasses.get(value);
            else {
                ImCol<ImMap<String, Object>> read = news.read(this, value);
                if(read.size()==0)
                    newClass = null;
                else
                    newClass = baseClass.findConcreteClassID((Integer) read.single().get("value"));
                newClasses.put(value, newClass);
            }
        }

        if(newClass==null)
            return value.objectClass;
        else
            return newClass;
    }

    public <K> ImMap<K, ConcreteClass> getCurrentClasses(ImMap<K, DataObject> map) throws SQLException, SQLHandledException {
        ImValueMap<K, ConcreteClass> mvResult = map.mapItValues(); // exception
        for(int i=0,size=map.size();i<size;i++)
            mvResult.mapValue(i, getCurrentClass(map.getValue(i)));
        return mvResult.immutableValue();
    }

    public ObjectValue getCurrentValue(ObjectValue value) throws SQLException, SQLHandledException {
        if(value instanceof NullValue)
            return value;
        else {
            DataObject dataObject = (DataObject)value;
            return new DataObject(dataObject.object, getCurrentClass(dataObject));
        }
    }

    public <K, V extends ObjectValue> ImMap<K, V> getCurrentObjects(ImMap<K, V> map) throws SQLException, SQLHandledException {
        ImValueMap<K, V> mvResult = map.mapItValues(); // exception
        for(int i=0,size=map.size();i<size;i++)
            mvResult.mapValue(i, (V) getCurrentValue(map.getValue(i)));
        return mvResult.immutableValue();
    }

    public DataObject getDataObject(ValueClass valueClass, Object value) throws SQLException, SQLHandledException {
        return baseClass.getDataObject(sql, value, valueClass.getUpSet());
    }

    public ObjectValue getObjectValue(ValueClass valueClass, Object value) throws SQLException, SQLHandledException {
        return baseClass.getObjectValue(sql, value, valueClass.getUpSet());
    }

    // узнает список изменений произошедших без него
    public ChangedData update(FormInstance<?> form) throws SQLException {
        // мн-во св-в constraints/persistent или все св-ва формы (то есть произвольное)
        assert activeForms.containsKey(form);

        UpdateChanges incrementChange = incrementChanges.get(form);
        boolean wasRestart = false;
        if(incrementChange!=null) // если не было restart
            //    to -> from или from = changes, to = пустому
            updateChanges.get(form).add(incrementChange);
            //    возвращаем to
        else { // иначе
            wasRestart = true;
            incrementChange = appliedChanges.remove(form);
            if(incrementChange==null) // совсем не было
                incrementChange = new UpdateChanges();
            UpdateChanges formChanges = new UpdateChanges(this);
            // from = changes (сбрасываем пометку что не было restart'а)
            updateChanges.put(form, formChanges);
            // возвращаем applied + changes
            incrementChange.add(formChanges);
        }
        incrementChanges.put(form,new UpdateChanges());

        return new ChangedData(CalcProperty.getDependsOnSet(incrementChange.properties), wasRestart);
    }

    public String applyMessage(BusinessLogics<?> BL) throws SQLException, SQLHandledException {
        return applyMessage(BL, null);
    }

    public String applyMessage(ExecutionContext context) throws SQLException, SQLHandledException {
        return applyMessage(context.getBL(), context);
    }

    public String applyMessage(BusinessLogics<?> BL, UserInteraction interaction) throws SQLException, SQLHandledException {
        if(apply(BL, interaction))
            return null;
        else
            return ThreadLocalContext.getLogMessage();
//            return ((LogMessageClientAction)BaseUtils.single(actions)).message;
    }

    public boolean apply(BusinessLogics BL, UpdateCurrentClasses update, UserInteraction interaction, ActionPropertyValueImplement applyAction) throws SQLException, SQLHandledException {
        return apply(BL, null, update, interaction, applyAction);
    }

    public boolean check(BusinessLogics BL, FormInstance form, UserInteraction interaction) throws SQLException, SQLHandledException {
        setApplyFilter(ApplyFilter.ONLYCHECK);

        boolean result = apply(BL, form, null, interaction, null);

        setApplyFilter(ApplyFilter.NO);
        return result;
    }

    public static <T extends PropertyInterface> boolean fitKeyClasses(CalcProperty<T> property, SinglePropertyTableUsage<T> change) {
        return change.getClassWhere(property.mapTable.mapKeys).means(property.mapTable.table.getClasses(), true); // если только по ширинам отличаются то тоже подходят
    }

    public static <T extends PropertyInterface> boolean fitClasses(CalcProperty<T> property, SinglePropertyTableUsage<T> change) {
        return change.getClassWhere(property.mapTable.mapKeys, property.field).means(property.fieldClassWhere, true); // если только по ширинам отличаются то тоже подходят
    }

    public static <T extends PropertyInterface> boolean notFitKeyClasses(CalcProperty<T> property, SinglePropertyTableUsage<T> change) {
        return change.getClassWhere(property.mapTable.mapKeys).and(property.mapTable.table.getClasses()).isFalse();
    }

    // см. splitSingleApplyClasses почему не используется сейчас
    public static <T extends PropertyInterface> boolean notFitClasses(CalcProperty<T> property, SinglePropertyTableUsage<T> change) {
        return change.getClassWhere(property.mapTable.mapKeys, property.field).and(property.fieldClassWhere).isFalse();
    }

    // для Single Apply
    private class EmptyModifier extends SessionModifier {

        private EmptyModifier() {
        }

        @Override
        public void addHintIncrement(CalcProperty property) {
            throw new RuntimeException("should not be"); // так как нет изменений то и hint не может придти
        }

        public ImSet<CalcProperty> calculateProperties() {
            return SetFact.EMPTY();
        }

        protected <P extends PropertyInterface> ModifyChange<P> calculateModifyChange(CalcProperty<P> property, PrereadRows<P> preread, FunctionSet<CalcProperty> overrided) {
            if(!preread.isEmpty())
                return new ModifyChange<P>(property.getNoChange(), preread, false);
            return null;
        }

        public SQLSession getSQL() {
            return sql;
        }

        public BaseClass getBaseClass() {
            return baseClass;
        }

        public QueryEnvironment getQueryEnv() {
            return env;
        }
    }
    public final EmptyModifier emptyModifier = new EmptyModifier();

    private <T extends PropertyInterface, D extends PropertyInterface> SinglePropertyTableUsage<T> splitApplySingleStored(CalcProperty<T> property, SinglePropertyTableUsage<T> changeTable, BusinessLogics<?> BL) throws SQLException, SQLHandledException {
        if(property.isEnabledSingleApply()) {
            Pair<SinglePropertyTableUsage<T>, SinglePropertyTableUsage<T>> split = property.splitSingleApplyClasses(changeTable, sql, baseClass, env);
            try {
                applySingleStored(property, split.first, BL);
            } catch (Throwable e) {
                split.second.drop(sql);
                throw ExceptionUtils.propagate(e, SQLException.class, SQLHandledException.class);
            }
            return split.second;
        }
        return changeTable;
    }

    private <T extends PropertyInterface, D extends PropertyInterface> void applySingleStored(CalcProperty<T> property, SinglePropertyTableUsage<T> change, BusinessLogics<?> BL) throws SQLException, SQLHandledException {
        assert isInTransaction();

        // assert что у change классы совпадают с property
        assert property.isSingleApplyStored();
        assert fitClasses(property, change); // проверяет гипотезу
        assert fitKeyClasses(property, change); // дополнительная проверка, она должна обеспечиваться тем что в change не должно быть замен null на null

        if(change.isEmpty())
            return;

        // тут есть assert что в increment+noUpdate не будет noDB, то есть не пересекется с NoEventModifier, то есть можно в любом порядке increment'ить
        IncrementTableProps increment = new IncrementTableProps(property, change);
        IncrementChangeProps noUpdate = new IncrementChangeProps(BL.getDataChangeEvents());

        OverrideSessionModifier modifier = new OverrideSessionModifier(new OverrideIncrementProps(noUpdate, increment), emptyModifier);

        ImOrderSet<CalcProperty> dependProps = BL.getSingleApplyDependFrom(property, this); // !!! важно в лексикографическом порядке должно быть

        try {
            if(neededProps!=null && !flush) { // придется отдельным прогоном чтобы правильную лексикографику сохранить
                for(CalcProperty<D> depend : dependProps)
                    if(!neededProps.contains(depend)) {
                        updatePendingApplyStart(property, change);
                        break;
                    }
            }
    
            for(CalcProperty<D> depend : dependProps) {
                assert depend.isSingleApplyStored() || (depend instanceof OldProperty && ((OldProperty)depend).scope.onlyDB());
    
                if(neededProps!=null) { // управление pending'ом
                    assert !flush || !pendingSingleTables.containsKey(depend); // assert что если flush то уже обработано (так как в обратном лексикографике идет)
                    if(!neededProps.contains(depend)) { // если не нужная связь не обновляем
                        if(!flush)
                            continue;
                    } else { // если нужная то уже обновили
                        if(flush) {
                            if(depend.isSingleApplyStored())
                                noUpdate.addNoChange(depend);
                            continue;
                        }
                    }
                }
    
                if(depend.isSingleApplyStored()) { // читаем новое значение, запускаем рекурсию
                    SinglePropertyTableUsage<D> dependChange = depend.readChangeTable(sql, modifier, baseClass, env);
                    applySingleStored((CalcProperty)depend, (SinglePropertyTableUsage)dependChange, BL);
                    noUpdate.addNoChange(depend); // докидываем noUpdate чтобы по нескольку раз одну ветку не отрабатывать
                } else {
                    SinglePropertyTableUsage<D> dependChange = ((OldProperty<D>) depend).property.readChangeTable(sql, modifier, baseClass, env);
                    updateApplyStart((OldProperty<D>) depend, dependChange);
                }
            }
            savePropertyChanges(property, change);
        } finally {
            change.drop(sql);
            modifier.clean(sql); // hint'ы и ссылки почистить
        }
    }

    private OrderedMap<CalcProperty, SinglePropertyTableUsage> pendingSingleTables = new OrderedMap<CalcProperty, SinglePropertyTableUsage>();
    boolean flush = false;

    private FunctionSet<CalcProperty> neededProps = null;
    private void startPendingSingles(ActionProperty action) throws SQLException {
        assert isInTransaction();

        if(!action.singleApply)
            return;

        neededProps = action.getDependsUsedProps();
    }

    private <P extends PropertyInterface> void updatePendingApplyStart(CalcProperty<P> property, SinglePropertyTableUsage<P> tableUsage) throws SQLException, SQLHandledException { // изврат конечно
        assert isInTransaction();

        SinglePropertyTableUsage<P> prevTable = pendingSingleTables.get(property);
        if(prevTable==null) {
            prevTable = property.createChangeTable();
            pendingSingleTables.put(property, prevTable);
        }
        ImRevMap<P, KeyExpr> mapKeys = property.getMapKeys();
        ModifyResult result = prevTable.modifyRows(sql, mapKeys, property.getExpr(mapKeys), tableUsage.join(mapKeys).getWhere(), baseClass, Modify.LEFT, env);// если он уже был в базе он не заместится
        if(!result.dataChanged() && prevTable.isEmpty())
            pendingSingleTables.remove(property);
    }

    // assert что в pendingSingleTables в обратном лексикографике
    private <T extends PropertyInterface> void flushPendingSingles(BusinessLogics BL) throws SQLException, SQLHandledException {
        assert isInTransaction();

        if(neededProps==null)
            return;

        flush = true;

        try {
            // сначала "возвращаем" изменения в базе на предыдущее
            for(Map.Entry<CalcProperty, SinglePropertyTableUsage> pendingSingle : pendingSingleTables.entrySet()) {
                CalcProperty<T> property = pendingSingle.getKey();
                SinglePropertyTableUsage<T> prevTable = pendingSingle.getValue();
    
                ImRevMap<T, KeyExpr> mapKeys = property.getMapKeys();
                SinglePropertyTableUsage<T> newTable = property.readChangeTable(sql, new PropertyChange<T>(mapKeys, property.getExpr(mapKeys), prevTable.join(mapKeys).getWhere()), baseClass, env);
                try {
                    savePropertyChanges(property, prevTable); // записываем старые изменения
                } finally {
                    prevTable.drop(sql);
                    pendingSingle.setValue(newTable); // сохраняем новые изменения
                }
            }
    
            for (Map.Entry<CalcProperty, SinglePropertyTableUsage> pendingSingle : pendingSingleTables.reverse().entrySet()) {
                try {
                    applySingleStored(pendingSingle.getKey(), pendingSingle.getValue(), BL);
                } finally {
                    pendingSingleTables.remove(pendingSingle.getKey());
                }
            }
        } finally {
            flush = false;
        }

        neededProps = null;
    }

    private void savePropertyChanges(Table implementTable, SessionTableUsage<KeyField, CalcProperty> changeTable) throws SQLException, SQLHandledException {
        savePropertyChanges(implementTable, changeTable.getValues().toMap(), changeTable.getKeys().toRevMap(), changeTable, true);
    }

    private <T extends PropertyInterface> void savePropertyChanges(CalcProperty<T> property, SinglePropertyTableUsage<T> change) throws SQLException, SQLHandledException {
        savePropertyChanges(property.mapTable.table, MapFact.singleton("value", (CalcProperty) property), property.mapTable.mapKeys, change, false);
    }

    private <K,V> void savePropertyChanges(Table implementTable, ImMap<V, CalcProperty> props, ImRevMap<K, KeyField> mapKeys, SessionTableUsage<K, V> changeTable, boolean onlyNotNull) throws SQLException, SQLHandledException {
        QueryBuilder<KeyField, PropertyField> modifyQuery = new QueryBuilder<KeyField, PropertyField>(implementTable);
        Join<V> join = changeTable.join(mapKeys.join(modifyQuery.getMapExprs()));
        
        Where reupdateWhere = null;
        Join<PropertyField> dbJoin = null;
        if(!DBManager.PROPERTY_REUPDATE && props.size() < Settings.get().getDisablePropertyReupdateCount()) {
            reupdateWhere = Where.TRUE;
            dbJoin = implementTable.join(modifyQuery.getMapExprs());
        }
                    
        for (int i=0,size=props.size();i<size;i++) {
            PropertyField field = props.getValue(i).field;
            Expr newExpr = join.getExpr(props.getKey(i));
            modifyQuery.addProperty(field, newExpr);

            if(reupdateWhere != null)
                reupdateWhere = reupdateWhere.and(newExpr.equalsFull(dbJoin.getExpr(field)));
        }
        modifyQuery.and(join.getWhere());
        
        if(reupdateWhere != null)
            modifyQuery.and(reupdateWhere.not());
        
        sql.modifyRecords(new ModifyQuery(implementTable, modifyQuery.getQuery(), env));
    }

    // хранит агрегированные изменения для уменьшения сложности (в транзакции очищает ветки от single applied)
    private IncrementTableProps apply = new IncrementTableProps();
    private OverrideSessionModifier applyModifier = new OverrideSessionModifier(apply, dataModifier);
    private OverrideSessionModifier onlyDataModifier = null;

    @Override
    public SessionModifier getModifier() {
        if(resolveModifier != null)
            return resolveModifier;

        if(isInSessionEvent())
            return sessionEventModifier;

        if(isInTransaction()) {
            if(onlyDataModifier!=null) {
                assert applyFilter == ApplyFilter.ONLY_DATA;
                return onlyDataModifier;
            }
            return applyModifier;
        }

        return dataModifier;
    }

    public Set<SessionDataProperty> recursiveUsed = SetFact.mAddRemoveSet();
    public List<ActionPropertyValueImplement> recursiveActions = ListFact.mAddRemoveList();
    public void addRecursion(ActionPropertyValueImplement action, ImSet<SessionDataProperty> sessionUsed, boolean singleApply) {
        action.property.singleApply = singleApply; // жестко конечно, но пока так
        recursiveActions.add(action);
        recursiveUsed.addAll(sessionUsed.toJavaSet());
    }

    public boolean apply(final BusinessLogics<?> BL, FormInstance form, UpdateCurrentClasses update, UserInteraction interaction, ActionPropertyValueImplement applyAction) throws SQLException, SQLHandledException {
        if(!hasChanges() && applyAction == null)
            return true;

        // до чтения persistent свойств в сессию
        if (applyObject == null) {
            try {
                applyObject = addObject(sessionClass);
                Integer changed = data.size();
                String dataChanged = "";
                for(Map.Entry<DataProperty, SinglePropertyTableUsage<ClassPropertyInterface>> entry : data.entrySet()){
                    dataChanged+=entry.getKey().getSID() + ": " + entry.getValue().getCount() + "\n";
                }
                BL.systemEventsLM.changesSession.change(dataChanged, DataSession.this, applyObject);
                currentSession.change(applyObject.object, DataSession.this);
                if (form != null){
                    BL.systemEventsLM.connectionSession.change(form.instanceFactory.connection, (ExecutionEnvironment)DataSession.this, applyObject);
                    Object ne = BL.reflectionLM.navigatorElementSID.read(form, new DataObject(form.entity.getSID(), StringClass.get(50)));
                    if(ne!=null) 
                        BL.systemEventsLM.navigatorElementSession.change(new DataObject(ne, BL.reflectionLM.navigatorElement), (ExecutionEnvironment)DataSession.this, applyObject);
                    BL.systemEventsLM.quantityAddedClassesSession.change(add.size(), DataSession.this, applyObject);
                    BL.systemEventsLM.quantityRemovedClassesSession.change(remove.size(), DataSession.this, applyObject);
                    BL.systemEventsLM.quantityChangedClassesSession.change(changed, DataSession.this, applyObject);
                }
            } catch (SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }

        executeSessionEvents(form);

        // очистим, так как в транзакции уже другой механизм используется, и старые increment'ы будут мешать
        dataModifier.clearHints(sql);

        return transactApply(BL, update, interaction, 0, applyAction);
    }

    private boolean transactApply(BusinessLogics<?> BL, UpdateCurrentClasses update, UserInteraction interaction, int autoAttemptCount, ActionPropertyValueImplement applyAction) throws SQLException, SQLHandledException {
//        assert !isInTransaction();
        startTransaction(update, BL);

        try {
            return recursiveApply(applyAction == null ? SetFact.<ActionPropertyValueImplement>EMPTYORDER() : SetFact.singletonOrder(applyAction), BL, update);
        } catch (Throwable t) { // assert'им что последняя SQL комманда, работа с транзакцией
            try {
                rollbackApply();
                
                if(t instanceof SQLHandledException && ((SQLHandledException)t).repeatApply(sql)) { // update conflict или deadlock или timeout - пробуем еще раз
                    boolean noTimeout = false;
                    if(t instanceof SQLTimeoutException && ((SQLTimeoutException)t).isTransactTimeout) {
                        if(interaction == null) {
                            autoAttemptCount++;
                            if(autoAttemptCount > Settings.get().getApplyAutoAttemptCountLimit()) {
                                ThreadLocalContext.delayUserInteraction(new LogMessageClientAction(getString("logics.server.apply.timeout.canceled"), true));                            
                                return false;
                            }
                        } else {
                            int option = (Integer)interaction.requestUserInteraction(new ConfirmClientAction("lsFusion",getString("logics.server.restart.transaction"), true, Settings.get().getDialogTransactionTimeout(), JOptionPane.CANCEL_OPTION));
                            if(option == JOptionPane.CANCEL_OPTION)
                                return false;
                            if(option == JOptionPane.YES_OPTION)
                                noTimeout = true;
                        }
                    }
                    
                    if(noTimeout)
                        sql.pushNoTransactTimeout();
                        
                    try {
                        return transactApply(BL, update, interaction, 0, applyAction);
                    } finally {
                        if(noTimeout)
                            sql.popNoTransactTimeout();
                    }
                }
            } catch (Throwable rs) {
                ServerLoggers.sqlHandLogger.info("ROLLBACK EXCEPTION " + rs.toString() + '\n' + ExceptionUtils.getStackTrace(rs));
            }

            throw ExceptionUtils.propagate(t, SQLException.class, SQLHandledException.class);
        }
    }

    private IdentityHashMap<FormInstance, Object> activeForms = new IdentityHashMap<FormInstance, Object>();
    public void registerForm(FormInstance form) throws SQLException {
        activeForms.put(form, true);

        dropFormCaches();
    }
    public void unregisterForm(FormInstance<?> form) throws SQLException {
        for(SessionModifier modifier : form.modifiers.values())
            modifier.clean(sql);

        activeForms.remove(form);
        incrementChanges.remove(form);
        appliedChanges.remove(form);
        updateChanges.remove(form);

        dropFormCaches();
    }
    private void dropFormCaches() throws SQLException {
        activeSessionEvents = null;
        sessionEventOldDepends = null;
    }
    public Set<FormInstance> getActiveForms() {
        return activeForms.keySet();
    }
    public <K> ImOrderSet<K> filterOrderEnv(ImOrderMap<K, SessionEnvEvent> elements) {
        return elements.filterOrderValues(new SFunctionSet<SessionEnvEvent>() {
            public boolean contains(SessionEnvEvent elements) {
                return elements.contains(DataSession.this);
            }});
    }

    public ApplyFilter applyFilter = ApplyFilter.NO;
    public void setApplyFilter(ApplyFilter applyFilter) {
        this.applyFilter = applyFilter;
    }
    
    private List<Runnable> rollbackInfo = new ArrayList<Runnable>(); 
    public void addRollbackInfo(Runnable run) {
        assert isInTransaction();
        
        rollbackInfo.add(run);
    }

    private boolean recursiveApply(ImOrderSet<ActionPropertyValueImplement> actions, BusinessLogics BL, UpdateCurrentClasses update) throws SQLException, SQLHandledException {
        // тоже нужен посередине, чтобы он успел dataproperty изменить до того как они обработаны
        ImOrderSet<Object> execActions = SetFact.addOrderExcl(actions, BL.getAppliedProperties(this));
        for (Object property : execActions) {
//            ServerLoggers.systemLogger.info(execActions.indexOf(property) + " of " + execActions.size() + " " + property);
            if(property instanceof ActionPropertyValueImplement) {
                startPendingSingles(((ActionPropertyValueImplement) property).property);
                ((ActionPropertyValueImplement)property).execute(this);
                if(!isInTransaction()) // если ушли из транзакции вываливаемся
                    return false;
                flushPendingSingles(BL);
            } else // постоянно-хранимые свойства
                readStored((CalcProperty<PropertyInterface>) property, BL);
        }

        if (applyFilter == ApplyFilter.ONLYCHECK) {
            cancel();
            return true;
        }

        sql.inconsistent = true;
        
        ImOrderSet<ActionPropertyValueImplement> updatedRecursiveActions;
        updatedRecursiveActions = updateRecursiveActions();
        if(update != null)
            update.update(this);

        try {
            // записываем в базу, то что туда еще не сохранено, приходится сохранять группами, так могут не подходить по классам
            packRemoveClasses(BL); // нужно делать до, так как классы должны быть актуальными, иначе спакует свои же изменения
            ImMap<ImplementTable, ImSet<CalcProperty>> groupTables = groupPropertiesByTables();
            for (int i=0,size=groupTables.size();i<size;i++) {
                ImplementTable table = groupTables.getKey(i);
                SessionTableUsage<KeyField, CalcProperty> saveTable = readSave(table, groupTables.getValue(i));
                try {
                    savePropertyChanges(table, saveTable);
                } finally {
                    saveTable.drop(sql);
                }
            }
    
            apply.clear(sql); // все сохраненные хинты обнуляем
            restart(false, SetFact.fromJavaSet(recursiveUsed)); // оставляем usedSessiona
        } finally {
            sql.inconsistent = false;
        }

        if(recursiveActions.size() > 0) {
            recursiveUsed.clear();
            recursiveActions.clear();
            return recursiveApply(updatedRecursiveActions, BL, update);
        }

        commitTransaction();

        return true;
    }

    private ImOrderSet<ActionPropertyValueImplement> updateRecursiveActions() throws SQLException, SQLHandledException {
        ImOrderSet<ActionPropertyValueImplement> updatedRecursiveActions = null;
        if(recursiveActions.size()>0) {
            recursiveUsed.add((SessionDataProperty) currentSession.property);

            updateCurrentClasses(filterKeys(data, recursiveUsed).values()); // обновить классы sessionDataProperty, которые остались

            MOrderExclSet<ActionPropertyValueImplement> mUpdatedRecursiveActions = SetFact.mOrderExclSet();
            for(ActionPropertyValueImplement recursiveAction : recursiveActions)
                mUpdatedRecursiveActions.exclAdd(recursiveAction.updateCurrentClasses(this));
            updatedRecursiveActions = mUpdatedRecursiveActions.immutableOrder();
        }
        return updatedRecursiveActions;
    }

    private void packRemoveClasses(BusinessLogics BL) throws SQLException, SQLHandledException {
        if(news==null)
            return;

        // проводим "мини-паковку", то есть удаляем все записи, у которых ключем является удаляемый объект
        for(ImplementTable table : BL.LM.tableFactory.getImplementTables(SetFact.fromJavaSet(remove))) {
            QueryBuilder<KeyField, PropertyField> query = new QueryBuilder<KeyField, PropertyField>(table);
            Where removeWhere = Where.FALSE;
            for (int i=0,size=table.mapFields.size();i<size;i++) {
                ValueClass value = table.mapFields.getValue(i);
                if (remove.contains(value)) {
                    Join<String> newJoin = news.join(query.getMapExprs().get(table.mapFields.getKey(i)));
                    removeWhere = removeWhere.or(newJoin.getWhere().and(isValueClass(newJoin.getExpr("value"), (CustomClass) value, usedNewClasses).not()));
                }
            }
            query.and(table.join(query.getMapExprs()).getWhere().and(removeWhere));
            sql.deleteRecords(new ModifyQuery(table, query.getQuery(), getQueryEnv()));
        }
    }

    @Message("message.session.apply.write")
    private <P extends PropertyInterface> void readStored(@ParamMessage CalcProperty<P> property, BusinessLogics<?> BL) throws SQLException, SQLHandledException {
        assert isInTransaction();
        assert property.isStored();
        if(property.hasChanges(getModifier())) {
            apply.add(property, splitApplySingleStored(property,
                    property.readChangeTable(sql, getModifier(), baseClass, env), BL));
        }
    }

    protected SQLSession getSQL() {
        return sql;
    }

    protected BaseClass getBaseClass() {
        return baseClass;
    }

    public QueryEnvironment getQueryEnv() {
        return env;
    }

    public final QueryEnvironment env = new QueryEnvironment() {
        public ParseInterface getSQLUser() {
            return new TypeObject(user.getCurrentUser().object, ObjectType.instance);
        }

        public ParseInterface getIsFullClient() {
            return new LogicalParseInterface() {
                public boolean isTrue() {
                    return computer.isFullClient();
                }
            };
        }

        public ParseInterface getSQLComputer() {
            return new TypeObject(computer.getCurrentComputer().object, ObjectType.instance);
        }

        public int getTransactTimeout() {
            return timeout.getTransactionTimeout();
        }

        public ParseInterface getIsServerRestarting() {
            return new LogicalParseInterface() {
                public boolean isTrue() {
                    return isServerRestarting.isServerRestarting();
                }
            };
        }
    };

    private static ImSet<ConcreteObjectClass> addUsed(Set<ConcreteObjectClass> used, ImSet<ConcreteObjectClass> add, boolean checkChanged) { // последний параметр оптимизация
        MFilterSet<ConcreteObjectClass> result = (checkChanged ? SetFact.mFilter(add) : null);
        for(ConcreteObjectClass element : add) {
            boolean added = used.add(element);
            if(checkChanged && added)
                result.keep(element);
        }
        return checkChanged ? SetFact.imFilter(result, add) : null;
    }

    private static ImSet<CustomClass> addUsed(ImSet<CustomClass> classes, final ImSet<ConcreteObjectClass> usedNews) {
        return classes.filterFn(new SFunctionSet<CustomClass>() {
            public boolean contains(CustomClass element) {
                UpClassSet upSet = element.getUpSet();
                for(ConcreteObjectClass usedClass : usedNews)
                    if(usedClass instanceof ConcreteCustomClass) {
                        ConcreteCustomClass customUsedClass = (ConcreteCustomClass) usedClass;
                        if(customUsedClass.inSet(upSet)) // добавляется еще один or
                            return true;
                    }
                return false;
            }});
    }

    private FunctionSet<CalcProperty<ClassPropertyInterface>> aspectChangeClass(ImSet<CustomClass> addClasses, ImSet<CustomClass> removeClasses, ImSet<ConcreteObjectClass> oldClasses, ImSet<ConcreteObjectClass> newClasses, ClassChange change) throws SQLException, SQLHandledException {
        checkTransaction(); // важно что, вначале

        if(news ==null)
            news = new SingleKeyPropertyUsage(ObjectType.instance, ObjectType.instance);

        SingleKeyPropertyUsage changeTable = null;
        try {
            if(change.needMaterialize(news)) { // safe modify, 2 раза повторяется, обобщать сложнее
                changeTable = change.materialize(sql, baseClass, env);
                change = changeTable.getChange();
            }
            ModifyResult tableChanged = change.modifyRows(news, sql, baseClass, Modify.MODIFY, env);
            if(!tableChanged.dataChanged()) {
                if(news.isEmpty())
                    news = null;
                return null;
            }
    
            boolean sourceNotChanged = !tableChanged.sourceChanged();
            this.newClasses.clear();
    
            // интересует изменения, только если не изменилась структура таблицы (в этом случае все равно все обновлять)
            ImSet<ConcreteObjectClass> changedUsedOld = addUsed(usedOldClasses, oldClasses, sourceNotChanged);
            ImSet<ConcreteObjectClass> changedUsedNew = addUsed(usedNewClasses, newClasses, sourceNotChanged);
    
            // оптимизация
            Pair<ImSet<CustomClass>, ImSet<CustomClass>> changedAdd = changeSingle(addClasses, change, singleAdd, add, singleRemove, remove, sourceNotChanged, sql, baseClass, env);
            Pair<ImSet<CustomClass>, ImSet<CustomClass>> changedRemove = changeSingle(removeClasses, change, singleRemove, remove, singleAdd, add, sourceNotChanged, sql, baseClass, env);
    
            ImSet<CustomClass> changedAddFull = sourceNotChanged ? changedAdd.first.addExcl(changedRemove.second).merge(addUsed(addClasses, changedUsedNew)) : null;
            ImSet<CustomClass> changeRemoveFull = sourceNotChanged ? changedRemove.first.addExcl(changedAdd.second).merge(addUsed(removeClasses, changedUsedNew)) : null;
    
            for(Map.Entry<DataProperty, SinglePropertyTableUsage<ClassPropertyInterface>> dataChange : data.entrySet()) { // удаляем существующие изменения
                DataProperty property = dataChange.getKey();
                if(property.depends(removeClasses)) { // оптимизация
                    SinglePropertyTableUsage<ClassPropertyInterface> table = dataChange.getValue();
    
                    // кейс с удалением, похож на getEventChange и в saveClassChanges - "мини паковка"
                    Where removeWhere = Where.FALSE;
                    ImRevMap<ClassPropertyInterface, KeyExpr> mapKeys = property.getMapKeys();
                    for(ClassPropertyInterface propertyInterface : property.interfaces)
                        if(SetFact.contains(propertyInterface.interfaceClass, removeClasses)) {
                            Join<String> newJoin = change.join(mapKeys.get(propertyInterface));
                            removeWhere = removeWhere.or(newJoin.getWhere().and(isValueClass(newJoin.getExpr("value"), (CustomClass) propertyInterface.interfaceClass, usedNewClasses).not()));
                        }
                    Join<String> join = table.join(mapKeys);
                    removeWhere = removeWhere.and(join.getWhere());
    
                    if(SetFact.contains(property.value, removeClasses)) {
                        Join<String> newJoin = change.join(join.getExpr("value"));
                        removeWhere = removeWhere.or(newJoin.getWhere().and(isValueClass(newJoin.getExpr("value"), (CustomClass) property.value, usedNewClasses).not()));
                    }
    
                    if(!removeWhere.isFalse()) { // оптимизация
                        if(changeTable == null && change.needMaterialize(table)) { // safe modify, 2 раза повторяется, обобщать сложнее
                            changeTable = change.materialize(sql, baseClass, env);
                            change = changeTable.getChange();
                        }
                        ModifyResult tableRemoveChanged = table.modifyRows(sql, new Query<ClassPropertyInterface, String>(mapKeys, removeWhere), baseClass, Modify.DELETE, getQueryEnv());
                        if(tableRemoveChanged.dataChanged())
                            dataModifier.eventChange(property, true, tableRemoveChanged.sourceChanged());
                    }
                }
            }
            return sourceNotChanged ? CustomClass.getProperties(changedAddFull, changeRemoveFull, changedUsedOld, changedUsedNew) : FullFunctionSet.<CalcProperty<ClassPropertyInterface>>instance();
        } finally {
            if(changeTable!=null)
                changeTable.drop(sql);
        }
    }

    private static Pair<ImSet<CustomClass>, ImSet<CustomClass>> changeSingle(ImSet<CustomClass> thisChanged, ClassChange thisChange, Map<CustomClass, DataObject> single, Set<CustomClass> changed, Map<CustomClass, DataObject> singleBack, Set<CustomClass> changedBack, boolean checkChanged, SQLSession sql, BaseClass baseClass, QueryEnvironment queryEnv) throws SQLException, SQLHandledException {
        MFilterSet<CustomClass> mNotChanged = checkChanged ? SetFact.mFilter(thisChanged) : null;
        MFilterSet<CustomClass> mChangedBack = checkChanged ? SetFact.mFilter(thisChanged) : null;

        for(CustomClass changeClass : thisChanged) {
            if(changed.contains(changeClass)) {
                if((single.remove(changeClass) == null) && checkChanged) // если не было
                    mNotChanged.keep(changeClass);
            } else {
                if(thisChange.keyValue !=null)
                    single.put(changeClass, thisChange.keyValue);
                changed.add(changeClass);
            }

            DataObject removeObject = singleBack.get(changeClass);
            if(removeObject!=null && thisChange.containsObject(sql, removeObject, baseClass, queryEnv)) {
                singleBack.remove(changeClass);
                changedBack.remove(changeClass);
                if(checkChanged)
                    mChangedBack.keep(changeClass);
            }
        }
        return checkChanged ? new Pair<ImSet<CustomClass>, ImSet<CustomClass>>(
                thisChanged.remove(SetFact.<CustomClass>imFilter(mNotChanged, thisChanged)), SetFact.<CustomClass>imFilter(mChangedBack, thisChanged)) : null;
    }

    private void aspectDropChanges(final DataProperty property) throws SQLException {
        SinglePropertyTableUsage<ClassPropertyInterface> dataChange = data.remove(property);
        if(dataChange!=null)
            dataChange.drop(sql);
    }

    private ModifyResult aspectChangeProperty(final DataProperty property, PropertyChange<ClassPropertyInterface> change) throws SQLException, SQLHandledException {
        checkTransaction();

        SinglePropertyTableUsage<ClassPropertyInterface> dataChange = data.get(property);
        if(dataChange == null) { // создадим таблицу, если не было
            dataChange = property.createChangeTable();
            data.put(property, dataChange);
        }
        ModifyResult result = change.modifyRows(dataChange, sql, baseClass, Modify.MODIFY, getQueryEnv());
        if(!result.dataChanged() && dataChange.isEmpty())
            data.remove(property);
        return result;
    }

    public void dropTables(ImSet<SessionDataProperty> keep) throws SQLException {
        for(SinglePropertyTableUsage<ClassPropertyInterface> dataTable : BaseUtils.filterNotKeys(data, keep).values())
            dataTable.drop(sql);
        if(news !=null)
            news.drop(sql);

        dataModifier.eventDataChanges(getChangedProps());
    }

    public DataSession getSession() {
        return this;
    }

    public FormInstance getFormInstance() {
        return null;
    }

    public boolean isInTransaction() {
        return isInTransaction;
    }

    public void cancel() throws SQLException {
        if(isInSessionEvent()) {
            inSessionEvent = false;
        }

        if(isInTransaction()) {
            rollbackApply();
            return;
        }

        restart(true, SetFact.<SessionDataProperty>EMPTY());
    }

    private void rollbackApply() throws SQLException {
        try {
            if(neededProps!=null) {
                for(SinglePropertyTableUsage table : pendingSingleTables.values())
                    table.drop(sql);
                pendingSingleTables.clear();
                neededProps = null;
                assert !flush;
            }
    
            recursiveUsed.clear();
            recursiveActions.clear();
    
            // не надо DROP'ать так как Rollback автоматически drop'ает все temporary таблицы
            apply.clear(sql);
            dataModifier.clearHints(sql); // drop'ем hint'ы (можно и без sql но пока не важно)
        } finally {
            rollbackTransaction();
        }
    }

    private <P extends PropertyInterface> void updateApplyStart(OldProperty<P> property, SinglePropertyTableUsage<P> tableUsage) throws SQLException, SQLHandledException { // изврат конечно
        assert isInTransaction();

        try {
            SinglePropertyTableUsage<P> prevTable = apply.getTable(property);
            if(prevTable==null) {
                prevTable = property.createChangeTable();
                apply.add(property, prevTable);
            }
            ImRevMap<P, KeyExpr> mapKeys = property.getMapKeys();
            ModifyResult tableChanges = prevTable.modifyRows(sql, mapKeys, property.getExpr(mapKeys), tableUsage.join(mapKeys).getWhere(), baseClass, Modify.LEFT, env); // если он уже был в базе он не заместится
            if(tableChanges.dataChanged())
                apply.eventChange(property, tableChanges.sourceChanged());
            else
                if(prevTable.isEmpty())
                    apply.remove(property, sql);
        } finally {
            tableUsage.drop(sql);
        }
    }

    public ImMap<ImplementTable, ImSet<CalcProperty>> groupPropertiesByTables() {
        return apply.getProperties().group(
                new BaseUtils.Group<ImplementTable, CalcProperty>() {
                    public ImplementTable group(CalcProperty key) {
                        if (key.isStored())
                            return key.mapTable.table;
                        assert key instanceof OldProperty;
                        return null;
                    }
                });
    }

    private final static Comparator<CalcProperty> propCompare = new Comparator<CalcProperty>() {
        public int compare(CalcProperty o1, CalcProperty o2) {
            return ((Integer)o1.getID()).compareTo(o2.getID());
        }
    };
    public <P extends PropertyInterface> SessionTableUsage<KeyField, CalcProperty> splitReadSave(ImplementTable table, ImSet<CalcProperty> properties) throws SQLException, SQLHandledException {
        IncrementChangeProps increment = new IncrementChangeProps();
        MAddSet<SessionTableUsage<KeyField, CalcProperty>> splitTables = SetFact.mAddSet();

        try {
            final int split = (int) Math.sqrt(properties.size());
            final ImOrderSet<CalcProperty> propertyOrder = properties.sort(propCompare).toOrderExclSet(); // для детерменированности
            for(ImSet<CalcProperty> splitProps : properties.group(new BaseUtils.Group<Integer, CalcProperty>() {
                        public Integer group(CalcProperty key) {
                            return propertyOrder.indexOf(key) / split;
                        }}).valueIt()) {
                SessionTableUsage<KeyField, CalcProperty> splitChangesTable = readSave(table, splitProps, getModifier());
                splitTables.add(splitChangesTable);
                for(CalcProperty<P> splitProp : splitProps)
                    increment.add(splitProp, SessionTableUsage.getChange(splitChangesTable, splitProp.mapTable.mapKeys, splitProp));
            }
    
            OverrideSessionModifier modifier = new OverrideSessionModifier(increment, emptyModifier);
            try {
                return readSave(table, properties, modifier);
            } finally {
                modifier.clean(sql);
            }
        } finally {
            for(SessionTableUsage<KeyField, CalcProperty> splitTable : splitTables)
                splitTable.drop(sql);
        }
    }

    @Message("message.increment.read.properties")
    public SessionTableUsage<KeyField, CalcProperty> readSave(ImplementTable table, @ParamMessage ImSet<CalcProperty> properties) throws SQLException, SQLHandledException {
        assert isInTransaction();

        final int split = Settings.get().getSplitIncrementApply();
        if(properties.size() > split) // если слишком много групп, разделим на несколько read'ов
            return splitReadSave(table, properties);

        return readSave(table, properties, getModifier());
    }

    public <P extends PropertyInterface> SessionTableUsage<KeyField, CalcProperty> readSave(ImplementTable table, ImSet<CalcProperty> properties, Modifier modifier) throws SQLException, SQLHandledException {
        // подготовили - теперь надо сохранить в курсор и записать классы
        SessionTableUsage<KeyField, CalcProperty> changeTable =
                new SessionTableUsage<KeyField, CalcProperty>(table.keys, properties.toOrderSet(), Field.<KeyField>typeGetter(),
                        new Type.Getter<CalcProperty>() {
                            public Type getType(CalcProperty key) {
                                return key.getType();
                            }
                        });
        changeTable.writeRows(sql, table.getReadSaveQuery(properties, modifier), baseClass, env);
        return changeTable;
    }

    public void pushVolatileStats() throws SQLException {
        sql.pushVolatileStats(null);
    }

    public void popVolatileStats() throws SQLException {
        sql.popVolatileStats(null);
    }
}
