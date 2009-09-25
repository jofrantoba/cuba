package com.haulmont.cuba.web.app.ui.security.role.edit;

import com.haulmont.cuba.gui.WindowManager;
import com.haulmont.cuba.gui.components.*;
import com.haulmont.cuba.gui.config.PermissionConfig;
import com.haulmont.cuba.gui.data.CollectionDatasource;
import com.haulmont.cuba.gui.data.Datasource;
import com.haulmont.cuba.security.entity.Permission;
import com.haulmont.cuba.security.entity.PermissionType;
import com.haulmont.cuba.security.entity.Role;
import com.haulmont.cuba.core.global.MessageProvider;
import org.apache.commons.lang.ObjectUtils;

import java.util.*;

public class RoleEditor extends AbstractEditor {

    private Set<String> initialized = new HashSet<String>();

    public RoleEditor(IFrame frame) {
        super(frame);
    }

    @Override
    protected void init(Map<String, Object> params) {
        initPermissionControls(
                "sec$Target.screenPermissions.lookup",
                "screen-permissions",
                PermissionType.SCREEN);

        Tabsheet tabsheet = getComponent("permissions-types");
        tabsheet.addListener(new Tabsheet.TabChangeListener() {
            public void tabChanged(Tabsheet.Tab newTab) {
                if ("entity-permissions-tab".equals(newTab.getName())) {
                    initPermissionControls(
                            "sec$Target.entityPermissions.lookup",
                            "entity-permissions",
                            PermissionType.ENTITY_OP);
                } else if ("property-permissions-tab".equals(newTab.getName())) {
                    initPermissionControls(
                            "sec$Target.propertyPermissions.lookup",
                            "property-permissions",
                            PermissionType.ENTITY_ATTR);
                } else if ("specific-permissions-tab".equals(newTab.getName())) {
                    initPermissionControls(
                            "sec$Target.specificPermissions.lookup",
                            "specific-permissions",
                            PermissionType.SPECIFIC);
                }
            }
        });
    }

    protected void initPermissionControls(final String lookupAction, final String permissionsStorage,
                                          final PermissionType permissionType)
    {
        if (initialized.contains(permissionsStorage))
            return;
        initialized.add(permissionsStorage);

        final Datasource ds = getDsContext().get(permissionsStorage);
        ds.refresh();

        final Table table = getComponent(permissionsStorage);
        table.addAction(new AbstractAction("grant") {
            public void actionPerform(Component component) {
                final PermissionsLookup permissionsLookup = openLookup(lookupAction, null, WindowManager.OpenType.THIS_TAB);
                permissionsLookup.setLookupHandler(new Lookup.Handler() {
                    public void handleLookup(Collection items) {
                        Integer value = permissionsLookup.getPermissionValue();
                        @SuppressWarnings({"unchecked"})
                        Collection<PermissionConfig.Target> targets = items;
                        for (PermissionConfig.Target target : targets) {
                            createPermissionItem(permissionsStorage, target, permissionType, value);
                        }
                    }
                });
            }

            @Override
            public String getCaption() {
                return MessageProvider.getMessage(getClass(), "permissions.grant");
            }
        });

        final TableActionsHelper helper = new TableActionsHelper(this, table);
        helper.createRemoveAction(false);
    }

    protected Set<PermissionConfig.Target> substract(
            Collection<PermissionConfig.Target> c1, Collection<PermissionConfig.Target> c2)
    {
        final HashSet<PermissionConfig.Target> res = new HashSet<PermissionConfig.Target>(c1);
        res.removeAll(c2);

        return res;
    }

    protected void createPermissionItem(String dsName, PermissionConfig.Target target, PermissionType type, Integer value) {
        final CollectionDatasource<Permission, UUID> ds = getDsContext().get(dsName);
        final Collection<UUID> permissionIds = ds.getItemIds();

        Permission permission = null;
        for (UUID id : permissionIds) {
            Permission p = ds.getItem(id);
            if (ObjectUtils.equals(p.getTarget(), target.getValue())) {
                permission = p;
                break;
            }
        }

        if (permission == null) {
            @SuppressWarnings({"unchecked"})
            final Datasource<Role> roleDs = getDsContext().get("role");

            final Permission newPermission = new Permission();
            newPermission.setRole(roleDs.getItem());
            newPermission.setTarget(target.getValue());
            newPermission.setType(type);
            newPermission.setValue(value);

            ds.addItem(newPermission);
        } else {
            permission.setValue(value);
        }
    }
}
