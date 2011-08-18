package org.cassandraunit.test;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

import org.cassandraunit.AbstractCassandraUnit4TestCase;
import org.cassandraunit.dataset.DataSet;
import org.cassandraunit.dataset.json.ClassPathJSONDataSet;
import org.junit.Test;

/**
 * 
 * @author Jeremy Sevellec
 *
 */
public class AutomaticallyStartAndLoadExtendedJSONDataSetTest extends AbstractCassandraUnit4TestCase {

	@Override
	public DataSet getDataSet() {
		return new ClassPathJSONDataSet("extendedDataSet.json");
	}

	@Test
	public void shouldHaveLoadAnExtendDataSet() throws Exception {
		assertThat(getKeyspace(), notNullValue());
		assertThat(getKeyspace().getKeyspaceName(), is("otherKeyspaceName"));
		/* and query all what you want */
	}

}
