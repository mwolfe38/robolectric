package com.xtremelabs.robolectric.res;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ResourceExtractor {
    public static enum ResourceType {
        SYSTEM,
        APPLICATION,
        LIBRARY
    };
    
    private class RClassAndType {
        public final Class<?> rClass;
        public final ResourceType type;
        public RClassAndType(Class<?> resClass, ResourceType rType) {
            rClass = resClass;
            type = rType;
        }
    }
    
    private List<RClassAndType> pendingClassesToResolve = new ArrayList<RClassAndType>();
    
    private Map<String, Integer> localResourceStringToId = new HashMap<String, Integer>();
    private Map<String, Integer> systemResourceStringToId = new HashMap<String, Integer>();
    private Map<Integer, String> resourceIdToName = new HashMap<Integer, String>();
 
    private boolean isApplicationClassResolved = false;
    
    public void addLocalRClass(Class<?> rClass) throws Exception {
        addRClass(rClass, ResourceType.APPLICATION);
    }

    public void addSystemRClass(Class<?> rClass) throws Exception {
        addRClass(rClass, ResourceType.SYSTEM);
    }
    
    public void addLibraryRClass(Class<?> rClass) throws Exception {
        //addRClass(rClass, ResourceType.LIBRARY);
        //currently implementation doesn't do anything because library R class should be subset of application R class.
    }

    //must wait until the application class to be resolved before resolving library r classes
    private void addRClass(Class<?> rClass, ResourceType resourceType) throws Exception {
        RClassAndType rct = new RClassAndType(rClass, resourceType);
        //application classes must resolve first
        if (resourceType == ResourceType.APPLICATION) {
            if (isApplicationClassResolved) {
                throw new RuntimeException("Can't have multiple application R classes");
            }
            isApplicationClassResolved = true;
            pendingClassesToResolve.add(0, rct);
        } else {
            pendingClassesToResolve.add(rct);
        }
        if (isApplicationClassResolved) {
            addUnresolvedRClasses();
        }
    }

    public void addUnresolvedRClasses() throws Exception {
        while(!pendingClassesToResolve.isEmpty()) {
            RClassAndType rct = pendingClassesToResolve.remove(0);
            Class<?> rClass = rct.rClass;
            ResourceType resourceType = rct.type;
            for (Class<?> innerClass : rClass.getClasses()) {
                for (Field field : innerClass.getDeclaredFields()) {
                    if (field.getType().equals(Integer.TYPE) && Modifier.isStatic(field.getModifiers())) {
                        String section = innerClass.getSimpleName();
                        String name = section + "/" + field.getName();
                        int value = field.getInt(null);
                        if (resourceType == ResourceType.SYSTEM) {
                            name = "android:" + name;
                        }
                        if (!section.equals("styleable")) {
                            if (resourceType == ResourceType.SYSTEM) {
                                systemResourceStringToId.put(name, value);
                            } else if (resourceType == ResourceType.APPLICATION){
                                localResourceStringToId.put(name, value);
                            }
                            //we don't store the mapping from id->name for library projects, instead we stored
                            //lib id -> app id mapping above. We'll have to do a special lookup from library classes
                            if (resourceType != ResourceType.LIBRARY) {
                                //ensure that we don't have any key section/id->name collisions.
                                //if this happens we simply can't test projects including library project resources
                                if (resourceIdToName.containsKey(value)) {
                                    throw new RuntimeException(value + " is already defined with name: " + resourceIdToName.get(value) + " can't also call it: " + name);
                                }
                                resourceIdToName.put(value,  name);
                            }
                            
                        }
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
        return getResourceId(value, false);
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
    
    public String getResourceName(int resourceId) {
        return resourceIdToName.get(resourceId);
    }
     
}