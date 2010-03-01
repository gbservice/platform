package platform.server.data;

import platform.base.BaseUtils;
import platform.base.OrderedMap;
import platform.server.caches.ParamLazy;
import platform.server.caches.TwinLazy;
import platform.server.caches.HashContext;
import platform.server.data.where.classes.ClassExprWhere;
import platform.server.data.where.classes.ClassWhere;
import platform.server.data.query.*;
import platform.server.data.expr.BaseExpr;
import platform.server.data.expr.Expr;
import platform.server.data.expr.InnerExpr;
import platform.server.data.expr.KeyExpr;
import platform.server.data.expr.ValueExpr;
import platform.server.data.expr.cases.CaseExpr;
import platform.server.data.expr.cases.MapCase;
import platform.server.data.translator.KeyTranslator;
import platform.server.data.translator.QueryTranslator;
import platform.server.data.expr.where.MapWhere;
import platform.server.data.sql.SQLSyntax;
import platform.server.data.type.Type;
import platform.server.data.SQLSession;
import platform.server.data.where.DataWhere;
import platform.server.data.where.DataWhereSet;
import platform.server.data.where.Where;
import platform.server.classes.BaseClass;
import platform.server.logics.DataObject;
import platform.server.logics.ObjectValue;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

public class Table implements MapKeysInterface<KeyField> {
    public final String name;
    public final Collection<KeyField> keys = new ArrayList<KeyField>();
    public final Collection<PropertyField> properties = new ArrayList<PropertyField>();

    public Map<KeyField, KeyExpr> getMapKeys() {
        Map<KeyField,KeyExpr> result = new HashMap<KeyField, KeyExpr>();
        for(KeyField key : keys)
            result.put(key,new KeyExpr(key.name));
        return result;
    }

    public Table(String name,ClassWhere<KeyField> classes) {
        this.name = name;
        this.classes = classes;
        propertyClasses = new HashMap<PropertyField, ClassWhere<Field>>();
    }

    public Table(String name) {
        this.name = name;
        classes = new ClassWhere<KeyField>();
        propertyClasses = new HashMap<PropertyField, ClassWhere<Field>>();
    }

    public Table(String name,ClassWhere<KeyField> classes,Map<PropertyField, ClassWhere<Field>> propertyClasses) {
        this.name = name;
        this.classes = classes;
        this.propertyClasses = propertyClasses;
    }

    public String getName(SQLSyntax Syntax) {
        return name;
    }

    public String toString() {
        return name;
    }

    public KeyField findKey(String name) {
        for(KeyField key : keys)
            if(key.name.equals(name))
                return key;
        return null;
    }

    public PropertyField findProperty(String name) {
        for(PropertyField property : properties)
            if(property.name.equals(name))
                return property;
        return null;
    }

    public void serialize(DataOutputStream outStream) throws IOException {
        outStream.writeUTF(name);
            outStream.writeInt(keys.size());
        for(KeyField key : keys)
            key.serialize(outStream);
        outStream.writeInt(properties.size());
        for(PropertyField property : properties)
            property.serialize(outStream);
    }


    public Table(DataInputStream inStream) throws IOException {
        name = inStream.readUTF();
        int keysNum = inStream.readInt();
        for(int i=0;i<keysNum;i++)
            keys.add((KeyField) Field.deserialize(inStream));
        int propNum = inStream.readInt();
        for(int i=0;i<propNum;i++)
            properties.add((PropertyField) Field.deserialize(inStream));

        classes = new ClassWhere<KeyField>();
        propertyClasses = new HashMap<PropertyField, ClassWhere<Field>>();
    }

    public OrderedMap<Map<KeyField,DataObject>,Map<PropertyField,ObjectValue>> read(SQLSession session, BaseClass baseClass) throws SQLException {
        Query<KeyField, PropertyField> query = new Query<KeyField,PropertyField>(this);
        platform.server.data.query.Join<PropertyField> tableJoin = join(query.mapKeys);
        query.properties.putAll(tableJoin.getExprs());
        query.and(tableJoin.getWhere());
        return query.executeClasses(session, baseClass);
    }

