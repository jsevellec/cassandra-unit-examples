package org.cassandraunit.test.spring.cql;

import org.cassandraunit.assertion.ExpectedCassandraDataSet;
import org.cassandraunit.spring.CassandraDataSet;
import org.cassandraunit.spring.CassandraUnitTestExecutionListener;
import org.cassandraunit.spring.EmbeddedCassandra;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * {@link ExpectedCassandraDataSet} in a Spring test - nothing to wire up.
 *
 * <p>The cassandra-unit listeners check the annotation themselves, and they do it <b>before</b>
 * their cleanup drops every non-system keyspace. An expectation verified after that would find
 * nothing at all, so the ordering is not an accident.
 *
 * <p>{@link CassandraUnitTestExecutionListener} reloads the dataset before every test method, which
 * is what lets this test mutate the data and still leave the next one a clean database.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration
@TestExecutionListeners(CassandraUnitTestExecutionListener.class)
@EmbeddedCassandra
@CassandraDataSet(value = {"cql/assertionSchema.cql", "rows/assertion-data.yaml"},
        keyspace = SpringExpectedCassandraDataSetTest.KEYSPACE)
class SpringExpectedCassandraDataSetTest {

    static final String KEYSPACE = "springassertionkeyspace";

    @Configuration
    static class Config {
    }

    @Test
    @ExpectedCassandraDataSet(value = "rows/expected-widget.yaml", keyspace = KEYSPACE,
            ignoreColumns = "created")
    void shipping_a_widget_changes_its_label() {
        EmbeddedCassandraServerHelper.getSession()
                .execute("update " + KEYSPACE + ".widget set label = 'shipped' "
                        + "where id = 11111111-1111-1111-1111-111111111111");
    }
}
