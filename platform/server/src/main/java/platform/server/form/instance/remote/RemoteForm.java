package platform.server.form.instance.remote;

import ar.com.fdvs.dj.core.DynamicJasperHelper;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import platform.base.BaseUtils;
import platform.interop.ClassViewType;
import platform.interop.Order;
import platform.interop.Scroll;
import platform.interop.action.CheckFailed;
import platform.interop.action.ClientAction;
import platform.interop.action.ClientApply;
import platform.interop.action.ResultClientAction;
import platform.interop.form.RemoteChanges;
import platform.interop.form.RemoteDialogInterface;
import platform.interop.form.RemoteFormInterface;
import platform.server.classes.ConcreteCustomClass;
import platform.server.classes.CustomClass;
import platform.server.form.entity.FormEntity;
import platform.server.form.entity.GroupObjectHierarchy;
import platform.server.form.entity.ObjectEntity;
import platform.server.form.instance.*;
import platform.server.form.instance.filter.FilterInstance;
import platform.server.form.instance.filter.RegularFilterGroupInstance;
import platform.server.form.instance.listener.CurrentClassListener;
import platform.server.form.view.FormView;
import platform.server.form.view.report.ReportDesignGenerator;
import platform.server.logics.BusinessLogics;
import platform.server.logics.DataObject;
import platform.server.serialization.SerializationType;
import platform.server.serialization.ServerSerializationPool;

import java.io.*;
import java.rmi.RemoteException;
import java.sql.SQLException;
import java.util.*;

// фасад для работы с клиентом
public class RemoteForm<T extends BusinessLogics<T>,F extends FormInstance<T>> extends platform.interop.remote.RemoteObject implements RemoteFormInterface {

    public final F form;
    public final FormView richDesign;

    private final CurrentClassListener currentClassListener;

    public RemoteForm(F form, FormView richDesign, int port, CurrentClassListener currentClassListener) throws RemoteException {
        super(port);
        
        this.form = form;
        this.richDesign = richDesign;
        this.currentClassListener = currentClassListener;
    }

