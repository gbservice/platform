package platform.client.descriptor.nodes;

import platform.client.descriptor.FormDescriptor;
import platform.client.descriptor.GroupObjectDescriptor;
import platform.client.descriptor.TreeGroupDescriptor;
import platform.client.tree.ClientTree;
import platform.interop.context.ApplicationContext;
import platform.interop.context.ApplicationContextProvider;

import javax.swing.*;

public class TreeGroupFolder extends PlainTextNode<TreeGroupFolder> implements ApplicationContextProvider {

    private FormDescriptor form;

    public ApplicationContext getContext() {
        return form.getContext();
    }

    public TreeGroupFolder(FormDescriptor form) {
        super("Деревья");

        this.form = form;

        for (TreeGroupDescriptor descriptor : form.treeGroups) {
            add(new TreeGroupNode(descriptor, form));
        }

        addCollectionReferenceActions(form, "treeGroups", new String[]{""}, new Class[]{TreeGroupDescriptor.class});
    }

    @Override
    public boolean canImport(TransferHandler.TransferSupport info) {
        return ClientTree.getNode(info) instanceof TreeGroupNode;
    }

    @Override
    public boolean importData(ClientTree tree, TransferHandler.TransferSupport info) {
        return form.moveTreeGroup((TreeGroupDescriptor) ClientTree.getNode(info).getTypedObject(), ClientTree.getChildIndex(info));
    }
}
