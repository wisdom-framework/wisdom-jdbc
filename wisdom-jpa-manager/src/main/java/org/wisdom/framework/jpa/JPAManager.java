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

import com.google.common.base.Splitter;
import org.apache.felix.ipojo.Factory;
import org.apache.felix.ipojo.annotations.*;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.util.tracker.BundleTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wisdom.framework.jpa.model.Persistence;

import javax.xml.bind.JAXB;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The entry point of the JPA bridge.
 * This component tracks bundles and check if they contain a {@code Meta-Persistence} header. If so, is creates a
 * necessary persistence unit. By default, the tracker check for {@code META-INF/persistence.xml}
 */
@Component(immediate = true)
@Instantiate
public class JPAManager {

    /**
     * The Meta-Persistence header.
     */
    public static final String META_PERSISTENCE = "Meta-Persistence";

    /**
     * The logger.
     */
    private final static Logger LOGGER = LoggerFactory.getLogger(JPAManager.class);

    private Map<String, Persistence.PersistenceUnit> pus = new HashMap<>();

    /**
     * The bundle context, used to register the tracker.
     */
    @Context
    BundleContext context;

    /**
     * The factory used to create Persistence Unit 'instances'
     */
    @Requires(filter = "(factory.name=org.wisdom.framework.jpa.PersistenceUnitComponent)")
    Factory factory;

    /**
     * Set to be sure the weaving hook is registered first.
     */
    @Requires
    JPATransformer transformer;

    /**
     * The tracked bundle.
     */
    BundleTracker<PersistentBundle> bundles;


    @Validate
    void start() throws Exception {
        // Track bundles.
        bundles = new BundleTracker<PersistentBundle>(context, Bundle.ACTIVE + Bundle.STARTING, null) {

            /**
             * A new bundle arrives, check whether or not it contains persistence unit.
             * @param bundle the bundle
             * @param event the event
             * @return the Persistence Bundle object if the bundle contain PU, {@code null} if none (bundle not
             * tracked)
             */
            @Override
            public PersistentBundle addingBundle(Bundle bundle, BundleEvent event) {
                try {
                    // Parse any persistence units, returns null (not tracked) when there is no persistence unit
                    return parse(bundle);
                } catch (Exception e) {
                    LOGGER.error("While parsing bundle {} for a persistence unit we encountered " +
                                    "an unexpected exception {}. This bundle (also the other persistence " +
                                    "units in this bundle) will be ignored.",
                            bundle, e.getMessage(), e);
                    //noinspection Contract
                    return null;
                }
            }

            /**
             * A bundle is leaving.
             * @param bundle the bundle
             * @param event the event
             * @param pu the persistent bundle
             */
            @Override
            public void removedBundle(Bundle bundle, BundleEvent event, PersistentBundle pu) {

                pu.destroy();
            }
        };

        bundles.open();
    }

    /**
     * Closes the tracker.
     */
    @Invalidate
    void stop() {
        bundles.close();
    }

    /**
     * Check a bundle for persistence units following the rules in the OSGi
     * spec.
     * <p>
     * A Persistence Bundle is a bundle that specifies the Meta-Persistence
     * header, see Meta Persistence Header on page 439. This header refers to
     * one or more Persistence Descriptors in the Persistence Bundle. Commonly,
     * this is the META-INF/persistence.xml resource. This location is the
     * standard for non- OSGi environments, however an OSGi bundle can also use
     * other locations as well as multiple resources. Any entity classes must
     * originate in the bundle's JAR, it cannot come from a fragment. This
     * requirement is necessary to simplify enhancing entity classes.
     *
     * @param bundle the bundle to be searched
     * @return a Persistent Bundle or null if it has no matching persistence
     * units
     */
    PersistentBundle parse(Bundle bundle) throws Exception {
        LOGGER.debug("Analysing bundle {}", bundle.getBundleId());
        String metapersistence = bundle.getHeaders().get(META_PERSISTENCE);

        if (metapersistence == null || metapersistence.trim().isEmpty()) {
            // Check default location (except for system bundle)
            if (bundle.getBundleId() != 0 && bundle.getResource("META-INF/persistence.xml") != null) {
                // Found at the default location
                metapersistence = "META-INF/persistence.xml";
            } else {
                return null;
            }
        }
        LOGGER.info("META_PERSISTENCE header found in bundle {} : {}", bundle.getBundleId(), metapersistence);


        // We can have multiple persistence units.
        Set<Persistence.PersistenceUnit> set = new HashSet<>();
        for (String location : Splitter.on(",").omitEmptyStrings().trimResults().split(metapersistence)) {
            LOGGER.info("Analysing location {}", location);
            // Lets remember where we came from
            Persistence.PersistenceUnit.Properties.Property p =
                    new Persistence.PersistenceUnit.Properties.Property();
            p.setName("location");
            p.setValue(location);

            // Try to find the resource for the persistence unit
            // on the classpath: getResource

            URL url = bundle.getResource(location);
            if (url == null) {
                LOGGER.error("Bundle {} specifies location '{}' in the Meta-Persistence header but no such" +
                        " resource is found in the bundle at that location.", bundle, location);
            } else {
                // Parse the XML file.
                Persistence persistence = JAXB.unmarshal(url, Persistence.class);
                LOGGER.info("Parsed persistence: {}, unit {}", persistence, persistence.getPersistenceUnit());
                for (Persistence.PersistenceUnit pu : persistence.getPersistenceUnit()) {
                    final String jta = pu.getJtaDataSource();
                    if(!pus.containsKey(jta)) {
                        if (pu.getProperties() == null) {
                            pu.setProperties(new Persistence.PersistenceUnit.Properties());
                        }
                        pu.getProperties().getProperty().add(p);
                        set.add(pu);
                        pus.put(jta, pu);
                        LOGGER.info("Adding persistence unit {}", pu);
                    } else {
                        Persistence.PersistenceUnit previousPu = pus.get(jta);
                        previousPu.getClazz().addAll(pu.getClazz());
                        LOGGER.info("Recycling persistence unit {}", previousPu);
                    }
                }
            }
        }

        // Ignore this bundle if no valid PUs
        if (set.isEmpty()) {
            LOGGER.warn("No persistence unit found in bundle {}, despite a META_PERSISTENCE header ({})",
                    bundle.getBundleId(), metapersistence);
            return null;
        }

        return new PersistentBundle(bundle, set, factory);
    }
}
