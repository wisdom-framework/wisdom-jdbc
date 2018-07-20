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


import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleReference;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.hooks.weaving.WeavingHook;
import org.osgi.framework.hooks.weaving.WovenClass;
import org.osgi.framework.wiring.BundleWiring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wisdom.framework.osgi.Clauses;

import javax.persistence.spi.ClassTransformer;
import javax.persistence.spi.PersistenceProvider;
import java.io.IOException;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.regex.Pattern;

@Component
@Provides(specifications = {WeavingHook.class, JPATransformer.class})
@Instantiate
public class JPATransformer implements WeavingHook {

    //
    // The Transformers hook enables weaving in OSGi
    // Every persistence unit will register itself with
    // the transformers hook. Order is important, once
    // the bundle tracker is called, the transformers
    // will be registered so do not move this method lower.
    //

    private final static Pattern WORD = Pattern.compile("[a-zA-Z0-9]+");
    private final static Logger LOGGER = LoggerFactory.getLogger(JPATransformer.class);

    @Requires(proxy = false)
    PersistenceProvider persistenceProvider;

    private final Map<Bundle, List<ClassTransformer>> transformers = new LinkedHashMap<>();
    private final List<String> imports;
    private final ClassTransformer DUMMY_TRANSFORMER = new ClassTransformer() {

        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                ProtectionDomain protectionDomain, byte[] classfileBuffer)
                throws IllegalClassFormatException {
            return null;
        }

    };

    public JPATransformer() throws IOException {
        this.imports = getImports();
    }

    @Override
    public void weave(WovenClass clazz) {
        try {
            if (transformers.isEmpty()) {
                return;
            }

            BundleWiring wiring = clazz.getBundleWiring();
            Bundle b = wiring.getBundle();

            ClassTransformer trfs[];
            synchronized (transformers) {
                Collection<ClassTransformer> list = transformers.get(b);
                if (list == null) {
                    return;
                }
                trfs = list.toArray(new ClassTransformer[list.size()]);
            }
            LOGGER.debug("Transforming {} with {}", clazz.getClassName(), Arrays.toString(trfs));
            for (ClassTransformer ctf : trfs) {
                if (ctf != null) {
                    ctf.transform(wiring.getClassLoader(), clazz.getClassName(), clazz.getDefinedClass(),
                            clazz.getProtectionDomain(), clazz.getBytes());
                }
            }

            if (!imports.isEmpty()) {
                clazz.getDynamicImports().addAll(imports);
            }

        } catch (Exception e) {
            LOGGER.error("Error while weaving class {}", clazz.getClassName(), e);
        }
    }

    boolean register(Bundle b, ClassTransformer ctf) {
        LOGGER.info("register transformer {} on bundle {}", ctf, b);
        if (ctf == null) {
            ctf = DUMMY_TRANSFORMER;
        }
        synchronized (transformers) {
            List<ClassTransformer> list = transformers.get(b);
            if (list == null) {
                list = new ArrayList<>();
                transformers.put(b, list);
            }
            list.add(ctf);
            return true;
        }
    }

    boolean unregister(Bundle b) {
        LOGGER.info("unregister transformers from bundle {}", b);
        synchronized (transformers) {
            transformers.remove(b);
            return true;
        }
    }

    private List<String> getImports() throws IOException {
        Bundle bundle;
        if (persistenceProvider instanceof BundleReference) {
            bundle = ((BundleReference) persistenceProvider).getBundle();
        } else {
            bundle = FrameworkUtil.getBundle(persistenceProvider.getClass());
        }

        if (bundle != null) {
            // Get the export clauses of the JPA provider.
            Clauses clauses = Clauses.parse(bundle.getHeaders().get(Constants.EXPORT_PACKAGE));
            if (!clauses.isEmpty()) {
                List<String> list = new ArrayList<>();
                for (Map.Entry<String, Map<String, String>> e : clauses.entrySet()) {

                    // Create a new clause
                    StringBuilder sb = new StringBuilder();
                    sb.append(e.getKey());
                    for (Map.Entry<String, String> ee : e.getValue().entrySet()) {
                        if (ee.getKey().endsWith(":")) {
                            continue;
                        }

                        sb.append(";").append(ee.getKey()).append("=");
                        String v = ee.getValue();
                        if (WORD.matcher(v).matches()) {
                            sb.append(ee.getValue());
                        }  else {
                            sb.append("\"").append(ee.getValue()).append("\"");
                        }
                    }
                    list.add(sb.toString());
                }
                // To retrieve the transaction manager.
                list.add("org.wisdom.framework.jpa.accessor");
                return list;
            }
        }
        return Collections.emptyList();
    }

}
