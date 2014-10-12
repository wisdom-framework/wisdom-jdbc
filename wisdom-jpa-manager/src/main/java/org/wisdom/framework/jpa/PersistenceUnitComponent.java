/*
 * #%L
 * Wisdom-Framework
 * %%
 * Copyright (C) 2013 - 2014 Wisdom Framework
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package org.wisdom.framework.jpa;

import org.apache.commons.io.IOUtils;
import org.apache.felix.ipojo.annotations.*;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.wiring.BundleWiring;
import org.wisdom.framework.jpa.model.Persistence;
import org.wisdom.framework.jpa.model.PersistenceUnitCachingType;
import org.wisdom.framework.jpa.model.PersistenceUnitValidationModeType;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.SharedCacheMode;
import javax.persistence.ValidationMode;
import javax.persistence.spi.ClassTransformer;
import javax.persistence.spi.PersistenceProvider;
import javax.persistence.spi.PersistenceUnitInfo;
import javax.persistence.spi.PersistenceUnitTransactionType;
import javax.sql.DataSource;
import javax.transaction.TransactionManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.*;

/**
 * This class is the interface between the bridge (the manager) and the Persistence Provider.
 * It is used by the Persistence Provider to get all context information. We create one
 * instance of these for each persistence unit found in a bundle.
 */
@Component
@Provides
public class PersistenceUnitComponent implements PersistenceUnitInfo {

    private static final String OSGI_UNIT_PROVIDER = "osgi.unit.provider";
    private static final String OSGI_UNIT_VERSION = "osgi.unit.version";
    private static final String OSGI_UNIT_NAME = "osgi.unit.name";

    private final Persistence.PersistenceUnit persistenceUnitXml;

    /**
     * Injected in the instance configuration.
     */
    private final PersistentBundle sourceBundle;
    private final String location;
    private final BundleContext bundleContext;


    /**
     * Filter injected in the instance configuration.
     */
    @Requires(optional = true, proxy = false, nullable = false, id = "jta-ds")
    DataSource jtaDataSource;

    /**
     * Filter injected in the instance configuration.
     */
    @Requires(optional = true, proxy = false, nullable = false, id = "ds")
    DataSource nonJtaDataSource;

    /**
     * The transformer.
     */
    @Requires
    JPATransformer transformer;

    /**
     * The service exposed by JPA provider.
     * Only one provider is supported.
     */
    @Requires(proxy = false)
    PersistenceProvider provider;

    /**
     * Transaction manager.
     */
    @Requires
    TransactionManager transactionManager;


    ServiceRegistration<EntityManager> emRegistration;
    ServiceRegistration<EntityManagerFactory> emfRegistration;

    /**
     * Create a new Persistence Unit Info
     *
     * @param bundle the source bundle
     * @param xml    The xml of the persistence unit
     */
    PersistenceUnitComponent(@Property(name = "bundle") PersistentBundle bundle,
                             @Property(name = "unit") Persistence.PersistenceUnit xml,
                             @Context BundleContext context) throws Exception {
        this.sourceBundle = bundle;
        this.persistenceUnitXml = xml;
        this.bundleContext = context;
        // Retrieve the location set while parsing the persistence unit.
        this.location = (String) getProperties().get("location");
    }

    /**
     * Shutdown this persistence unit
     */
    @Invalidate
    void shutdown() {
        if (emRegistration != null) {
            emRegistration.unregister();
        }
        if (emfRegistration != null) {
            emfRegistration.unregister();
        }
        if (transformer != null) {
            transformer.unregister(sourceBundle.bundle);
        }
    }

