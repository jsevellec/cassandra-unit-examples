package org.cassandraunit.test.cql;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import org.cassandraunit.CQLDataLoader;
import org.cassandraunit.dataset.cql.ClassPathCQLDataSet;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class CQLScriptLoadWithNativeApproachTest {

    private CqlSession session;

    @Before
    public void setUp() throws Exception {
        EmbeddedCassandraServerHelper.startEmbeddedCassandra();
        session = EmbeddedCassandraServerHelper.getSession();
        new CQLDataLoader(session).load(new ClassPathCQLDataSet("simple.cql", "keyspaceNameToCreate"));
    }

    @Test
    public void should_have_started_and_execute_cql_script() throws Exception {
        ResultSet result = session.execute("select * from mytable WHERE id='myKey01'");
        assertThat(result.iterator().next().getString("value"), is("myValue01"));
    }
}
