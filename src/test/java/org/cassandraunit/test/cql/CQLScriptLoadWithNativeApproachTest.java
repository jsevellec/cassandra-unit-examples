package org.cassandraunit.test.cql;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import org.cassandraunit.CQLDataLoader;
import org.cassandraunit.dataset.cql.ClassPathCQLDataSet;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No rule, no base class: start the server and load the dataset by hand.
 * {@code startEmbeddedCassandra()} is idempotent, so every test class in a fork can call it.
 */
public class CQLScriptLoadWithNativeApproachTest {

    private CqlSession session;

    @Before
    public void setUp() throws Exception {
        EmbeddedCassandraServerHelper.startEmbeddedCassandra();
        session = EmbeddedCassandraServerHelper.getSession();
        new CQLDataLoader(session).load(new ClassPathCQLDataSet("simple.cql", "keyspaceNameToCreate"));
    }

    @Test
    public void should_have_started_and_execute_cql_script() {
        Row row = session.execute("select value from mytable where id = 'myKey01'").one();

        assertThat(row).isNotNull();
        assertThat(row.getString("value")).isEqualTo("myValue01");
    }
}
