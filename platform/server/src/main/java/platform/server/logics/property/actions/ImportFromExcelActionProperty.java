package platform.server.logics.property.actions;

import jxl.Cell;
import jxl.Sheet;
import jxl.Workbook;
import org.apache.log4j.Logger;
import platform.interop.ClassViewType;
import platform.interop.KeyStrokes;
import platform.interop.action.ClientAction;
import platform.server.classes.*;
import platform.server.data.type.ParseException;
import platform.server.data.type.Type;
import platform.server.form.entity.FormEntity;
import platform.server.form.entity.PropertyDrawEntity;
import platform.server.form.instance.FormInstance;
import platform.server.form.instance.PropertyDrawInstance;
import platform.server.form.instance.PropertyObjectInterfaceInstance;
import platform.server.form.instance.remote.RemoteForm;
import platform.server.form.view.DefaultFormView;
import platform.server.logics.DataObject;
import platform.server.logics.ObjectValue;
import platform.server.logics.property.ActionProperty;
import platform.server.logics.property.ClassPropertyInterface;
import platform.server.session.Changes;
import platform.server.session.DataSession;
import platform.server.session.Modifier;

import javax.swing.*;
import java.io.ByteArrayInputStream;
import java.sql.SQLException;
import java.util.*;
import java.util.List;

public class ImportFromExcelActionProperty extends ActionProperty {
    private static Logger logger = Logger.getLogger(ImportFromExcelActionProperty.class);

    private CustomClass valueClass;

    public ImportFromExcelActionProperty(String sID, CustomClass valueClass) {
        super(sID, "Импортировать (" + valueClass + ")", new ValueClass[]{});

        this.valueClass = valueClass;
    }

    public void execute(Map<ClassPropertyInterface, DataObject> keys, ObjectValue value, DataSession session, Modifier<? extends Changes> modifier, List<ClientAction> actions,
                        RemoteForm executeForm, Map<ClassPropertyInterface, PropertyObjectInterfaceInstance> mapObjects, boolean groupLast) throws SQLException {

        Sheet sh;
        try {
            ByteArrayInputStream inFile = new ByteArrayInputStream((byte[]) value.getValue());
            sh = Workbook.getWorkbook(inFile).getSheet(0);
        } catch (Exception e) {
            logger.fatal("Не могу прочитать .xsl файл.");
            return;
        }

        // читаем sid свойств из первого ряда таблицы
        Set<String> definedPropertiesSIDs = new HashSet<String>();
        for (int i = 0; i < sh.getColumns(); ++i) {
            definedPropertiesSIDs.add(sh.getCell(i, 0).getContents());
        }

        // находим используемые свойства
        Map<String, PropertyDrawInstance> definedProperties = new HashMap<String, PropertyDrawInstance>();
        for (PropertyDrawInstance<?> property : ((RemoteForm<?, ?>) executeForm).form.properties)
            if (definedPropertiesSIDs.contains(property.propertyObject.property.sID)) {
                definedProperties.put(property.propertyObject.property.sID, property);
            }

        for (int i = 1; i < sh.getRows(); ++i) {
            FormInstance<?> form = (FormInstance<?>) executeForm.form;
            form.addObject((ConcreteCustomClass) valueClass);

            for (int j = 0; j < sh.getColumns(); ++j) {
                Cell cell = sh.getCell(j, i);
                String cellValue = cell.getContents();

                String propertySID = sh.getCell(j, 0).getContents();
                PropertyDrawInstance property = definedProperties.get(propertySID);
                if (property != null) {
                    try {
                        Type type = property.propertyObject.getType();
                        form.changeProperty(property, type.parseString(cellValue), true);
                    } catch (ParseException e) {
                        logger.warn("Не конвертировано значение совйства", e);
                    }
                }
            }
        }
    }

    @Override
    public DataClass getValueClass() {
        return FileActionClass.getDefinedInstance(false, "Файлы таблиц", "xls");
    }

    @Override
    public void proceedDefaultDraw(PropertyDrawEntity<ClassPropertyInterface> entity, FormEntity form) {
        super.proceedDefaultDraw(entity, form);
        entity.shouldBeLast = true;
        entity.forceViewType = ClassViewType.PANEL;
    }

    @Override
    public void proceedDefaultDesign(DefaultFormView view, PropertyDrawEntity<ClassPropertyInterface> entity) {
        super.proceedDefaultDesign(view, entity);
        view.get(entity).editKey = KeyStrokes.getImportActionPropertyKeyStroke();
        view.get(entity).design.setImage(new ImageIcon(AddObjectActionProperty.class.getResource("/images/import.png")));
        view.get(entity).showEditKey = false;
        view.get(entity).drawToToolbar = true;
    }

}