    public byte[] getReportHierarchyByteArray() {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        try {
            ObjectOutputStream objOut = new ObjectOutputStream(outStream);
            Map<String, List<String>> dependencies = form.entity.getReportHierarchy().getReportHierarchyMap();
            objOut.writeUTF(GroupObjectHierarchy.rootNodeName);
            objOut.writeObject(dependencies);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return outStream.toByteArray();
    }

    public byte[] getReportDesignsByteArray(boolean toExcel) {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        try {
            ObjectOutputStream objOut = new ObjectOutputStream(outStream);
            Map<String, JasperDesign> res = getReportDesigns(toExcel);
            objOut.writeObject(res);
            return outStream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] getReportSourcesByteArray() {
        ReportSourceGenerator<T> sourceGenerator = new ReportSourceGenerator<T>(form, form.entity.getReportHierarchy());
        try {
            Map<String, ReportData> sources = sourceGenerator.generate();
            ReportSourceGenerator.ColumnGroupCaptionsData columnGroupCaptions = sourceGenerator.getColumnGroupCaptions();
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            DataOutputStream dataStream = new DataOutputStream(outStream);

            dataStream.writeInt(sources.size());
            for (Map.Entry<String, ReportData> source : sources.entrySet()) {
                dataStream.writeUTF(source.getKey());
                source.getValue().serialize(dataStream);
            }

            dataStream.writeInt(columnGroupCaptions.differentValues.size());
            for (Map.Entry<String, LinkedHashSet<List<Object>>> entry : columnGroupCaptions.differentValues.entrySet()) {
                dataStream.writeUTF(entry.getKey());
                LinkedHashSet<List<Object>> value = entry.getValue();
                dataStream.writeInt(value.size());
                for (List<Object> list : value) {
                    dataStream.writeInt(list.size());
                    for (Object obj : list) {
                        BaseUtils.serializeObject(dataStream, obj);
                    }
                }
            }
            
            for (Map.Entry<String, List<ObjectInstance>> entry : columnGroupCaptions.propertyObjects.entrySet()) {
                dataStream.writeUTF(entry.getKey());
                dataStream.writeInt(entry.getValue().size());
                for (ObjectInstance object : entry.getValue()) {
                    dataStream.writeInt(object.getID());
                }
            }

            return outStream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, JasperDesign> getCustomReportDesigns() {
        try {
            GroupObjectHierarchy.ReportHierarchy hierarchy = form.entity.getReportHierarchy();
            Map<String, JasperDesign> designs = new HashMap<String, JasperDesign>();
            List<String> ids = new ArrayList<String>();
            ids.add(GroupObjectHierarchy.rootNodeName);
            for (GroupObjectHierarchy.ReportNode node : hierarchy.getAllNodes()) {
                ids.add(node.getID());
            }
            for (String id : ids) {
                File subReportFile = new File(getCustomReportName(id));
                if (subReportFile.exists()) {
                    JasperDesign subreport = JRXmlLoader.load(subReportFile);
                    designs.put(id, subreport);
                }
            }
            return designs;
        } catch (JRException e) {
            return null;
        }
    }

    private Map<String, JasperDesign> getReportDesigns(boolean toExcel) {
        if (hasCustomReportDesign())  {
            Map<String, JasperDesign> designs = getCustomReportDesigns();
            if (designs != null) {
                return designs;
            }
        }

        Set<Integer> hidedGroupsId = new HashSet<Integer>();
        for (GroupObjectInstance group : form.groups) {
            if (group.curClassView == ClassViewType.HIDE) {
                hidedGroupsId.add(group.getID());
            }
        }
        try {
            ReportDesignGenerator generator =
                    new ReportDesignGenerator(richDesign, form.entity.getReportHierarchy(), hidedGroupsId, toExcel);
            Map<String, JasperDesign> designs = generator.generate();
            for (Map.Entry<String, JasperDesign> entry : designs.entrySet()) {
                String id = entry.getKey();
                String reportName = getAutoReportName(id);
                DynamicJasperHelper.generateJRXML(JasperCompileManager.compileReport(entry.getValue()), "UTF-8", reportName);
            }
            return designs;
        } catch (JRException e) {
            throw new RuntimeException("Ошибка при создании дизайна", e);
        }
    }

    private String getCustomReportName(String name) {
        if (name.equals(GroupObjectHierarchy.rootNodeName)) {
            return "reports/custom/" + getSID() + ".jrxml";
        } else {
            return "reports/custom/" + getSID() + "_" + name + ".jrxml";
        }
    }

    private String getAutoReportName(String name) {
        if (name.equals(GroupObjectHierarchy.rootNodeName)) {
            return "reports/auto/" + getSID() + ".jrxml";
        } else {
            return "reports/auto/" + getSID() + "_" + name + ".jrxml";
        }
    }

    public boolean hasCustomReportDesign() {
        return new File(getCustomReportName(GroupObjectHierarchy.rootNodeName)).exists();
    }

    public byte[] getRichDesignByteArray() {

        //будем использовать стандартный OutputStream, чтобы кол-во передаваемых данных было бы как можно меньше
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        try {
            new ServerSerializationPool().serializeObject(new DataOutputStream(outStream), richDesign, SerializationType.GENERAL);
//            richDesign.serialize(new DataOutputStream(outStream));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return outStream.toByteArray();
    }

    public void changePageSize(int groupID, int pageSize) throws RemoteException {

        GroupObjectInstance groupObject = form.getGroupObjectInstance(groupID);
        form.changePageSize(groupObject, pageSize);
    }

    public void gainedFocus() {
        form.gainedFocus();
    }

    private byte[] getFormChangesByteArray() {

        ByteArrayOutputStream outStream = new ByteArrayOutputStream();

        try {
            form.endApply().serialize(new DataOutputStream(outStream));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return outStream.toByteArray();
    }

    public RemoteChanges getRemoteChanges() {
        byte[] formChanges = getFormChangesByteArray();

        List<ClientAction> remoteActions = actions;
        actions = new ArrayList<ClientAction>();

        int objectClassID = 0;
        if(updateCurrentClass!=null) {
            ConcreteCustomClass currentClass = form.getObjectClass(updateCurrentClass);
            if(currentClass != null && currentClassListener.changeCurrentClass(currentClass))
                objectClassID = currentClass.ID;

            updateCurrentClass = null;
        }
        return new RemoteChanges(formChanges, remoteActions, objectClassID);
    }

    public void changeGroupObject(int groupID, byte[] value) {
        
        GroupObjectInstance groupObject = form.getGroupObjectInstance(groupID);
        try {

            DataInputStream inStream = new DataInputStream(new ByteArrayInputStream(value));
            // считаем ключи и найдем groupObjectValue
            Map<ObjectInstance,Object> mapValues = new HashMap<ObjectInstance, Object>();
            for(ObjectInstance object : groupObject.objects)
                mapValues.put(object, BaseUtils.deserializeObject(inStream));
            form.changeGroupObject(groupObject, groupObject.findGroupObjectValue(mapValues));

            updateCurrentClass = groupObject.objects.iterator().next();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void changeGroupObject(int groupID, byte changeType) {
        try {
            GroupObjectInstance groupObject = form.getGroupObjectInstance(groupID);
            form.changeGroupObject(groupObject, Scroll.deserialize(changeType));

            updateCurrentClass = groupObject.objects.iterator().next();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<ClientAction> actions = new ArrayList<ClientAction>();
    private ObjectInstance updateCurrentClass = null;

    public void changePropertyDraw(int propertyID, byte[] object, boolean all, byte[] columnKeys) {
        try {
            PropertyDrawInstance propertyDraw = form.getPropertyDraw(propertyID);
            Map<ObjectInstance, DataObject> keys = deserializeKeys(propertyDraw, columnKeys);
            actions.addAll(form.changeProperty(propertyDraw, BaseUtils.deserializeObject(object), this, all, keys));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void addObject(int objectID, int classID) {
        CustomObjectInstance object = (CustomObjectInstance) form.getObjectInstance(objectID);
        try {
            form.addObject(object, (classID == -1) ? null : object.baseClass.findConcreteClassID(classID));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void changeClass(int objectID, int classID) {
        try {
            CustomObjectInstance objectImplement = (CustomObjectInstance) form.getObjectInstance(objectID);
            form.changeClass(objectImplement, classID);

            updateCurrentClass = objectImplement;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean canChangeClass(int objectID) throws RemoteException {
        return form.canChangeClass((CustomObjectInstance)form.getObjectInstance(objectID));
    }

    public void changeGridClass(int objectID,int idClass) {
        ((CustomObjectInstance) form.getObjectInstance(objectID)).changeGridClass(idClass);
    }

    public void switchClassView(int groupID) {
        form.switchClassView(form.getGroupObjectInstance(groupID));
    }

    public void changeClassView(int groupID, ClassViewType classView) {
        form.changeClassView(form.getGroupObjectInstance(groupID), classView);
    }

    private Map<ObjectInstance, DataObject> deserializeKeys(PropertyDrawInstance<?> propertyDraw, byte[] columnKeys) throws IOException {
        DataInputStream inStream = new DataInputStream(new ByteArrayInputStream(columnKeys));
        Map<ObjectInstance, DataObject> keys = new HashMap<ObjectInstance, DataObject>();
        for (GroupObjectInstance groupInstance : propertyDraw.columnGroupObjects) {
            Map<ObjectInstance, Object> mapValues = new HashMap<ObjectInstance, Object>();
            boolean found = false;
            for (ObjectInstance objectInstance : groupInstance.objects) {
                Object val = BaseUtils.deserializeObject(inStream);
                if (val != null) {
                    mapValues.put(objectInstance, val);
                    found = true;
                }
            }

            if (found) {
                keys.putAll( groupInstance.findGroupObjectValue(mapValues) );
            }
        }
        return keys;
    }

    public void changePropertyOrder(int propertyID, byte modiType, byte[] columnKeys) throws RemoteException {
        PropertyDrawInstance<?> propertyDraw = form.getPropertyDraw(propertyID);
        try {
            Map<ObjectInstance, DataObject> keys = deserializeKeys(propertyDraw, columnKeys);
            propertyDraw.toDraw.changeOrder(propertyDraw.propertyObject.getRemappedPropertyObject(keys), Order.deserialize(modiType));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void clearUserFilters() {

        for(GroupObjectInstance group : form.groups)
            group.clearUserFilters();
    }

    public void addFilter(byte[] state) {
        try {
            FilterInstance filter = FilterInstance.deserialize(new DataInputStream(new ByteArrayInputStream(state)), form);
            filter.getApplyObject().addUserFilter(filter);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void setRegularFilter(int groupID, int filterID) {
        RegularFilterGroupInstance filterGroup = form.getRegularFilterGroup(groupID);
        form.setRegularFilter(filterGroup, filterGroup.getFilter(filterID));
    }

    public int getID() {
        return form.entity.getID();
    }

    public String getSID() {
        return form.entity.sID;
    }

    public void refreshData() {
        try {
            form.refreshData();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean hasClientApply() {
        return form.entity.hasClientApply();
    }

    public ClientApply applyClientChanges() throws RemoteException {
        try {
            String result = form.applyChanges(true);
            if(result!=null)
                return new CheckFailed(result);
            else
                return form.entity.getClientApply(form);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void confirmClientChanges(Object clientResult) throws RemoteException {
        String checkClientApply = form.entity.checkClientApply(clientResult);
        try {
            if(checkClientApply!=null) {
                form.rollbackApply();
                actions.add(new ResultClientAction(checkClientApply, true));
            } else {
                form.commitApply();
                actions.add(new ResultClientAction("Изменения были удачно записаны...", false));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void rollbackClientChanges() throws RemoteException {
        try {
            form.rollbackApply();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void applyChanges() throws RemoteException {
        try {
            form.applyActionChanges(false, actions);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void cancelChanges() {
        try {
            form.cancelChanges();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] getBaseClassByteArray(int objectID) {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        try {
            form.getObjectInstance(objectID).getBaseClass().serialize(new DataOutputStream(outStream));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return outStream.toByteArray();
    }

    public byte[] getChildClassesByteArray(int objectID, int classID) {

        List<CustomClass> childClasses = (((CustomObjectInstance)form.getObjectInstance(objectID)).baseClass).findClassID(classID).children;

        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        DataOutputStream dataStream = new DataOutputStream(outStream);
        try {
            dataStream.writeInt(childClasses.size());
            for (CustomClass cls : childClasses)
                cls.serialize(dataStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return outStream.toByteArray();
    }

    public byte[] getPropertyChangeType(int propertyID) {

        try {
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            DataOutputStream dataStream = new DataOutputStream(outStream);

            form.serializePropertyEditorType(dataStream,form.getPropertyDraw(propertyID));

            return outStream.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public RemoteDialogInterface createClassPropertyDialog(int viewID, int value) throws RemoteException {
        try {
            DialogInstance<T> dialogForm = form.createClassPropertyDialog(viewID, value);
            return new RemoteDialog<T>(dialogForm,dialogForm.entity.getRichDesign(),exportPort, currentClassListener);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public RemoteDialogInterface createEditorPropertyDialog(int viewID) throws RemoteException {
        try {
            DialogInstance<T> dialogForm = form.createEditorPropertyDialog(viewID);
            return new RemoteDialog<T>(dialogForm,dialogForm.entity.getRichDesign(),exportPort, currentClassListener);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public RemoteDialogInterface createObjectDialog(int objectID) {
        try {
            DialogInstance<T> dialogForm = form.createObjectDialog(objectID);
            return new RemoteDialog<T>(dialogForm,dialogForm.entity.getRichDesign(), exportPort, currentClassListener);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public RemoteDialogInterface createObjectDialogWithValue(int objectID, int value) throws RemoteException {
        try {
            DialogInstance<T> dialogForm = form.createObjectDialog(objectID, value);
            return new RemoteDialog<T>(dialogForm,dialogForm.entity.getRichDesign(), exportPort, currentClassListener);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public RemoteFormInterface createForm(FormEntity formEntity, Map<ObjectEntity, DataObject> mapObjects) {
        try {
            FormInstance<T> formInstance = form.createForm(formEntity, mapObjects);
            return new RemoteForm<T, FormInstance<T>>(formInstance, formEntity.getRichDesign(), exportPort, currentClassListener);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