    @Validate
    public void start() {
        Map<String, Object> map = new HashMap<>();
        for (Persistence.PersistenceUnit.Properties.Property p :
                persistenceUnitXml.getProperties().getProperty()) {
            map.put(p.getName(), p.getValue());
        }

        if (isOpenJPA()) {
            map.put("openjpa.ManagedRuntime",
                    "invocation(TransactionManagerMethod=org.wisdom.framework.jpa.accessor" +
                            ".TransactionManagerAccessor.get)");
        }


        Hashtable<String, Object> properties = new Hashtable<>();
        properties.put(OSGI_UNIT_NAME, persistenceUnitXml.getName());
        properties.put(OSGI_UNIT_VERSION, sourceBundle.bundle.getVersion());
        properties.put(OSGI_UNIT_PROVIDER, provider.getClass().getName());
        properties.put("jpa.transaction.type", getTransactionType().toString());

        // If the unit set the transaction to RESOURCE_LOCAL, no JTA involved.
        if (persistenceUnitXml.getTransactionType() ==
                org.wisdom.framework.jpa.model.PersistenceUnitTransactionType.RESOURCE_LOCAL) {
            if (isOpenJPA()) {
                map.put("openjpa.TransactionMode", "false");
            }
            EntityManagerFactory emf = provider.createContainerEntityManagerFactory(this, map);
            emfRegistration = bundleContext.registerService(EntityManagerFactory.class, emf, properties);

            emRegistration = bundleContext.registerService(EntityManager.class,
                    emf.createEntityManager(), properties);
        } else {
            // JTA
            EntityManagerFactory emf = provider.createContainerEntityManagerFactory(this, map);
            emRegistration = bundleContext.registerService(EntityManager.class,
                    new TransactionalEntityManager(transactionManager, emf, this), properties);
            emfRegistration = bundleContext.registerService(EntityManagerFactory.class, emf, properties);
        }
    }

    private boolean isOpenJPA() {
        return provider.getClass().getName().contains("openjpa");
    }

    /**
     * Add a new transformer.
     *
     * @see javax.persistence.spi.PersistenceUnitInfo#addTransformer(javax.persistence.spi.ClassTransformer)
     */
    @Override
    public void addTransformer(ClassTransformer transformer) {
        this.transformer.register(sourceBundle.bundle, transformer);
    }

    /*
     * @see javax.persistence.spi.PersistenceUnitInfo#excludeUnlistedClasses()
     */
    @Override
    public boolean excludeUnlistedClasses() {
        Boolean b = persistenceUnitXml.isExcludeUnlistedClasses();
        return b == null || b;
    }

    /*
     * @see javax.persistence.spi.PersistenceUnitInfo#getClassLoader()
     */
    @Override
    public synchronized ClassLoader getClassLoader() {
        return sourceBundle.bundle.adapt(BundleWiring.class).getClassLoader();
    }

