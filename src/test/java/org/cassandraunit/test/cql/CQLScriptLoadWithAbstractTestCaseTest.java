package org.cassandraunit.test.cql;

import com.datastax.oss.driver.api.core.cql.ResultSet;
import org.cassandraunit.AbstractCassandraUnit4CQLTestCase;
import org.cassandraunit.CassandraCQLUnit;
import org.cassandraunit.dataset.CQLDataSet;
import org.cassandraunit.dataset.cql.AbstractCQLDataSet;
import org.cassandraunit.dataset.cql.ClassPathCQLDataSet;
import org.junit.Rule;
import org.junit.Test;

import java.io.InputStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class CQLScriptLoadWithAbstractTestCaseTest extends AbstractCassandraUnit4CQLTestCase {

    @Override
    public CQLDataSet getDataSet() {
        return new ClassPathCQLDataSet("simple.cql","keyspaceNameToCreate");
    }

    @Test
    public void should_have_started_and_execute_cql_script() throws Exception {
        ResultSet result = this.getSession().execute("select * from mytable WHERE id='myKey01'");
        assertThat(result.iterator().next().getString("value"), is("myValue01"));
    }
}
