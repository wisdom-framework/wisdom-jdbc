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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wisdom.framework.jpa.crud.JPARepository;
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
import javax.validation.ValidatorFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
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

    private final static Logger LOGGER = LoggerFactory.getLogger(PersistenceUnitComponent.class);

    //TODO right now we don't check that we have data sources, which may be an issue,
    // We should enforce their availability and track them


    private static final String UNIT_PROVIDER_PROP = "persistent.unit.provider";
    private static final String UNIT_VERSION_PROP = "persistent.unit.version";
    private static final String UNIT_NAME_PROP = "persistent.unit.name";
    private static final String UNIT_ENTITIES_PROP = "persistent.unit.entities";
    private static final String UNIT_TRANSACTION_PROP = "persistent.unit.transaction.mode";

    private final Persistence.PersistenceUnit persistenceUnitXml;

    /**
     * Injected in the instance configuration.
     */
    private final PersistentBundle sourceBundle;
    private final String location;
    private final BundleContext bundleContext;

    private EntityManager entityManager;
    private EntityManagerFactory entityManagerFactory;
    private JPARepository repository;

    @Requires
    private ValidatorFactory validator;


    /**
     * Filter injected in the instance configuration.
     */
    @Requires(proxy = false, id = "jta-ds")
    DataSource jtaDataSource;

    /**
     * Filter injected in the instance configuration.
     */
    @Requires(proxy = false, id = "ds")
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
     * @param bundle  the source bundle
     * @param xml     The xml of the persistence unit
     * @param context the bundle context
     */
    public PersistenceUnitComponent(@Property(name = "bundle") PersistentBundle bundle,
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
        if (repository != null) {
            repository.dispose();
        }

        if (emRegistration != null) {
            emRegistration.unregister();
        }
        if (entityManager != null) {
            entityManager.close();
        }
        if (emfRegistration != null) {
            emfRegistration.unregister();
        }
        if (entityManagerFactory != null) {
            entityManagerFactory.close();
        }
        if (transformer != null) {
            transformer.unregister(sourceBundle.bundle);
        }
    }

    /**
     * Starts the unit. It creates the entity manager factory and entity manager, as well as the repository and crud
     * services.
     */
    @Validate
    public void start() {
        try {
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

            // This is not going to work with OpenJPA because the current version of OpenJPA requires an old version
            // of javax.validation. The wisdom one is too recent.
            map.put("javax.persistence.validation.factory", validator);

            Dictionary<String, Object> properties = new Hashtable<>();
            properties.put(UNIT_NAME_PROP, persistenceUnitXml.getName());
            properties.put(UNIT_VERSION_PROP, sourceBundle.bundle.getVersion());
            properties.put(UNIT_PROVIDER_PROP, provider.getClass().getName());
            List<String> entities = persistenceUnitXml.getClazz();
            properties.put(UNIT_ENTITIES_PROP, entities.toArray(new String[entities.size()]));
            properties.put(UNIT_TRANSACTION_PROP, getTransactionType().toString());

            // If the unit set the transaction to RESOURCE_LOCAL, no JTA involved.
            if (persistenceUnitXml.getTransactionType() ==
                    org.wisdom.framework.jpa.model.PersistenceUnitTransactionType.RESOURCE_LOCAL) {
                entityManagerFactory = provider.createContainerEntityManagerFactory(this, map);
                emfRegistration = bundleContext.registerService(EntityManagerFactory.class, entityManagerFactory, properties);

                entityManager = entityManagerFactory.createEntityManager();
                emRegistration = bundleContext.registerService(EntityManager.class,
                        entityManager, properties);

                repository = new JPARepository(persistenceUnitXml, entityManager,
                        entityManagerFactory, transactionManager, sourceBundle.bundle.getBundleContext());
            } else {
                // JTA
                entityManagerFactory = provider.createContainerEntityManagerFactory(this, map);
                entityManager = new TransactionalEntityManager(transactionManager, entityManagerFactory, this);
                emRegistration = bundleContext.registerService(EntityManager.class,
                        entityManager, properties);
                emfRegistration = bundleContext.registerService(EntityManagerFactory.class, entityManagerFactory, properties);
                repository = new JPARepository(persistenceUnitXml, entityManager,
                        entityManagerFactory, transactionManager, sourceBundle.bundle.getBundleContext());
            }
        } catch (Exception e) {
            LOGGER.error("Error while initializing the JPA services for unit {}",
                    persistenceUnitXml.getName(), e);
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
        List<URL> urls = new ArrayList<>();
        for (String url : persistenceUnitXml.getJarFile()) {
            try {
                urls.add(new URL(url));
            } catch (MalformedURLException e) {
                LOGGER.error("Cannot create an URL object from {}", url, e);
            }
        }
        return urls;
    }

    /**
     * We hand out a proxy that automatically enlists any connections on the
     * current transaction.
     *
     * @see javax.persistence.spi.PersistenceUnitInfo#getJtaDataSource()
     */
    @Override
    public DataSource getJtaDataSource() {
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

        return new ClassLoader(getClassLoader()) {  //NOSONAR
            /**
             * Searches for the .class file and define it using the current class loader.
             * @param className the class name
             * @return the class object
             * @throws ClassNotFoundException if the class cannot be found
             */
            @Override
            protected Class findClass(String className) throws ClassNotFoundException {

                // Use path of class, then get the resource
                String path = className.replace('.', '/').concat(".class");
                URL resource = getParent().getResource(path);
                if (resource == null) {
                    throw new ClassNotFoundException(className + " as resource " + path + " in " + getParent());
                }

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
        // Make one that is OSGi based, it relies on the 'location' property
        String loc = location;
        int n = loc.lastIndexOf('/');
        if (n > 0) {
            loc = loc.substring(0, n);
        }
        if (loc.isEmpty()) {
            loc = "/";
        }
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
        if (persistenceUnitXml.getProperties() != null && persistenceUnitXml.getProperties().getProperty() != null) {
            for (Persistence.PersistenceUnit.Properties.Property p : persistenceUnitXml.getProperties().getProperty()) {
                properties.put(p.getName(), p.getValue());
            }
        }
        return properties;
    }

    /*
     * @see javax.persistence.spi.PersistenceUnitInfo#getSharedCacheMode()
     */
    @Override
    public SharedCacheMode getSharedCacheMode() {
        PersistenceUnitCachingType sharedCacheMode = persistenceUnitXml.getSharedCacheMode();
        if (sharedCacheMode == null) {
            return null;
        }

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
