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

import org.assertj.core.data.MapEntry;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClausesTest {

    @Test
    public void testSimpleHeader() {
        Clauses clauses = Clauses.parse("org.acme");
        assertThat(clauses).hasSize(1).containsKeys("org.acme");
        assertThat(clauses.get("org.acme")).isEmpty();
    }

    @Test
    public void testAttribute() {
        Clauses clauses = Clauses.parse("org.acme;version=1.0");
        assertThat(clauses).hasSize(1).containsKeys("org.acme");
        assertThat(clauses.get("org.acme")).containsExactly(MapEntry.entry("version", "1.0"));
    }

    @Test
    public void testAttributeThatIsAQuoted() {
        Clauses clauses = Clauses.parse("org.acme;version=1.0;uses=\"foo,bar,baz\"");
        assertThat(clauses).hasSize(1).containsKeys("org.acme");
        assertThat(clauses.get("org.acme"))
                .containsExactly(MapEntry.entry("version", "1.0"), MapEntry.entry("uses", "foo,bar,baz"));
    }

    @Test
    public void testDirective() {
        Clauses clauses = Clauses.parse("org.acme;resolution:=optional");
        assertThat(clauses).hasSize(1).containsKeys("org.acme");
        assertThat(clauses.get("org.acme")).containsExactly(MapEntry.entry("resolution:", "optional"));
    }

    @Test
    public void testTwoPackages() {
        Clauses clauses = Clauses.parse("org.acme, org.foo");
        assertThat(clauses).hasSize(2).containsKeys("org.acme", "org.foo");
        assertThat(clauses.get("org.acme")).isEmpty();
        assertThat(clauses.get("org.foo")).isEmpty();
    }

}