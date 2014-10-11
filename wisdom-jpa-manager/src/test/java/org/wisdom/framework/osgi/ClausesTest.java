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