    protected ClassWhere<KeyField> classes; // по сути условия на null'ы в том числе

    protected final Map<PropertyField,ClassWhere<Field>> propertyClasses;

    @Override
    public boolean equals(Object obj) {
        return this == obj || getClass()==obj.getClass() && name.equals(((Table)obj).name) && classes.equals(((Table)obj).classes) && propertyClasses.equals(((Table)obj).propertyClasses);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    public void out(SQLSession session) throws SQLException {
        Query<KeyField,PropertyField> query = new Query<KeyField,PropertyField>(this);
        platform.server.data.query.Join<PropertyField> join = joinAnd(query.mapKeys);
        query.and(join.getWhere());
        query.properties.putAll(join.getExprs());
        query.outSelect(session);
    }

    public platform.server.data.query.Join<PropertyField> join(Map<KeyField, ? extends Expr> joinImplement) {
        JoinCaseList<PropertyField> result = new JoinCaseList<PropertyField>();
        for(MapCase<KeyField> caseJoin : CaseExpr.pullCases(joinImplement))
            result.add(new JoinCase<PropertyField>(caseJoin.where,joinAnd(caseJoin.data)));
        return new CaseJoin<PropertyField>(result, properties);
    }

    public platform.server.data.query.Join<PropertyField> joinAnd(Map<KeyField, ? extends BaseExpr> joinImplement) {
        return new Join(joinImplement);
    }

    public class Join extends platform.server.data.query.Join<PropertyField> implements InnerJoin {

        public Map<KeyField, BaseExpr> joins;

        public Join(Map<KeyField, ? extends BaseExpr> iJoins) {
            joins = (Map<KeyField, BaseExpr>) iJoins;
            assert (joins.size()==keys.size());
        }

        @TwinLazy
        Where getJoinsWhere() {
            return platform.server.data.expr.Expr.getWhere(joins);
        }

        public DataWhereSet getJoinFollows() {
            return InnerExpr.getExprFollows(joins);
        }

        protected DataWhereSet getExprFollows() {
            return ((IsIn)getWhere()).getFollows();
        }

        @TwinLazy
        public platform.server.data.expr.Expr getExpr(PropertyField property) {
            return BaseExpr.create(new Expr(property));
        }
        @TwinLazy
        public Where getWhere() {
            return DataWhere.create(new IsIn());
        }

        // интерфейсы для translateDirect
        public Expr getDirectExpr(PropertyField property) {
            return new Expr(property);
        }
        public IsIn getDirectWhere() {
            return new IsIn();
        }

        public Collection<PropertyField> getProperties() {
            return Table.this.properties;
        }

        public int hashContext(HashContext hashContext) {
            int hash = Table.this.hashCode()*31;
                // нужен симметричный хэш относительно выражений
            for(Map.Entry<KeyField, BaseExpr> join : joins.entrySet())
                hash += join.getKey().hashCode() ^ join.getValue().hashContext(hashContext);
            return hash;
        }

        @ParamLazy
        public Join translateDirect(KeyTranslator translator) {
            return new Join(translator.translateDirect(joins));
        }

        @ParamLazy
        public platform.server.data.query.Join<PropertyField> translateQuery(QueryTranslator translator) {
            return join(translator.translate(joins));
        }

        public String getName(SQLSyntax syntax) {
            return Table.this.getName(syntax);
        }

        private Table getTable() {
            return Table.this;
        }

        @Override
        public boolean equals(Object o) {
            return this == o || o instanceof Join && Table.this.equals(((Join) o).getTable()) && joins.equals(((Join) o).joins);
        }

        public boolean isIn(DataWhereSet set) {
            return set.contains((DataWhere)getWhere());
        }

        @Override
        public int hashCode() {
            return hashContext(new HashContext(){
                public int hash(KeyExpr expr) {
                    return expr.hashCode();
                }

                public int hash(ValueExpr expr) {
                    return expr.hashCode();
                }
            });


        }

        public class IsIn extends DataWhere implements JoinData {

            public String getFirstKey() {
                if(keys.size()==0) {
                    assert Table.this.name.equals("global");
                    return "dumb";
                }
                return keys.iterator().next().toString();
            }

            public void enumerate(SourceEnumerator enumerator) {
                enumerator.fill(joins);
            }

            public Join getJoin() {
                return Join.this;
            }

            public InnerJoin getFJGroup() {
                return Join.this;
            }

            public InnerJoins getInnerJoins() {
                return new InnerJoins(Join.this,this);
            }

            protected void fillDataJoinWheres(MapWhere<JoinData> joins, Where andWhere) {
                joins.add(this,andWhere);
            }

            public String getSource(CompileSource compile) {
                return compile.getSource(this);
            }

            public String toString() {
                return "IN JOIN " + Join.this.toString();
            }

            public Where translateDirect(KeyTranslator translator) {
                return Join.this.translateDirect(translator).getDirectWhere();
            }
            public Where translateQuery(QueryTranslator translator) {
                return Join.this.translateQuery(translator).getWhere();
            }

            protected DataWhereSet getExprFollows() {
                return getJoinFollows();
            }

            public platform.server.data.expr.Expr getFJExpr() {
                return ValueExpr.get(this);
            }

            public String getFJString(String exprFJ) {
                return exprFJ + " IS NOT NULL";
            }

            public ClassExprWhere calculateClassWhere() {
                return classes.map(joins).and(getJoinsWhere().getClassWhere());
            }

            public boolean twins(AbstractSourceJoin o) {
                return Join.this.equals(((IsIn) o).getJoin());
            }

            public int hashContext(HashContext hashContext) {
                return Join.this.hashContext(hashContext);
            }
        }

        public class Expr extends InnerExpr {

            public final PropertyField property;

            // напрямую может конструироваться только при полной уверенности что не null
            private Expr(PropertyField iProperty) {
                property = iProperty;
            }

            public platform.server.data.expr.Expr translateQuery(QueryTranslator translator) {
                return Join.this.translateQuery(translator).getExpr(property);
            }

            public Expr translateDirect(KeyTranslator translator) {
                return Join.this.translateDirect(translator).getDirectExpr(property);
            }

            public void enumerate(SourceEnumerator enumerator) {
                enumerator.fill(joins);
            }

            public Join getJoin() {
                return Join.this;
            }

            public InnerJoin getFJGroup() {
                return Join.this;
            }

            public String toString() {
                return Join.this.toString() + "." + property;
            }

            public Type getType(Where where) {
                return property.type;
            }

            // возвращает Where без следствий
            public Where calculateWhere() {
                return new NotNull();
            }

            @Override
            public boolean twins(AbstractSourceJoin o) {
                return Join.this.equals(((Expr) o).getJoin()) && property.equals(((Expr) o).property);
            }

            public int hashContext(HashContext hashContext) {
                return Join.this.hashContext(hashContext)*31+property.hashCode();
            }

            public String getSource(CompileSource compile) {
                return compile.getSource(this);
            }

            public class NotNull extends InnerExpr.NotNull {

                protected DataWhereSet getExprFollows() {
                    return Join.this.getExprFollows();
                }

                public InnerJoins getInnerJoins() {
                    return new InnerJoins(Join.this,this);
                }

                public ClassExprWhere calculateClassWhere() {
                    return propertyClasses.get(property).map(BaseUtils.merge(joins,Collections.singletonMap(property, Expr.this))).and(Join.this.getJoinsWhere().getClassWhere());
                }
            }
        }

        @Override
        public String toString() {
            return Table.this.toString();
        }
    }
}
