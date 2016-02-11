/*
 * Copyright (c) 2008-2013 Haulmont. All rights reserved.
 * Use is subject to license terms, see http://www.cuba-platform.com/license for details.
 */

package com.haulmont.cuba.core.sys.jpql.model;

import com.haulmont.cuba.core.sys.jpql.InferredType;

import java.util.*;

/**
 * @author chevelev
 * @version $Id$
 */
public class EntityImpl implements Entity {
    private String name;
    private List<String> attributeNames = new ArrayList<>();
    private Map<String, AttributeImpl> name2attribute = new HashMap<>();
    private String userFriendlyName;

    public EntityImpl(String name) {
        if (name == null)
            throw new NullPointerException("No entity name passed");

        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getUserFriendlyName() {
        return userFriendlyName;
    }

    public void setUserFriendlyName(String userFriendlyName) {
        this.userFriendlyName = userFriendlyName;
    }

    public void addSingleValueAttribute(Class aClass, String name) {
        addSingleValueAttribute(aClass, name, null);
    }

    @Override
    public void addSingleValueAttribute(Class aClass, String name, String userFriendlyName) {
        if (aClass == null)
            throw new NullPointerException("No attribute type passed");
        if (name == null)
            throw new NullPointerException("No attribute name passed");

        AttributeImpl attribute = new AttributeImpl(aClass, name);
        attribute.setUserFriendlyName(userFriendlyName);
        attributeNames.add(name);
        name2attribute.put(name, attribute);
    }

    @Override
    public AttributeImpl getAttributeByName(String attributeName) {
        return name2attribute.get(attributeName);
    }

    @Override
    public List<Attribute> findAttributesStartingWith(String fieldNamePattern, Set<InferredType> expectedTypes) {
        List<Attribute> result = new ArrayList<>();
        for (Map.Entry<String, AttributeImpl> entry : name2attribute.entrySet()) {
            if (entry.getKey().startsWith(fieldNamePattern)) {
                for (InferredType expectedType : expectedTypes) {
                    AttributeImpl attribute = entry.getValue();
                    if (expectedType.matches(attribute)) {
                        result.add(attribute);
                        break;
                    }
                }
            }
        }
        return result;
    }

    public void addReferenceAttribute(String referencedEntityName, String name) {
        addReferenceAttribute(referencedEntityName, name, null, false);
    }

    @Override
    public void addReferenceAttribute(String referencedEntityName, String name, String userFriendlyName, boolean isEmbedded) {
        if (referencedEntityName == null)
            throw new NullPointerException("No referencedEntityName passed");
        if (name == null)
            throw new NullPointerException("No attribute name passed");

        AttributeImpl attribute = new AttributeImpl(referencedEntityName, name, false);
        attribute.setUserFriendlyName(userFriendlyName);
        attribute.setEmbedded(isEmbedded);
        attributeNames.add(name);
        name2attribute.put(name, attribute);
    }

    public void addCollectionReferenceAttribute(String referencedEntityName, String name) {
        addCollectionReferenceAttribute(referencedEntityName, name, null);
    }

    @Override
    public void addCollectionReferenceAttribute(String referencedEntityName, String name, String userFriendlyName) {
        if (referencedEntityName == null)
            throw new NullPointerException("No referencedEntityName passed");
        if (name == null)
            throw new NullPointerException("No attribute name passed");

        AttributeImpl attribute = new AttributeImpl(referencedEntityName, name, true);
        attribute.setUserFriendlyName(userFriendlyName);
        attributeNames.add(name);
        name2attribute.put(name, attribute);
    }

    @Override
    public void addAttributeCopy(Attribute attribute) {
        if (attribute == null)
            throw new NullPointerException("No attribute passed");

        attributeNames.add(attribute.getName());
        try {
            name2attribute.put(attribute.getName(), (AttributeImpl) attribute.clone());
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }
}