    /*
     * @see javax.persistence.spi.PersistenceUnitInfo#getJarFileUrls()
     */
    @Override
    public List<URL> getJarFileUrls() {
        try {
            List<URL> urls = new ArrayList<>();
            for (String url : persistenceUnitXml.getJarFile()) {
                urls.add(new URL(url));
            }
            return urls;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * We hand out a proxy that automatically enlists any connections on the
     * current transaction.
     *
     * @see javax.persistence.spi.PersistenceUnitInfo#getJtaDataSource()
     */
    @Override
    public synchronized DataSource getJtaDataSource() {
        return jtaDataSource;
    }

    /*
     * @see javax.persistence.spi.PersistenceUnitInfo#getManagedClassNames()
     */
    @Override
    public List<String> getManagedClassNames() {
        return persistenceUnitXml.getClazz();
    }

    /*
     * @see javax.persistence.spi.PersistenceUnitInfo#getMappingFileNames()
     */
    @Override
    public List<String> getMappingFileNames() {
        return persistenceUnitXml.getMappingFile();
    }

    /**
     * In this method we just create a simple temporary class loader. This class
     * loader uses the bundle's class loader as parent but defines the classes
     * in this class loader. This has the implicit assumption that the temp
     * class loader is used BEFORE any bundle's classes are loaded since a class
     * loader does parent delegation first. Sigh, guess it works most of the
     * time. There is however, no good alternative though in OSGi we could
     * actually refresh the bundle.
     *
     * @see javax.persistence.spi.PersistenceUnitInfo#getNewTempClassLoader()
     */
    @Override
    public ClassLoader getNewTempClassLoader() {

        return new ClassLoader(getClassLoader()) {
            /**
             * Searchs for the .class file and define it using the current class loader.
             * @param className the class name
             * @return the class object
             * @throws ClassNotFoundException if the class cannot be found
             */
            @Override
            protected Class<?> findClass(String className) throws ClassNotFoundException {

                // Use path of class, then get the resource
                String path = className.replace('.', '/').concat(".class");
                URL resource = getParent().getResource(path);
                if (resource == null)
                    throw new ClassNotFoundException(className + " as resource " + path + " in " + getParent());

                try {
                    ByteArrayOutputStream bout = new ByteArrayOutputStream();
                    IOUtils.copy(resource.openStream(), bout);
                    byte[] buffer = bout.toByteArray();
                    return defineClass(className, buffer, 0, buffer.length);
                } catch (Exception e) {
                    throw new ClassNotFoundException(className + " as resource" + path + " in " + getParent(), e);
                }
            }

            /**
             * Finds a resource in the bundle.
             * @param resource the resource
             * @return the url of the resource from the bundle, {@code null} if not found.
             */
            @Override
            protected URL findResource(String resource) {
                return getParent().getResource(resource);
            }

            /**
             * Finds resources in the bundle.
             * @param resource the resource
             * @return the url of the resources from the bundle, empty if not found.
             */
            @Override
            protected Enumeration<URL> findResources(String resource) throws IOException {
                return getParent().getResources(resource);
            }

        };
    }

    /**
     * This Data Source is based on a XA Data Source but will not be enlisted in
     * a transaction.
     *
     * @see javax.persistence.spi.PersistenceUnitInfo#getNonJtaDataSource()
     */
    @Override
    public DataSource getNonJtaDataSource() {
        return nonJtaDataSource;
    }

    /*
     * @see
     * javax.persistence.spi.PersistenceUnitInfo#getPersistenceProviderClassName
     * ()
     */
    @Override
    public String getPersistenceProviderClassName() {
        return provider.getClass().getName();
    }

    /*
     * @see javax.persistence.spi.PersistenceUnitInfo#getPersistenceUnitName()
     */
    @Override
    public String getPersistenceUnitName() {
        return persistenceUnitXml.getName();
    }

    /*
     * @see
     * javax.persistence.spi.PersistenceUnitInfo#getPersistenceUnitRootUrl()
     */
    @Override
    public URL getPersistenceUnitRootUrl() {
        //
        // Make one that is OSGi based
        //

        String loc = location;
        int n = loc.lastIndexOf('/');
        if (n > 0) {
            loc = loc.substring(0, n);
        }
        if (loc.isEmpty())
            loc = "/";

        return sourceBundle.bundle.getResource(loc);
    }

    /*
     * @see
     * javax.persistence.spi.PersistenceUnitInfo#getPersistenceXMLSchemaVersion
     * ()
     */
    @Override
    public String getPersistenceXMLSchemaVersion() {
        return "2.1";
    }

    /*
     * @see javax.persistence.spi.PersistenceUnitInfo#getProperties()
     */
    @Override
    public Properties getProperties() {
        Properties properties = new Properties();
        if (persistenceUnitXml.getProperties() != null && persistenceUnitXml.getProperties().getProperty() != null)
            for (Persistence.PersistenceUnit.Properties.Property p : persistenceUnitXml.getProperties().getProperty()) {
                properties.put(p.getName(), p.getValue());
            }
        return properties;
    }

    /*
     * @see javax.persistence.spi.PersistenceUnitInfo#getSharedCacheMode()
     */
    @Override
    public SharedCacheMode getSharedCacheMode() {
        PersistenceUnitCachingType sharedCacheMode = persistenceUnitXml.getSharedCacheMode();
        if (sharedCacheMode == null)
            return null;

        return SharedCacheMode.valueOf(sharedCacheMode.name());
    }

    /*
     * @see javax.persistence.spi.PersistenceUnitInfo#getTransactionType()
     */
    @Override
    public PersistenceUnitTransactionType getTransactionType() {
        if (persistenceUnitXml.getTransactionType() ==
                org.wisdom.framework.jpa.model.PersistenceUnitTransactionType
                        .RESOURCE_LOCAL) {
            return PersistenceUnitTransactionType.RESOURCE_LOCAL;
        } else {
            return PersistenceUnitTransactionType.JTA;
        }
    }

    /*
     * @see javax.persistence.spi.PersistenceUnitInfo#getValidationMode()
     */
    @Override
    public ValidationMode getValidationMode() {
        PersistenceUnitValidationModeType validationMode = persistenceUnitXml.getValidationMode();
        if (validationMode == null) {
            return null;
        }

        return ValidationMode.valueOf(validationMode.name());
    }
}
