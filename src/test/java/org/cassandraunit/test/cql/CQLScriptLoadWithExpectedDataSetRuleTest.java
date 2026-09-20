package org.cassandraunit.test.cql;

import org.cassandraunit.CassandraCQLUnit;
import org.cassandraunit.assertion.ExpectedCassandraDataSet;
import org.cassandraunit.assertion.ExpectedCassandraDataSetRule;
import org.cassandraunit.dataset.CQLDataSetFactory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

/**
 * {@link ExpectedCassandraDataSet} on JUnit 4, through a second rule chained around
 * {@link CassandraCQLUnit}: an {@code ExternalResource} cannot see an annotation on the test
 * method, a {@code TestRule} can.
 */
public class CQLScriptLoadWithExpectedDataSetRuleTest {

    private static final String KEYSPACE = "junit4assertionkeyspace";

    private final CassandraCQLUnit cassandra = new CassandraCQLUnit(
            CQLDataSetFactory.fromClassPathAll(KEYSPACE,
                    "cql/assertionSchema.cql", "rows/assertion-data.yaml"));

    @Rule
    public RuleChain rules = RuleChain.outerRule(cassandra)
            .around(new ExpectedCassandraDataSetRule(cassandra::getSession));

    @Test
    @ExpectedCassandraDataSet(value = "rows/expected-widget.yaml", keyspace = KEYSPACE,
            ignoreColumns = "created")
    public void shipping_a_widget_changes_its_label() {
        cassandra.session.execute("update " + KEYSPACE + ".widget set label = 'shipped' "
                + "where id = 11111111-1111-1111-1111-111111111111");
    }
}
