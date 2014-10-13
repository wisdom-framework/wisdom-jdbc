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

import com.google.common.collect.ImmutableSet;
import org.apache.felix.ipojo.ComponentInstance;
import org.apache.felix.ipojo.Factory;
import org.apache.openjpa.persistence.PersistenceProviderImpl;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.Version;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.jdbc.DataSourceFactory;
import org.wisdom.framework.entities.Student;
import org.wisdom.framework.jpa.model.Persistence;
import org.wisdom.framework.jpa.model.PersistenceUnitTransactionType;
import org.wisdom.jdbc.driver.h2.H2Service;

import javax.sql.DataSource;
import java.util.Dictionary;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class PersistenceUnitComponentTest {


    @Test
    public void testCreationUsingJTATransactionMode() throws Exception {
        Factory factory = mock(Factory.class);
        when(factory.createComponentInstance(any(Dictionary.class))).thenReturn(mock(ComponentInstance.class));
        Bundle bundle = mock(Bundle.class);
        when(bundle.getVersion()).thenReturn(new Version(1,0,0));
        BundleWiring wiring = mock(BundleWiring.class);
        when(wiring.getClassLoader()).thenReturn(this.getClass().getClassLoader());
        when(bundle.adapt(BundleWiring.class)).thenReturn(wiring);
        BundleContext context = mock(BundleContext.class);
        when(context.getBundle()).thenReturn(bundle);
        when(context.registerService(any(Class.class), any(), any(Dictionary.class))).thenReturn(mock(ServiceRegistration.class));
        Persistence.PersistenceUnit pu = new Persistence.PersistenceUnit();
        pu.setName("unit-test");
        pu.setJtaDataSource("data");
        pu.setNonJtaDataSource("data");
        pu.setTransactionType(PersistenceUnitTransactionType.fromValue("JTA"));
        pu.getClazz().add(Student.class.getName());
        final Persistence.PersistenceUnit.Properties properties = new Persistence.PersistenceUnit.Properties();
        final Persistence.PersistenceUnit.Properties.Property property
                = new Persistence.PersistenceUnit.Properties.Property();
        property.setName("location");
        property.setValue("META-INF/persistence.xml");
        properties.getProperty().add(property);
        pu.setProperties(properties);
        PersistentBundle pb = new PersistentBundle(bundle, ImmutableSet.of(pu), factory);
        PersistenceUnitComponent component = new PersistenceUnitComponent(pb, pu, context);
        component.jtaDataSource = new JdbcDataSource();
        component.nonJtaDataSource = new JdbcDataSource();
        component.provider = new PersistenceProviderImpl();
        component.transformer = mock(JPATransformer.class);

        component.start();

        // Check registration
        assertThat(component.emfRegistration).isNotNull();
        assertThat(component.emRegistration).isNotNull();

        component.shutdown();
    }

    @Test
    public void testCreationUsingResourceLocalTransactionMode() throws Exception {
        Factory factory = mock(Factory.class);
        when(factory.createComponentInstance(any(Dictionary.class))).thenReturn(mock(ComponentInstance.class));
        Bundle bundle = mock(Bundle.class);
        when(bundle.getVersion()).thenReturn(new Version(1,0,0));
        BundleWiring wiring = mock(BundleWiring.class);
        when(wiring.getClassLoader()).thenReturn(this.getClass().getClassLoader());
        when(bundle.adapt(BundleWiring.class)).thenReturn(wiring);
        BundleContext context = mock(BundleContext.class);
        when(context.getBundle()).thenReturn(bundle);
        when(context.registerService(any(Class.class), any(), any(Dictionary.class))).thenReturn(mock(ServiceRegistration.class));
        Persistence.PersistenceUnit pu = new Persistence.PersistenceUnit();
        pu.setName("unit-test");
        pu.setJtaDataSource("data");
        pu.setNonJtaDataSource("data");
        pu.setTransactionType(
                PersistenceUnitTransactionType.fromValue("RESOURCE_LOCAL"));
        pu.getClazz().add(Student.class.getName());
        final Persistence.PersistenceUnit.Properties properties = new Persistence.PersistenceUnit.Properties();
        final Persistence.PersistenceUnit.Properties.Property property
                = new Persistence.PersistenceUnit.Properties.Property();
        property.setName("location");
        property.setValue("META-INF/persistence.xml");
        properties.getProperty().add(property);
        pu.setProperties(properties);
        PersistentBundle pb = new PersistentBundle(bundle, ImmutableSet.of(pu), factory);
        PersistenceUnitComponent component = new PersistenceUnitComponent(pb, pu, context);
        H2Service h2 = new H2Service();
        DataSource ds = h2.createDataSource(getDataSourceProperties());

        component.jtaDataSource = ds;
        component.nonJtaDataSource = ds;
        component.provider = new PersistenceProviderImpl();
        component.transformer = mock(JPATransformer.class);

        component.start();

        // Check registration
        assertThat(component.emfRegistration).isNotNull();
        assertThat(component.emRegistration).isNotNull();

        component.shutdown();
    }

    private Properties getDataSourceProperties() {
        Properties props = new Properties();
        props.put(DataSourceFactory.JDBC_URL, "jdbc:h2:mem:test");
        props.put(DataSourceFactory.JDBC_USER, "user");
        props.put(DataSourceFactory.JDBC_PASSWORD, "password");
        return props;
    }

}