package org.cassandraunit.test.xml;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.util.List;

import me.prettyprint.cassandra.serializers.CompositeSerializer;
import me.prettyprint.cassandra.serializers.IntegerSerializer;
import me.prettyprint.cassandra.serializers.LongSerializer;
import me.prettyprint.cassandra.serializers.StringSerializer;
import me.prettyprint.hector.api.beans.ColumnSlice;
import me.prettyprint.hector.api.beans.Composite;
import me.prettyprint.hector.api.beans.HColumn;
import me.prettyprint.hector.api.factory.HFactory;
import me.prettyprint.hector.api.query.QueryResult;
import me.prettyprint.hector.api.query.SliceQuery;

import org.cassandraunit.AbstractCassandraUnit4TestCase;
import org.cassandraunit.dataset.DataSet;
import org.cassandraunit.dataset.xml.ClassPathXmlDataSet;
import org.junit.Test;

/**
 * 
 * @author Jeremy Sevellec
 *
 */
public class AutomaticallyStartAndLoadExtendedXmlDataSetTest extends AbstractCassandraUnit4TestCase {

	@Override
	public DataSet getDataSet() {
		return new ClassPathXmlDataSet("extendedDataSet.xml");
	}

	@Test
	public void shouldHaveLoadAnExtendDataSet() throws Exception {
		assertThat(getKeyspace(), notNullValue());
		assertThat(getKeyspace().getKeyspaceName(), is("otherKeyspaceName"));
		/* and query all what you want */
		
		SliceQuery<Composite, Composite, String> query = HFactory.createSliceQuery(getKeyspace(),
				new CompositeSerializer(), new CompositeSerializer(), StringSerializer.get());
		Composite key = new Composite();
		key.addComponent(10L, LongSerializer.get());
		key.addComponent("azerty", StringSerializer.get());
		query.setColumnFamily("columnFamilyWithCompositeType").setKey(key);
		query.setRange(null, null, false, 1000);
		QueryResult<ColumnSlice<Composite, String>> result = query.execute();
		List<HColumn<Composite, String>> columns = result.get().getColumns();

		assertThat(columns.get(0).getName().get(0, StringSerializer.get()), is("aa"));
		assertThat(columns.get(0).getName().get(1, IntegerSerializer.get()), is(10));
		assertThat(columns.get(0).getName().get(2, StringSerializer.get()), is("aa"));

		assertThat(columns.get(1).getName().get(0, StringSerializer.get()), is("aa"));
		assertThat(columns.get(1).getName().get(1, IntegerSerializer.get()), is(10));
		assertThat(columns.get(1).getName().get(2, StringSerializer.get()), is("ab"));
		
	}

}
