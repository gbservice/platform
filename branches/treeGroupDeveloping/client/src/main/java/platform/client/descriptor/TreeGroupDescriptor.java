package platform.client.descriptor;

import platform.client.descriptor.context.ContextIdentityDescriptor;
import platform.client.logics.ClientComponent;
import platform.client.logics.ClientContainer;
import platform.client.logics.ClientGroupObject;
import platform.client.logics.ClientTreeGroup;
import platform.client.serialization.ClientIdentitySerializable;
import platform.client.serialization.ClientSerializationPool;
import platform.interop.form.layout.GroupObjectContainerSet;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TreeGroupDescriptor extends ContextIdentityDescriptor implements ClientIdentitySerializable, CustomConstructible, ContainerMovable<ClientContainer> {
    public ClientTreeGroup client;
    public List<GroupObjectDescriptor> groups = new ArrayList<GroupObjectDescriptor>();

    public void customConstructor() {
        client = new ClientTreeGroup(getID(), getContext());
    }

    public void customSerialize(ClientSerializationPool pool, DataOutputStream outStream, String serializationType) throws IOException {
        pool.serializeCollection(outStream, groups, serializationType);
    }

    public void customDeserialize(ClientSerializationPool pool, DataInputStream inStream) throws IOException {
        groups = pool.deserializeList(inStream);

        client = pool.context.getTreeGroup(ID);
    }

    public void setGroups(List<GroupObjectDescriptor> groups) {
        List<ClientGroupObject> clientGroups = new ArrayList<ClientGroupObject>();
        for (GroupObjectDescriptor group : groups) {
            clientGroups.add(group.client);
        }

        this.groups = groups;
        client.groups = clientGroups;

        getContext().updateDependency(this, "groups");
    }

    public List<GroupObjectDescriptor> getGroups() {
        return groups;
    }

    @Override
    public String toString() {
        return client.toString();
    }

    public ClientContainer getDestinationContainer(ClientContainer parent, List<GroupObjectDescriptor> groupObjects) {
        return parent;
    }

    public ClientContainer getClientComponent(ClientContainer parent) {
        return parent.findContainerBySID(GroupObjectContainerSet.TREE_GROUP_CONTAINER + getID());
    }
}