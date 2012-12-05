package com.xtremelabs.robolectric.res;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

public class ResourceExtractor {
    
    private class ResourceSectionIdKey {
        public final String section;
        public final Integer id;
        public ResourceSectionIdKey(Integer id, String section) {
            if (section == null || id == null) {
                throw new RuntimeException("Null type or name not allowed for resource key's");
            }
            this.section = section;
            this.id = id;
        }
        @Override
        public boolean equals(Object o) {
            if (o == null) return false;
            if (o == this) return true;
            ResourceSectionIdKey other = (ResourceSectionIdKey) o;
            return section.equals(other.section) && id.equals(other.id);
        }
        @Override
        public int hashCode() {
            return 31 * section.hashCode() + id;
        }
        @Override
        public String toString() {
            return section + "/0x" + Integer.toHexString(id);
        }
    }
    
    private Map<String, Integer> localResourceStringToId = new HashMap<String, Integer>();
    private Map<String, Integer> systemResourceStringToId = new HashMap<String, Integer>();
    private Map<ResourceSectionIdKey, String> resourceSectionIdToName= new HashMap<ResourceSectionIdKey, String>();
    

    public void addLocalRClass(Class<?> rClass) throws Exception {
        addRClass(rClass, false);
    }

    public void addSystemRClass(Class<?> rClass) throws Exception {
        addRClass(rClass, true);
    }

    private void addRClass(Class<?> rClass, boolean isSystemRClass) throws Exception {
        for (Class<?> innerClass : rClass.getClasses()) {
            for (Field field : innerClass.getDeclaredFields()) {
                if (field.getType().equals(Integer.TYPE) && Modifier.isStatic(field.getModifiers())) {
                    String section = innerClass.getSimpleName();
                    String name = section + "/" + field.getName();
                    int value = field.getInt(null);
                    if (isSystemRClass) {
                        name = "android:" + name;
                    }
                    if (!section.equals("styleable")) {
                        if (isSystemRClass) {
                            systemResourceStringToId.put(name, value);
                        } else {
                            localResourceStringToId.put(name, value);
                        }
                        //ensure that we don't have any key section/id->name collisions.
                        //if this happens we simply can't test projects including library project resources
                        ResourceSectionIdKey key = new ResourceSectionIdKey(value, section);
                        if (resourceSectionIdToName.containsKey(key)) {
                            throw new RuntimeException(value + " is already defined in the section " + section + " with name: " + resourceSectionIdToName.get(key) + " can't also call it: " + name);
                        }
                        resourceSectionIdToName.put(new ResourceSectionIdKey(value, section), name);
                    }
                }
            }
        }
    }


    
    public Integer getResourceId(String resourceName) {
        if (resourceName.contains("android:")) { // namespace needed for platform files
            return getResourceId(resourceName, true);
        } else {
            return getResourceId(resourceName, false);
        }
    }

    public Integer getLocalResourceId(String value) {
        boolean isSystem = false;
        return getResourceId(value, isSystem);
    }

    public Integer getResourceId(String resourceName, boolean isSystemResource) {
        if (resourceName == null ) {
            return null;
        }
        if (resourceName.equals("@null")) {
        	return 0;
        }
        
        if (resourceName.startsWith("@+id")) {
            resourceName = resourceName.substring(2);
        } else if (resourceName.startsWith("@+android:id")) {
            resourceName = resourceName.substring(2);
        } else if (resourceName.startsWith("@")) {
            resourceName = resourceName.substring(1);
        }

        if (isSystemResource) {
            return systemResourceStringToId.get(resourceName);
        } else {
            return localResourceStringToId.get(resourceName);
        }
    }

    public String getResourceName(int resourceId, String section) {
        ResourceSectionIdKey key = new ResourceSectionIdKey(resourceId,  section);
        return resourceSectionIdToName.get(key);
    }
    
    public String getResourceName(int resourceId) {
        for (Field field : ResourceSection.class.getDeclaredFields()) {
            if (field.getType().equals(String.class) && 
                    Modifier.isStatic(field.getModifiers()) && 
                    Modifier.isPublic(field.getModifiers())) {
                String name=null;
                try {
                    name = getResourceName(resourceId, (String)field.get(null));
                } catch (Exception e) {
                   //not sure what to do here?
                }
                if (name != null) {
                    return name;
                }
            }
        } 
        return null;
       
    }
}