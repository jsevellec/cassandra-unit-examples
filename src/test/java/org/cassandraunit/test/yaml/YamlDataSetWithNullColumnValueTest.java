package org.cassandraunit.test.yaml;

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
import org.cassandraunit.dataset.yaml.ClassPathYamlDataSet;
import org.junit.Test;

import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

/**
 * 
 * @author Jeremy Sevellec
 * 
 */
public class YamlDataSetWithNullColumnValueTest extends AbstractCassandraUnit4TestCase {

	@Override
	public DataSet getDataSet() {
		return new ClassPathYamlDataSet("yaml/dataSetWithNullColumnValue.yaml");
	}

	@Test
	public void shouldHaveLoadADataSet() throws Exception {
		assertThat(getKeyspace(), notNullValue());
		assertThat(getKeyspace().getKeyspaceName(), is("keyspaceWithNullColumnValue"));
		/* and query all what you want */

		SliceQuery<String ,String , String> query = HFactory.createSliceQuery(getKeyspace(),
                StringSerializer.get(), StringSerializer.get(), StringSerializer.get());
		query.setColumnFamily("columnFamilyWithNullColumnValue");
		query.setKey("rowWithNullColumnValue");
        query.setRange(null,null,false,100);
		QueryResult<ColumnSlice<String, String>> result = query.execute();
		List<HColumn<String, String>> columns = result.get().getColumns();

		assertThat(columns.get(0).getName(),is("columnWithNullColumnValue1"));
		assertThat(columns.get(0).getValue(),is(""));

        assertThat(columns.get(1).getName(),is("columnWithNullColumnValue2"));
        assertThat(columns.get(1).getValue(),is(""));
        assertThat(columns.get(2).getName(),is("columnWithNullColumnValue3"));
        assertThat(columns.get(2).getValue(),is(""));
        assertThat(columns.get(3).getName(),is("columnWithNullColumnValue4"));
        assertThat(columns.get(3).getValue(),is(""));


	}

}
