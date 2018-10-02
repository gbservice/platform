package lsfusion.server.logics.property.actions.integration.hierarchy.json;

import com.google.common.base.Throwables;
import lsfusion.base.Pair;
import lsfusion.base.col.ListFact;
import lsfusion.base.col.interfaces.mutable.MList;
import lsfusion.server.logics.property.actions.integration.hierarchy.Node;
import lsfusion.server.logics.property.actions.integration.importing.hierarchy.json.JSONReader;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

public class JSONNode implements Node<JSONNode> {
    public final JSONObject element;

    public JSONNode(JSONObject element) {
        this.element = element;
    }
    
    public static JSONNode getJSONNode(Object object, boolean convertValue) throws JSONException {
        return new JSONNode(JSONReader.toJSONObject(object, convertValue));
    }

    public static Object putJSONNode(JSONNode node, boolean convertValue) throws JSONException {
        return JSONReader.fromJSONObject(node.element, convertValue);
    }

    @Override
    public JSONNode getNode(String key) {
        try {
            return getJSONNode(element.get(key), false); // no need to convert value for property group
        } catch (JSONException e) {
            throw Throwables.propagate(e);
        }
    }

    public void addNode(JSONNode node, String key, JSONNode childNode) {
        try {
            node.element.put(key, putJSONNode(childNode, false)); // no need to convert value for property group
        } catch (JSONException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public Object getValue(String key, boolean attr, Type type) throws ParseException {
        try {
            return type.parseJSON(element, key);
        } catch (JSONException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public Iterable<Pair<Object, JSONNode>> getMap(String key, boolean isIndex) {
        try {
            MList<Pair<Object, JSONNode>> mResult = ListFact.mList();
            Object child = element.get(key);
            if(isIndex) {
                JSONArray array = (JSONArray) child;
                for(int i=0,size=array.length();i<size;i++)
                    mResult.add(new Pair<Object, JSONNode>(i, getJSONNode(array.get(i), true)));
            } else {
                JSONObject object = (JSONObject) child;
                for (Iterator it = object.keys(); it.hasNext(); ) {
                    String objectKey = (String) it.next();
                    mResult.add(new Pair<Object, JSONNode>(objectKey, getJSONNode(object.get(objectKey), true)));
                }
            }
            return mResult.immutableList();
        } catch (JSONException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public JSONNode createNode() {
        return new JSONNode(new JSONObject());
    }

    public void addValue(JSONNode node, String key, boolean attr, String value) {
        try {
            node.element.put(key, type.formatJSON(value));
        } catch (JSONException e) {
            throw Throwables.propagate(e);
        }
    }

    public void addMap(JSONNode node, String key, boolean isIndex, Iterable<Pair<Object, JSONNode>> map) {
        try {
            Object addObject;
            if(isIndex) {
                JSONArray array = new JSONArray();
                for(Pair<Object, JSONNode> value : map)
                    array.put(putJSONNode(value.second, true));
                addObject = array;
            } else {
                JSONObject object = new JSONObject();
                for(Pair<Object, JSONNode> value : map)
                    object.put((String) value.first, putJSONNode(value.second, true));
                addObject = object;
            }                
            node.element.put(key, addObject);
        } catch (JSONException e) {
            throw Throwables.propagate(e);
        }
    }
}
