package org.cassandraunit.test.cql;

import org.cassandraunit.CassandraCQLUnit;
import org.cassandraunit.dataset.CQLDataSetFactory;
import org.junit.Rule;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.cassandraunit.assertion.CqlAssertions.assertThat;

/**
 * {@code CqlAssertions} takes a session and nothing else, so it is the same API on JUnit 4 as on
 * Jupiter - no rule, no extension, no vintage engine involved in the assertion itself.
 */
public class CqlAssertionsRuleTest {

    private static final String KEYSPACE = "assertionsjunit4keyspace";
    private static final String ONE = "11111111-1111-1111-1111-111111111111";

    @Rule
    public CassandraCQLUnit cassandra = new CassandraCQLUnit(
            CQLDataSetFactory.fromClassPathAll(KEYSPACE,
                    "cql/assertionSchema.cql", "rows/assertion-data.yaml"));

    @Test
    public void asserts_one_value_without_writing_a_fixture() {
        assertThat(cassandra.getSession()).keyspace(KEYSPACE)
                .table("widget")
                    .hasRowCount(3)
                    .row("id", ONE)
                        .hasValue("label", "ordered")
                        .hasValue("quantity", 1)
                        .hasNonNull("created");
    }

    @Test
    public void asserts_what_exists() {
        assertThat(cassandra.getSession())
                .hasKeyspace(KEYSPACE)
                .doesNotHaveKeyspace("nosuchkeyspace")
                .keyspace(KEYSPACE)
                    .hasTable("widget");
    }

    @Test
    public void fails_with_the_value_it_found() {
        assertThatThrownBy(() -> assertThat(cassandra.getSession()).keyspace(KEYSPACE)
                .table("widget")
                    .row("id", ONE)
                        .hasValue("label", "shipped"))
                .hasMessageContaining("ordered");
    }
}
