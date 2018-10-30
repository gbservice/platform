package lsfusion.server.form.entity;

import lsfusion.base.BaseUtils;
import lsfusion.base.col.SetFact;
import lsfusion.base.col.interfaces.immutable.ImOrderSet;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.base.col.interfaces.mutable.MExclSet;
import lsfusion.server.caches.IdentityInstanceLazy;
import lsfusion.server.caches.IdentityLazy;
import lsfusion.server.context.ThreadLocalContext;
import lsfusion.server.form.view.FormView;

import java.util.*;

public class GroupObjectHierarchy {

    private final GroupObjectEntity root;
    private final Map<GroupObjectEntity, ImOrderSet<GroupObjectEntity>> dependencies; // null key is root

    public GroupObjectHierarchy(GroupObjectEntity root, Map<GroupObjectEntity, ImOrderSet<GroupObjectEntity>> dependencies) {
        this.root = root;
        this.dependencies = dependencies;
    }
    
    public GroupObjectEntity getRoot() {
        return root;
    }
    
    public ImOrderSet<GroupObjectEntity> getDependencies(GroupObjectEntity group) {
        return dependencies.get(group);
    }

    public GroupObjectEntity getParentGroup(GroupObjectEntity group) {        
        for(Map.Entry<GroupObjectEntity, ImOrderSet<GroupObjectEntity>> dependency : dependencies.entrySet())
            if(dependency.getValue().contains(group))
                return dependency.getKey();
        return null; // when hierarchy is not full (i.e. printing one group)
    }

    public static final class ReportNode {
        private List<GroupObjectEntity> groups;
                
        /// max children groupLevel + groups.size()
        private int groupLevel;

        private ReportNode(List<GroupObjectEntity> groups) {
            this.groups = groups;
        }

        private GroupObjectEntity getFirstGroup() {
            return groups.iterator().next();
        }

        public String getID() {
            GroupObjectEntity firstGroup = getFirstGroup();
            return firstGroup == null ? "__ROOT__" : firstGroup.getSID();
        }

        public String getName(FormView formView) {
            GroupObjectEntity firstGroup = getFirstGroup();
            return firstGroup == null ? ThreadLocalContext.localize(formView.caption) : firstGroup.getSID();
        }

        public String getFileName(String formSID) {
            GroupObjectEntity firstGroup = getFirstGroup();
            return formSID + (firstGroup == null ? "" : "_" + firstGroup.getSID()) + ".jrxml";
        }

        public CalcPropertyObjectEntity getReportPathProp(FormEntity formEntity) {
            GroupObjectEntity firstGroup = getFirstGroup();
            return firstGroup == null ? formEntity.reportPathProp : firstGroup.reportPathProp;
        }

        public boolean needMargin() {
            GroupObjectEntity firstGroup = getFirstGroup();
            return firstGroup == null;
        }

        public GroupObjectEntity getLastGroup() {
            return groups.get(groups.size() - 1);
        }

        public List<GroupObjectEntity> getGroupList() {
            return groups;
        }

        public int getGroupCount() {
            return groups.size();
        }

        private void merge(ReportNode obj) {
            groups.addAll(obj.groups);
        }

        private boolean isNonJoinable = false;

        public int getGroupLevel() {
            return groupLevel;
        }

        void setGroupLevel(int groupLevel) {
            this.groupLevel = groupLevel;
        }

        @Override
        public String toString() {
            return groups + " : " + groupLevel;
        }
    }

    @IdentityInstanceLazy
    public ReportHierarchy getReportHierarchy() {
        return new ReportHierarchy(root, dependencies);
    }

    public static class ReportHierarchy {
        
        public final ReportNode rootNode;        
        private final Map<ReportNode, List<ReportNode>> dependencies = new HashMap<>();
        
        private ReportNode createNode(GroupObjectEntity group, Map<GroupObjectEntity, ImOrderSet<GroupObjectEntity>> groupDependencies) {
            List<GroupObjectEntity> groups = new ArrayList<>(); // mutable will be changed in squeeze
            groups.add(group);
            ReportNode thisNode = new ReportNode(groups);

            ImOrderSet<GroupObjectEntity> childGroups = groupDependencies.get(group);
            List<ReportNode> childNodes = new ArrayList<>();
            for(GroupObjectEntity childGroup : childGroups) // mutable will be changed in squeeze
                childNodes.add(createNode(childGroup, groupDependencies));

            dependencies.put(thisNode, childNodes);
            return thisNode;
        } 

        public ReportHierarchy(GroupObjectEntity rootGroup, Map<GroupObjectEntity, ImOrderSet<GroupObjectEntity>> dependencies) {

            rootNode = createNode(rootGroup, dependencies);
            rootNode.isNonJoinable = true;
//            for(ReportNode topGroup : this.dependencies.get(rootNode))
//                topGroup.isNonJoinable = true;

            squeeze(rootNode);

            countGroupLevels(rootNode);
        }

        @IdentityLazy
        public ImSet<ReportNode> getAllNodes() {
            MExclSet<ReportNode> mChildren = SetFact.mExclSet();
            fillAllChildNodes(rootNode, mChildren);
            return mChildren.immutable();
        }
        
        private void fillAllChildNodes(ReportNode node, MExclSet<ReportNode> mChildren) {
            mChildren.exclAdd(node);
            for(ReportNode childNode : getChildNodes(node))
                fillAllChildNodes(childNode, mChildren);
        }

        public List<ReportNode> getChildNodes(ReportNode parent) {
            return dependencies.get(parent);
        }

        public boolean isLeaf(ReportNode node) {
            Collection<ReportNode> children = dependencies.get(node);
            assert(children != null);
            return children.size() == 0;
        }

        private void squeeze(ReportNode reportNode) {
            Collection<ReportNode> children = dependencies.get(reportNode);
            for (ReportNode child : children) {
                squeeze(child);
            }
            if (children.size() == 1 && !reportNode.isNonJoinable) {
                ReportNode child = BaseUtils.single(children);
                dependencies.put(reportNode, dependencies.get(child));
                dependencies.remove(child);
                reportNode.merge(child);
            }
        }

        private int countGroupLevels(ReportNode node) {
            int maxChildLevel = 0;
            List<ReportNode> children = dependencies.get(node);
            for (ReportNode child : children) {
                maxChildLevel = Math.max(maxChildLevel, countGroupLevels(child));
            }

            int level = node.getGroupCount() + maxChildLevel;
            node.setGroupLevel(level);
            return level;
        }

        public Map<String, List<String>> getReportHierarchyMap() {
            Map<String, List<String>> res = new HashMap<>();
            for (Map.Entry<ReportNode, List<ReportNode>> parentNode : dependencies.entrySet()) {
                String parentID = parentNode.getKey().getID();
                List<String> childIDs = new ArrayList<>();
                for (ReportNode child : parentNode.getValue()) {
                    childIDs.add(child.getID());
                }
                res.put(parentID, childIDs);
            }
            return res;
        }
    }
}