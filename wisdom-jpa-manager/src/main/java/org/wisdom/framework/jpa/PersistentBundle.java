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

import org.apache.felix.ipojo.*;
import org.osgi.framework.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wisdom.database.jdbc.service.DataSources;
import org.wisdom.framework.jpa.model.Persistence;

import javax.sql.DataSource;
import java.util.*;

/**
 * This class represents a bundle with one or more valid Persistence Units. It
 * maintains a the service registrations for the TransactionalEntityManager for
 * each persistence units and closes them when applicable.
 */
class PersistentBundle {


    private final Set<ComponentInstance> instances = new HashSet<>();

    final Bundle bundle;
    private final Factory factory;

    final static Logger LOGGER = LoggerFactory.getLogger(PersistentBundle.class);
    /**
     * We found some persistence units for this bridge so create this manager.
     *
     * @param bundle  the actual bundle for the persistence units
     * @param set     a set of persistence units.
     * @param factory the PersistenceUnit factory
     */
    PersistentBundle(Bundle bundle, Set<Persistence.PersistenceUnit> set,
                     Factory factory) throws Exception {
        LOGGER.info("Creating persistence bundle for {}", bundle.getBundleId());
        this.factory = factory;
        this.bundle = bundle;

        for (Persistence.PersistenceUnit pu : set) {
            instances.add(createUnitInstance(pu));
        }
    }

    public void destroy() {
        for (ComponentInstance instance : instances) {
            instance.dispose();
        }
        instances.clear();
    }

    private ComponentInstance createUnitInstance(Persistence.PersistenceUnit unit) throws MissingHandlerException,
            UnacceptableConfiguration, ConfigurationException {
        LOGGER.info("Creating persistence unit instance for unit {}", unit.getName());
        Dictionary<String, Object> configuration = new Hashtable<>();
        configuration.put("bundle", this);
        configuration.put("unit", unit);
        Dictionary<String, String> filters = new Hashtable<>();
        filters.put("jta-ds", createDataSourceFilter(unit.getJtaDataSource()));
        filters.put("ds", createDataSourceFilter(unit.getNonJtaDataSource()));
        configuration.put("requires.filters", filters);
        LOGGER.info("Creating persistence unit instance for unit {} : {}", unit.getName(), configuration);
        return factory.createComponentInstance(configuration);
    }

    private String createDataSourceFilter(String name) {
        if (name == null) {
            return "(" + DataSources.DATASOURCE_NAME_PROPERTY + "= not-set)";
        }
        if (name.startsWith("osgi:service/" + DataSource.class.getName() + "/")) {
            return name.substring(("osgi:service/" + DataSource.class.getName() + "/").length());
        }
        if (name.startsWith("(")  && name.endsWith(")")) {
            return name;
        }
        return "(" + DataSources.DATASOURCE_NAME_PROPERTY + "=" + name + ")";
    }
}
