package nl.knmi.adaguc.services.esgfsearch.threddscatalog;

import java.util.ArrayList;
import java.util.Collection;

class Service {
    String name;
    Collection<AccessType> accessTypes = new ArrayList<>();

    static class AccessType {
        String serviceType;
        String base;
    }

    void addAccessType(String serviceType, String base) {
        serviceType = serviceType.toLowerCase();
        switch (serviceType) {
            case "opendap":
                serviceType = "dapurl";
                break;
            case "httpserver":
                serviceType = "httpurl";
                break;
            case "catalog":
                serviceType = "catalogurl";
                break;
        }

        AccessType accessType = new AccessType();
        accessType.serviceType = serviceType;
        accessType.base = base;
        accessTypes.add(accessType);
    }
}
