package com.enterprise.auth.platform.modules.menu.application;

import cn.hutool.core.lang.tree.Tree;
import cn.hutool.core.lang.tree.TreeNode;
import cn.hutool.core.lang.tree.TreeNodeConfig;
import cn.hutool.core.lang.tree.TreeUtil;
import com.enterprise.auth.platform.modules.menu.domain.MenuTreeNode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜单树构建工具，对齐 haorong-mall 的 TreeUtil.build() 模式。
 * Service 层返回平铺列表，Controller 层调用本工具构建树。
 */
public final class MenuTreeUtil {

    private static final Long ROOT_PARENT_ID = 0L;

    private static final TreeNodeConfig TREE_NODE_CONFIG = TreeNodeConfig.DEFAULT_CONFIG
            .setIdKey("id")
            .setParentIdKey("parentId")
            .setNameKey("name")
            .setWeightKey("weight")
            .setChildrenKey("children");

    private MenuTreeUtil() {
    }

    /**
     * 将平铺的 MenuTreeNode 列表构建为树。
     * 对齐 haorong-mall: Controller 拿到平铺列表后 TreeUtil.build()。
     */
    public static List<MenuTreeNode> buildTree(List<MenuTreeNode> flatNodes) {
        if (flatNodes == null || flatNodes.isEmpty()) {
            return List.of();
        }
        List<TreeNode<Long>> treeNodes = flatNodes.stream()
                .sorted(Comparator.comparingInt(node -> node.sort() == null ? Integer.MAX_VALUE : node.sort()))
                .map(MenuTreeUtil::toTreeNode)
                .toList();
        List<Tree<Long>> tree = TreeUtil.build(treeNodes, ROOT_PARENT_ID, TREE_NODE_CONFIG, (tn, treeObj) -> {
            treeObj.setId(tn.getId());
            treeObj.setParentId(tn.getParentId());
            treeObj.setName(tn.getName());
            treeObj.setWeight(tn.getWeight());
            treeObj.putAll(tn.getExtra());
        });
        return tree.stream().map(MenuTreeUtil::fromTree).toList();
    }

    /**
     * MenuTreeNode → Hutool TreeNode，所有扩展字段放入 extra Map。
     * 对齐 haorong-mall: extra 包含 icon、type、permission、path、component 等。
     */
    private static TreeNode<Long> toTreeNode(MenuTreeNode node) {
        TreeNode<Long> tn = new TreeNode<>();
        tn.setId(node.id());
        tn.setParentId(node.parentId() != null ? node.parentId() : ROOT_PARENT_ID);
        tn.setName(node.name());
        tn.setWeight(node.sort() == null ? 0 : node.sort());
        Map<String, Object> extra = new HashMap<>();
        extra.put("type", node.type());
        extra.put("permission", node.permission());
        extra.put("path", node.path());
        extra.put("component", node.component());
        extra.put("redirect", node.redirect());
        extra.put("icon", node.icon());
        extra.put("sort", node.sort());
        extra.put("outerStatus", node.outerStatus());
        extra.put("applicationKey", node.applicationKey());
        tn.setExtra(extra);
        return tn;
    }

    /**
     * Hutool Tree → MenuTreeNode，递归还原 children。
     */
    private static MenuTreeNode fromTree(Tree<Long> tree) {
        List<MenuTreeNode> children = List.of();
        if (tree.hasChild()) {
            children = tree.getChildren().stream()
                    .map(MenuTreeUtil::fromTree)
                    .toList();
        }
        return new MenuTreeNode(
                tree.getId(),
                str(tree, "type"),
                tree.getName().toString(),
                ROOT_PARENT_ID.equals(tree.getParentId()) ? null : tree.getParentId(),
                str(tree, "permission"),
                str(tree, "path"),
                str(tree, "component"),
                str(tree, "redirect"),
                str(tree, "icon"),
                getInt(tree, "sort"),
                getBoolean(tree, "outerStatus"),
                str(tree, "applicationKey"),
                children
        );
    }

    private static String str(Tree<Long> tree, String key) {
        Object value = tree.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        return value.toString();
    }

    private static int getInt(Tree<Long> tree, String key) {
        Object value = tree.get(key);
        if (value instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    private static boolean getBoolean(Tree<Long> tree, String key) {
        Object value = tree.get(key);
        if (value instanceof Boolean b) {
            return b;
        }
        return false;
    }
}