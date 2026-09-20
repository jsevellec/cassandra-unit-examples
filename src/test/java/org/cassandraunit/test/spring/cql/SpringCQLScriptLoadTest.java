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
 * cassandra-unit inside a Spring test context: Spring's own {@link SpringExtension} plus a
 * cassandra-unit {@code TestExecutionListener}. {@link EmbeddedCassandra} is mandatory - the
 * listener does a {@code requireNonNull} on it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration
@TestExecutionListeners(CassandraUnitTestExecutionListener.class)
@EmbeddedCassandra
@CassandraDataSet(value = "simple.cql", keyspace = "keyspaceNameToCreate")
class SpringCQLScriptLoadTest {

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

    @Test
    void should_reload_the_dataset_for_every_test_method() {
        CqlSession session = EmbeddedCassandraServerHelper.getSession();

        Row row = session.execute("select value from keyspaceNameToCreate.mytable where id = 'myKey02'").one();

        assertThat(row).isNotNull();
        assertThat(row.getString("value")).isEqualTo("myValue02");
    }
}
