package platform.fullclient.layout;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.design.*;
import platform.base.BaseUtils;
import platform.base.Pair;
import platform.interop.form.RemoteFormInterface;
import platform.interop.form.ReportConstants;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.*;

/**
 * User: DAle
 * Date: 16.09.2010
 * Time: 15:06:37
 */

public class ReportGenerator {
    private final String rootID;
    private final Map<String, List<String>> hierarchy;
    private final Map<String, JasperDesign> designs;
    private final Map<String, ClientReportData> data;
    private final Map<String, List<List<Object>>> compositeData;
    private final Map<String, List<Integer>> compositeFieldsObjects;

    private static class sourcesGenerationOutput {
        public Map<String, ClientReportData> data;
        public Map<String, List<List<Object>>> compositeData;
        public Map<String, List<Integer>> compositeFieldsObjects;
    }

    public ReportGenerator(RemoteFormInterface remoteForm, boolean toExcel) throws IOException, ClassNotFoundException, JRException {
        Pair<String, Map<String, List<String>>> hpair = retrieveReportHierarchy(remoteForm);
        rootID = hpair.first;
        hierarchy = hpair.second;
        designs = retrieveReportDesigns(remoteForm, toExcel);

        sourcesGenerationOutput output = retrieveReportSources(remoteForm);
        data = output.data;
        compositeData = output.compositeData;
        compositeFieldsObjects = output.compositeFieldsObjects;

        transformDesigns();
    }

    public JasperPrint createReport() throws JRException {
        
        Pair<Map<String, Object>, JRDataSource> compileParams = prepareReportSources();

        JasperReport report = JasperCompileManager.compileReport(designs.get(rootID));
        return JasperFillManager.fillReport(report, compileParams.first, compileParams.second);
    }

    private Pair<Map<String, Object>, JRDataSource> prepareReportSources() throws JRException {
        Map<String, Object> params = new HashMap<String, Object>();
        for (String childID : hierarchy.get(rootID)) {
            iterateChildSubreports(childID, params);
        }

        ReportRootDataSource rootSource = new ReportRootDataSource();
        return new Pair<Map<String, Object>, JRDataSource>(params, rootSource);
    }

    private ReportDependentDataSource iterateChildSubreports(String parentID, Map<String, Object> params) throws JRException {
        Map<String, Object> localParams = new HashMap<String, Object>();
        List<ReportDependentDataSource> childSources = new ArrayList<ReportDependentDataSource>();
        ReportDependentDataSource source = new ReportDependentDataSource(data.get(parentID), childSources);

        for (String childID : hierarchy.get(parentID)) {
            ReportDependentDataSource childSource = iterateChildSubreports(childID, localParams);
            childSources.add(childSource);
        }

        params.put(parentID + ReportConstants.reportSuffix, JasperCompileManager.compileReport(designs.get(parentID)));
        params.put(parentID + ReportConstants.sourceSuffix, source);
        params.put(parentID + ReportConstants.paramsSuffix, localParams);
        return source;
    }

    private static Pair<String, Map<String, java.util.List<String>>> retrieveReportHierarchy(RemoteFormInterface remoteForm) throws IOException, ClassNotFoundException {
        byte[] hierarchyArray = remoteForm.getReportHierarchyByteArray();
        ObjectInputStream objStream = new ObjectInputStream(new ByteArrayInputStream(hierarchyArray));
        String rootID = objStream.readUTF();
        Map<String, java.util.List<String>> hierarchy = (Map<String, java.util.List<String>>) objStream.readObject();
        return new Pair<String, Map<String, java.util.List<String>>>(rootID, hierarchy);
    }

    private static Map<String, JasperDesign> retrieveReportDesigns(RemoteFormInterface remoteForm, boolean toExcel) throws IOException, ClassNotFoundException {
        byte[] designsArray = remoteForm.getReportDesignsByteArray(toExcel);
        ObjectInputStream objStream = new ObjectInputStream(new ByteArrayInputStream(designsArray));
        return (Map<String, JasperDesign>) objStream.readObject();
    }

    private static sourcesGenerationOutput retrieveReportSources(RemoteFormInterface remoteForm) throws IOException, ClassNotFoundException {
        sourcesGenerationOutput output = new sourcesGenerationOutput();
        byte[] sourcesArray = remoteForm.getReportSourcesByteArray();
        DataInputStream dataStream = new DataInputStream(new ByteArrayInputStream(sourcesArray));
        int size = dataStream.readInt();
        output.data = new HashMap<String, ClientReportData>();
        for (int i = 0; i < size; i++) {
            String sid = dataStream.readUTF();
            ClientReportData reportData = new ClientReportData(dataStream);
            output.data.put(sid, reportData);
        }

        output.compositeData = new HashMap<String, List<List<Object>>>();
        int compositeCaptionsCnt = dataStream.readInt();
        for (int i = 0; i < compositeCaptionsCnt; i++) {
            String caption = dataStream.readUTF();
            List<List<Object>> diffValues = new ArrayList<List<Object>>();
            int valuesCnt = dataStream.readInt();
            for (int j = 0; j < valuesCnt; j++) {
                List<Object> values = new ArrayList<Object>();
                int objCnt = dataStream.readInt();
                for (int k = 0; k < objCnt; k++) {
                    values.add(BaseUtils.deserializeObject(dataStream));
                }
                diffValues.add(values);
            }
            output.compositeData.put(caption, diffValues);
        }

        output.compositeFieldsObjects = new HashMap<String, List<Integer>>();
        for (int i = 0; i < compositeCaptionsCnt; i++) {
            String caption = dataStream.readUTF();
            List<Integer> objectsId = new ArrayList<Integer>();
            int objectCnt = dataStream.readInt();
            for (int j = 0; j < objectCnt; j++) {
                objectsId.add(dataStream.readInt());
            }
            output.compositeFieldsObjects.put(caption, objectsId);
        }

        for (ClientReportData data : output.data.values()) {
            data.setCompositeData(output.compositeData, output.compositeFieldsObjects);
        }
        return output;
    }

