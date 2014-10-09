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

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Clauses extends LinkedHashMap<String,Map<String,String>> {
	private static final long	serialVersionUID	= 1L;

	/**
	 * Standard OSGi header parser. This parser can handle the format clauses
	 * ::= clause ( ',' clause ) + clause ::= name ( ';' name ) (';' key '='
	 * value ) This is mapped to a Map { name => Map { attr|directive => value }
	 * }
	 * 
	 * @param value
	 * @return
	 */
	static public Clauses parse(String value, Logger logger) {
		if (value == null || value.trim().length() == 0)
			return new Clauses();

		Clauses result = new Clauses();
		QuotedTokenizer qt = new QuotedTokenizer(value, ";=,");
		char del;
		do {
			boolean hadAttribute = false;
			Clause clause = new Clause();
			List<String> aliases = new ArrayList<String>();
			aliases.add(qt.nextToken());
			del = qt.getSeparator();
			while (del == ';') {
				String adname = qt.nextToken();
				if ((del = qt.getSeparator()) != '=') {
					if (hadAttribute)
						throw new IllegalArgumentException("Header contains name field after attribute or directive: "
								+ adname + " from " + value);
					aliases.add(adname);
				} else {
					String advalue = qt.nextToken();
					clause.put(adname, advalue);
					del = qt.getSeparator();
					hadAttribute = true;
				}
			}
            for (String packageName : aliases) {
                if (result.containsKey(packageName)) {
                    if (logger != null)
                        logger.warn("Duplicate package name in header: " + packageName
                                + ". Multiple package names in one clause not supported in Bnd.");
                } else
                    result.put(packageName, clause);
            }
		} while (del == ',');
		return result;
	}

}
