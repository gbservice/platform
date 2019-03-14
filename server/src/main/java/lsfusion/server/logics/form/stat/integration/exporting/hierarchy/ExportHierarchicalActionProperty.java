package lsfusion.server.logics.form.stat.integration.exporting.hierarchy;

import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImList;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.file.RawFileData;
import lsfusion.interop.session.ExternalUtils;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.language.linear.LP;
import lsfusion.server.logics.action.ExecutionContext;
import lsfusion.server.logics.form.open.FormSelector;
import lsfusion.server.logics.form.open.ObjectSelector;
import lsfusion.server.logics.form.open.stat.ExportActionProperty;
import lsfusion.server.logics.form.stat.StaticDataGenerator;
import lsfusion.server.logics.form.stat.integration.FormIntegrationType;
import lsfusion.server.logics.form.stat.integration.exporting.StaticExportData;
import lsfusion.server.logics.form.stat.integration.hierarchy.Node;
import lsfusion.server.logics.form.stat.integration.hierarchy.ParseNode;
import lsfusion.server.logics.form.struct.object.ObjectEntity;
import lsfusion.server.logics.property.Property;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.property.implement.PropertyInterfaceImplement;
import lsfusion.server.physics.dev.i18n.LocalizedString;

import java.io.*;
import java.sql.SQLException;

public abstract class ExportHierarchicalActionProperty<T extends Node<T>, O extends ObjectSelector> extends ExportActionProperty<O> {

    private PropertyInterfaceImplement<ClassPropertyInterface> rootProperty;
    private PropertyInterfaceImplement<ClassPropertyInterface> tagProperty;

    protected final LP<?> exportFile; // nullable

    public ExportHierarchicalActionProperty(LocalizedString caption,
                                            FormSelector<O> form,
                                            ImList<O> objectsToSet,
                                            ImList<Boolean> nulls,
                                            FormIntegrationType staticType,
                                            LP exportFile,
                                            String charset,
                                            Property root,
                                            Property tag) {
        super(caption, form, objectsToSet, nulls, staticType, charset != null ? charset : ExternalUtils.defaultXMLJSONCharset, root, tag);

        if (root != null) {
            this.rootProperty = root.getImplement(
                    getOrderInterfaces().subOrder(objectsToSet.size(), interfaces.size())
            );
        }

        if (tag != null) {
            this.tagProperty = tag.getImplement(
                    getOrderInterfaces().subOrder(objectsToSet.size(), interfaces.size())
            );
        }

        this.exportFile = exportFile;
    }

    public void export(ExecutionContext<ClassPropertyInterface> context, StaticExportData exportData, StaticDataGenerator.Hierarchy hierarchy) throws IOException, SQLException, SQLHandledException {
        ParseNode parseNode = hierarchy.getIntegrationHierarchy();
        String root = rootProperty == null ? null : (String) rootProperty.read(context, context.getKeys());
        String tag = tagProperty == null ? null : (String) tagProperty.read(context, context.getKeys());
        T rootNode = createRootNode(root, tag);
        parseNode.exportNode(rootNode, MapFact.<ObjectEntity, Object>EMPTY(), exportData);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(outputStream, charset)))) {
            writeRootNode(out, rootNode);
        }
        writeResult(exportFile, staticType, context, new RawFileData(outputStream));
    }

    protected abstract T createRootNode(String root, String tag);

    protected abstract void writeRootNode(PrintWriter printWriter, T rootNode) throws IOException;

    @Override
    protected ImMap<Property, Boolean> aspectChangeExtProps() {
        return getChangeProps(exportFile.property);
    }
}