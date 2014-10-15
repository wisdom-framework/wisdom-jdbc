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
package org.wisdom.framework.jpa.crud;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.wisdom.api.model.Crud;
import org.wisdom.api.model.Repository;
import org.wisdom.framework.jpa.model.Persistence;
import org.wisdom.framework.jpa.model.PersistenceUnitTransactionType;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.metamodel.EntityType;
import javax.transaction.TransactionManager;
import java.util.*;

/**
 * An implementation of {@link org.wisdom.api.model.Repository} based on a JPA Entity Manager.
 */
public class JPARepository implements Repository<EntityManager> {


    private final EntityManager em;
    List<AbstractJTACrud<?, ?>> cruds = new ArrayList<>();
    String name;

    List<ServiceRegistration<Crud>> registrations = new ArrayList<>();

    /**
     * Creates a new {@link org.wisdom.framework.jpa.crud.JPARepository} instance.
     * It infers the Crud service from the list of entities, and publish them as service.
     *
     * @param pu                 the persistent unit
     * @param em                 the entity manager
     * @param emf                the entity manager factory
     * @param transactionManager the transaction manager (not used on non-JTA unit)
     * @param context            the bundle context used to register the crud services.
     */
    @SuppressWarnings("unchecked")
    public JPARepository(Persistence.PersistenceUnit pu, EntityManager em, EntityManagerFactory emf,
                         TransactionManager transactionManager, BundleContext context) {
        this.name = pu.getName();
        this.em = em;
        for (EntityType t : emf.getMetamodel().getEntities()) {
            Class id = t.getIdType().getJavaType();
            Class entity = t.getJavaType();
            AbstractJTACrud<?, ?> crud;
            if (pu.getTransactionType() == PersistenceUnitTransactionType.RESOURCE_LOCAL) {
                crud =
                        new LocalEntityCrud(name, em,
                                entity, id, this);
            } else {
                crud =
                        new JTAEntityCrud(name, em, transactionManager,
                                entity, id, this);
            }
            cruds.add(crud);
            Dictionary<String, Object> properties = new Hashtable<>();
            properties.put(Crud.ENTITY_CLASS_PROPERTY, entity);
            properties.put(Crud.ENTITY_CLASSNAME_PROPERTY, entity.getName());
            registrations.add(context.registerService(Crud.class, crud, properties));
        }
    }

    /**
     * Gets the list of Crud service managed by the current repository.
     *
     * @return the list of crud services, empty if none
     */
    @Override
    public Collection<Crud<?, ?>> getCrudServices() {
        List<Crud<?, ?>> list = new ArrayList<>();
        list.addAll(cruds);
        return list;
    }

    /**
     * The name of the repository.
     *
     * @return the current repository name
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * The type of repository, generally the technology name.
     *
     * @return the type of repository
     */
    @Override
    public String getType() {
        return "JPA";
    }

    /**
     * The class of the technical object represented by this repository. For instance, in the Ebean case,
     * it would be 'com.avaje.ebean.EbeanServer', while for MongoJack it would be 'org.mongojack.JacksonDBCollection'
     *
     * @return the class of the repository
     */
    @Override
    public Class<EntityManager> getRepositoryClass() {
        return EntityManager.class;
    }

    /**
     * The technical object represented by this repository.
     *
     * @return the current repository
     */
    @Override
    public EntityManager get() {
        return em;
    }

    /**
     * Stops the repository. It un-registers all crud services.
     */
    public void dispose() {
        for (ServiceRegistration registration : registrations) {
            registration.unregister();
        }
        registrations.clear();
        for (AbstractJTACrud crud : cruds) {
            crud.dispose();
        }
    }
}
