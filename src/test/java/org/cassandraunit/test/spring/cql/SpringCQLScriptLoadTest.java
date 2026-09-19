package org.cassandraunit.test.spring.cql;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * cassandra-unit inside a Spring test context.
 *
 * <p>There is no cassandra-unit-specific JUnit 5 extension for Spring: you use Spring's own
 * {@link SpringExtension} and plug cassandra-unit in as a {@code TestExecutionListener}.
 *
 * <p>{@link EmbeddedCassandra} is <em>mandatory</em> - the listener does a
 * {@code requireNonNull} on it, so {@link CassandraDataSet} on its own fails with an NPE.
 *
 * <p>{@link CassandraUnitTestExecutionListener} starts the server and reloads the dataset
 * before <em>every test method</em>, and cleans up after each one. For a single load per class
 * see {@link SpringCassandraUnitAnnotationTest}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration
@TestExecutionListeners(CassandraUnitTestExecutionListener.class)
@EmbeddedCassandra
@CassandraDataSet(value = "simple.cql", keyspace = "keyspaceNameToCreate")
class SpringCQLScriptLoadTest {

    /**
     * Spring needs a context to bootstrap. A nested static {@code @Configuration} class is
     * picked up automatically by the bare {@code @ContextConfiguration} above, which keeps the
     * example self-contained - no XML file to go and find.
     */
    @Configuration
    static class Config {
    }

    @Test
    void should_have_started_and_execute_cql_script() {
        CqlSession session = EmbeddedCassandraServerHelper.getSession();

        Row row = session.execute("select value from keyspaceNameToCreate.mytable where id = 'myKey01'").one();

        assertThat(row).isNotNull();
        assertThat(row.getString("value")).isEqualTo("myValue01");
    }

    /** The listener reloads the dataset before each method, so this sees the same data. */
    @Test
    void should_reload_the_dataset_for_every_test_method() {
        CqlSession session = EmbeddedCassandraServerHelper.getSession();

        Row row = session.execute("select value from keyspaceNameToCreate.mytable where id = 'myKey02'").one();

        assertThat(row).isNotNull();
        assertThat(row.getString("value")).isEqualTo("myValue02");
    }
}
