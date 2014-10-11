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
package org.wisdom.framework.osgi;

import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a parsed header value from an OSGi manifest.
 * Code taken from BND.
 */
public class Clauses extends LinkedHashMap<String, Map<String, String>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(Clauses.class);

    /**
     * Standard OSGi header parser. This parser can handle the format:
     * <ul>
     * <li>clauses ::= clause ( ',' clause ) +</li>
     * <li>clause ::= name ( ';' name ) (';' key '=' value )</li>
     * </ul>
     * <p>
     * The return structure is a Map of Map: <code>Map {name => Map {attribute | directive} => value}</code>.
     *
     * @param header the header to parse
     * @return the clauses
     */
    static public Clauses parse(String header) {
        if (Strings.isNullOrEmpty(header)) {
            return new Clauses();
        }

        Clauses result = new Clauses();
        QuotedTokenizer qt = new QuotedTokenizer(header, ";=,");
        char del;
        do {
            boolean hadAttribute = false;
            Map<String, String> clause = new LinkedHashMap<>();
            List<String> aliases = new ArrayList<String>();
            aliases.add(qt.nextToken());
            del = qt.getSeparator();
            while (del == ';') {
                String name = qt.nextToken();
                if ((del = qt.getSeparator()) != '=') {
                    // Found an ; or , - it's valid if we didn't find an attribute.
                    if (hadAttribute) {
                        throw new IllegalArgumentException("Header contains name field after " +
                                "attribute or directive: " + name + " from " + header);
                    }
                    aliases.add(name);
                } else {
                    // Parse attribute or directive.
                    String value = qt.nextToken();
                    clause.put(name, value);
                    del = qt.getSeparator();
                    hadAttribute = true;
                }
            }
            for (String packageName : aliases) {
                if (result.containsKey(packageName)) {
                    LOGGER.warn("Duplicate package name in header: " + packageName
                            + ". Multiple package names in one clause are supported.");
                } else {
                    result.put(packageName, clause);
                }
            }
        } while (del == ',');
        return result;
    }

}
