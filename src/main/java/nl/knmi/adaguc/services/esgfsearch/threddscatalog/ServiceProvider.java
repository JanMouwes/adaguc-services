package nl.knmi.adaguc.services.esgfsearch.threddscatalog;

import nl.knmi.adaguc.tools.MyXMLParser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class ServiceProvider {
    private Collection<Service> supportedServices;

    public Collection<Service> findServiceNames(MyXMLParser.XMLElement dataset) {
        Collection<Service> output = new ArrayList<>();
        findServiceNames(dataset, output);
        return output;
    }

    public void findServiceNames(MyXMLParser.XMLElement dataset, Collection<Service> output) {

        //Try to find additional servicenames based on access elements
        try {
            Collection<MyXMLParser.XMLElement> access = dataset.getList("access");
            for (MyXMLParser.XMLElement element : access) {
                String serviceName;

                serviceName = element.getAttrValue("serviceName");

                //Debug.println("serviceName:"+serviceName);
                Service service = getServiceByName(serviceName, supportedServices);
                if (service == null) continue;

                output.add(service);

            }

        } catch (Exception ignored) {}
    }


    public Service getServiceByName(String serviceType, Collection<Service> supportedServices) {
        return supportedServices.stream()
                                .filter(supportedService -> supportedService.name.equals(serviceType))
                                .findFirst()
                                .orElse(null);
    }

    private void recursivelyWalkServiceElement(Collection<Service> supportedServices, Collection<MyXMLParser.XMLElement> xmlElements) {
        for (MyXMLParser.XMLElement element : xmlElements) {
            String serviceType = "";
            try {
                serviceType = element.getAttrValue("serviceType");
            } catch (Exception ignored) { }

            Service service = new Service();
            try {
                service.name = element.getAttrValue("name");
            } catch (Exception ignored) { }

            if (serviceType.equalsIgnoreCase("compound")) {
                try {
                    for (MyXMLParser.XMLElement xmlElement : element.getList("service")) {
                        service.addAccessType(xmlElement.getAttrValue("serviceType"), xmlElement.getAttrValue("base"));
                    }
                    supportedServices.add(service);
                } catch (Exception ignored) { }
                recursivelyWalkServiceElement(supportedServices, element.getList("service"));
                continue;
            }

            try {
                service.addAccessType(serviceType, element.getAttrValue("base"));
                supportedServices.add(service);
            } catch (Exception ignored) { }
        }
    }

    public Collection<Service> getSupportedServices(MyXMLParser.XMLElement catalogElement) {
        supportedServices = new ArrayList<>();
        recursivelyWalkServiceElement(supportedServices, catalogElement.getList("service"));
        return Collections.unmodifiableCollection(supportedServices);
    }
}