    // Пока что добавляет только свойства с группами в колонках
    private void transformDesigns() {
        for (JasperDesign design : designs.values()) {
            transformDesign(design);
//            try {
//                JasperCompileManager.compileReportToFile(design, "D:/"+design.getName());
//            } catch (Exception e) {}
        }
    }

    private void transformDesign(JasperDesign design) {
        transformBand(design, design.getPageHeader());
        transformBand(design, design.getPageFooter());

        transformSection(design, design.getDetailSection());
        for (JRGroup group : design.getGroups()) {
            transformSection(design, group.getGroupHeaderSection());
            transformSection(design, group.getGroupFooterSection());
        }
    }

    private void transformSection(JasperDesign design, JRSection section) {
        if (section instanceof JRDesignSection) {
            JRDesignSection designSection = (JRDesignSection) section;
            List bands = designSection.getBandsList();
            for (Object band : bands) {
                if (band instanceof JRBand) {
                    transformBand(design, (JRBand) band);
                }
            }
        }
    }

    private void transformBand(JasperDesign design, JRBand band) {
        if (band instanceof JRDesignBand) {
            JRDesignBand designBand = (JRDesignBand) band;
            List<JRDesignElement> toDelete = new ArrayList<JRDesignElement>();
            List<JRDesignElement> toAdd = new ArrayList<JRDesignElement>();

            for (JRElement element : band.getElements()) {
                if (element instanceof JRDesignTextField) {
                    JRDesignTextField textField = (JRDesignTextField) element;
                    transformTextField(design, textField, toAdd, toDelete);
                }
            }
            for (JRDesignElement element : toDelete) {
                designBand.removeElement(element);
            }
            for (JRDesignElement element : toAdd) {
                designBand.addElement(element);
            }
        }
    }

    private void transformTextField(JasperDesign design, JRDesignTextField textField,
                                    List<JRDesignElement> toAdd, List<JRDesignElement> toDelete) {
        String exprText = textField.getExpression().getText();
        String id;
        if (exprText.startsWith("$F{")) {
            id = exprText.substring(3, exprText.length()-1);
        } else {
            assert exprText.startsWith("\"");
            id = exprText.substring(1, exprText.length()-1);
        }

        String dataId = id;
        if (id.endsWith(ReportConstants.captionSuffix)) {
            dataId = id.substring(0, id.length() - ReportConstants.captionSuffix.length());
        }
        if (compositeData.containsKey(dataId)) {
            toDelete.add(textField);
            design.removeField(id);
            int newFieldsCount= compositeData.get(dataId).size();
            List<JRDesignTextField> subFields = makeFieldPartition(textField, newFieldsCount);

            for (int i = 0; i < newFieldsCount; i++) {
                JRDesignExpression subExpr = new JRDesignExpression();
                subExpr.setValueClassName(textField.getExpression().getValueClassName());
                if (exprText.startsWith("\"")) {  // caption без property
                    subExpr.setText(exprText);
                } else {
                    String fieldName = id + ClientReportData.beginMarker + i + ClientReportData.endMarker;
                    subExpr.setText("$F{" + fieldName + "}");
                    JRDesignField designField = new JRDesignField();
                    designField.setName(fieldName);
                    designField.setValueClassName(subExpr.getValueClassName());
                    try {
                        design.addField(designField);
                    } catch (JRException e) {}  // todo [dale]: обработать ошибку
                }
                subFields.get(i).setExpression(subExpr);
            }
            toAdd.addAll(subFields);
        }
    }

    // Разбивает поле на cnt полей с примерно одинаковой шириной
    private static List<JRDesignTextField> makeFieldPartition(JRDesignTextField textField, int cnt) {
        List<JRDesignTextField> res = new ArrayList<JRDesignTextField>();
        int widthLeft = textField.getWidth();
        int x = textField.getX();
        int fieldsLeft = cnt;

        for (int i = 0; i < cnt; i++) {
            JRDesignTextField subField = (JRDesignTextField) textField.clone();

            int subWidth = widthLeft / fieldsLeft;
            subField.setWidth(subWidth);
            subField.setX(x);

            x += subWidth;
            widthLeft -= subWidth;
            --fieldsLeft;

            res.add(subField);
        }
        return res;
    }
}
