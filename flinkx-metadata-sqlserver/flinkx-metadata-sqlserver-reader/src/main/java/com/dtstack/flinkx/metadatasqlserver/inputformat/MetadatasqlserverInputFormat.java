/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dtstack.flinkx.metadatasqlserver.inputformat;


import com.dtstack.flinkx.metadatasqlserver.constants.SqlServerMetadataCons;
import com.dtstack.flinkx.metadatasqlserver.entity.MetadatasqlserverEntity;
import com.dtstack.flinkx.metadatasqlserver.entity.SqlserverIndexEntity;
import com.dtstack.flinkx.metadatasqlserver.entity.SqlserverPartitionEntity;
import com.dtstack.flinkx.metadatasqlserver.entity.SqlserverTableEntity;
import com.dtstack.flinkx.util.ExceptionUtil;
import com.dtstack.metadata.rdb.core.entity.ColumnEntity;
import com.dtstack.metadata.rdb.core.entity.MetadatardbEntity;
import com.dtstack.metadata.rdb.inputformat.MetadatardbInputFormat;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.flink.types.Row;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static com.dtstack.flinkx.metadatasqlserver.constants.SqlServerMetadataCons.KEY_ZERO;
import static com.dtstack.metadata.rdb.core.constants.RdbCons.KEY_FALSE;
import static com.dtstack.metadata.rdb.core.constants.RdbCons.KEY_TRUE;

/**
 * @author : kunni@dtstack.com
 * @date : 2020/08/06
 */

public class MetadatasqlserverInputFormat extends MetadatardbInputFormat {

    private static final long serialVersionUID = 1L;

    /**当前schema*/
    protected String schema;

    /**当前表*/
    protected String table;


    @Override
    protected void doOpenInternal() {
        try {
            if (connection == null) {
                connection = getConnection();
                statement = connection.createStatement();
            }
            switchDatabase();
            if (CollectionUtils.isEmpty(tableList)) {
                tableList = showTables();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    protected Row nextRecordInternal(Row row) {

        MetadatasqlserverEntity metadatasqlserverEntity = new MetadatasqlserverEntity();

        currentObject = iterator.next();
        Map<String, String> map = (Map<String, String>) currentObject;
        schema = map.get(SqlServerMetadataCons.KEY_SCHEMA_NAME);
        table = map.get(SqlServerMetadataCons.KEY_TABLE_NAME);
        currentObject = table;

        try {
            metadatasqlserverEntity = (MetadatasqlserverEntity) createMetadatardbEntity();
            metadatasqlserverEntity.setDatabaseName(currentDatabase);
            metadatasqlserverEntity.setSchema(schema);
            metadatasqlserverEntity.setTableName(table);
            metadatasqlserverEntity.setQuerySuccess(true);

        } catch (Exception e) {
            metadatasqlserverEntity.setQuerySuccess(false);
            metadatasqlserverEntity.setErrorMsg(ExceptionUtil.getErrorMessage(e));
            throw new RuntimeException(e);
        }
        return Row.of(metadatasqlserverEntity);
    }

    @Override
    public List<Object> showTables() throws SQLException {
        List<Object> tableNameList = new LinkedList<>();
        try (ResultSet rs = statement.executeQuery(SqlServerMetadataCons.SQL_SHOW_TABLES)) {
            while (rs.next()) {
                HashMap<String, String> map = new HashMap<>();
                map.put(SqlServerMetadataCons.KEY_SCHEMA_NAME, rs.getString(1));
                map.put(SqlServerMetadataCons.KEY_TABLE_NAME, rs.getString(2));
                tableNameList.add(map);
            }
        }
        return tableNameList;
    }

    @Override
    public MetadatardbEntity createMetadatardbEntity() throws Exception {
        MetadatasqlserverEntity metadatasqlserverEntity = new MetadatasqlserverEntity();

        SqlserverTableEntity tableEntity = queryTableProp();
        tableEntity.setIndex(queryIndexes());
        tableEntity.setPrimaryKey(queryTablePrimaryKey());
        tableEntity.setPartition(queryPartition());

        List<ColumnEntity> columns = (List<ColumnEntity>) queryColumn(schema);
        String key = queryPartitionColumn();
        List<ColumnEntity> partitionColumn = distinctPartitionColumn(columns, key);

        metadatasqlserverEntity.setColumns(columns);
        metadatasqlserverEntity.setPartionColumns(partitionColumn);
        metadatasqlserverEntity.setTableProperties(tableEntity);
        return metadatasqlserverEntity;
    }


    private void switchDatabase() throws SQLException {
        // database 以数字开头时，需要双引号
        statement.execute(String.format(SqlServerMetadataCons.SQL_SWITCH_DATABASE, currentDatabase));
    }

    private List<SqlserverPartitionEntity> queryPartition() throws SQLException {
        List<SqlserverPartitionEntity> partitions = new ArrayList<>();
        String sql = String.format(SqlServerMetadataCons.SQL_SHOW_PARTITION, quote(table), quote(schema));
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                SqlserverPartitionEntity partitionEntity = new SqlserverPartitionEntity();
                partitionEntity.setColumnName(resultSet.getString(1));
                partitionEntity.setRows(resultSet.getLong(2));
                partitionEntity.setCreateTime(resultSet.getString(3));
                partitionEntity.setFileGroupName(resultSet.getString(4));
            }
        }
        return partitions;
    }


    private String queryPartitionColumn() throws SQLException {
        String partitionKey = null;
        String sql = String.format(SqlServerMetadataCons.SQL_SHOW_PARTITION_COLUMN, quote(table), quote(schema));
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                partitionKey = resultSet.getString(1);
            }
        }
        return partitionKey;
    }

    /**
     * 区分分区字段和非分区字段
     *
     * @param columns 所有字段
     * @param key     分区字段
     */
    private List<ColumnEntity> distinctPartitionColumn(List<ColumnEntity> columns, String key) {
        List<ColumnEntity> partitionColumn = new ArrayList<>();
        if (StringUtils.isNotEmpty(key)) {
            columns.removeIf(columnEntity ->
            {
                if (StringUtils.equals(key, columnEntity.getName())) {
                    partitionColumn.add(columnEntity);
                    return true;
                } else {
                    return false;
                }
            });
        }
        return partitionColumn;
    }

    private List<SqlserverIndexEntity> queryIndexes() throws SQLException {
        List<SqlserverIndexEntity> result = new ArrayList<>();
        //索引名对columnName的映射
        HashMap<String, ArrayList<String>> indexColumns = new HashMap<>(16);
        //索引名对索引类型的映射
        HashMap<String, String> indexType = new HashMap<>(16);

        ResultSet resultSet = connection.getMetaData().getIndexInfo(currentDatabase, schema, table, false, false);
        while (resultSet.next()) {
            ArrayList<String> columns = indexColumns.get(resultSet.getString("INDEX_NAME"));
            if (columns != null) {
                columns.add(resultSet.getString("COLUMN_NAME"));
            } else if (resultSet.getString("COLUMN_NAME") != null) {
                ArrayList<String> list = new ArrayList<>();
                list.add(resultSet.getString("COLUMN_NAME"));
                indexColumns.put(resultSet.getString("INDEX_NAME"), list);
            }
        }

        String sql = String.format(SqlServerMetadataCons.SQL_SHOW_TABLE_INDEX, quote(table), quote(schema));
        try (ResultSet indexResultSet = statement.executeQuery(sql)) {
            while (indexResultSet.next()) {
                indexType.put(indexResultSet.getString(1)
                        , indexResultSet.getString(3));
            }
        }
        for (String key : indexColumns.keySet()) {
            result.add(new SqlserverIndexEntity(key, indexType.get(key), indexColumns.get(key)));
        }

        return result;
    }


    private List<String> queryTablePrimaryKey() throws SQLException {
        List<String> primaryKey = new ArrayList<>();
        ResultSet resultSet = connection.getMetaData().getPrimaryKeys(currentDatabase, schema, table);
        while (resultSet.next()) {
            primaryKey.add(resultSet.getString("COLUMN_NAME"));
        }
        return primaryKey;
    }


    private SqlserverTableEntity queryTableProp() throws SQLException {
        SqlserverTableEntity tableEntity = new SqlserverTableEntity();
        String sql = String.format(SqlServerMetadataCons.SQL_SHOW_TABLE_PROPERTIES, quote(table), quote(schema));
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                tableEntity.setCreateTime(resultSet.getString(1));
                tableEntity.setRows(resultSet.getLong(2));
                tableEntity.setTotalSize(resultSet.getLong(3));
                tableEntity.setComment(resultSet.getString(4));
            }
        }
        tableEntity.setTableName(table);
        return tableEntity;
    }


    private String quote(String name) {
        return "'" + name + "'";
    }


    @Override
    public List<ColumnEntity> queryColumn(String schema) throws SQLException {
        List<ColumnEntity> columnEntities = new ArrayList<>();
        String sql = String.format(SqlServerMetadataCons.SQL_SHOW_TABLE_COLUMN, quote(table), quote(schema));
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                ColumnEntity perColumn = new ColumnEntity();
                perColumn.setName(resultSet.getString(1));
                perColumn.setType(resultSet.getString(2));
                perColumn.setComment(resultSet.getString(3));
                perColumn.setNullAble(StringUtils.equals(resultSet.getString(4), KEY_ZERO) ? KEY_FALSE : KEY_TRUE);
                perColumn.setLength(resultSet.getInt(5));
                perColumn.setDefaultValue(resultSet.getString(6));
                perColumn.setIndex(resultSet.getInt(7));
                columnEntities.add(perColumn);
            }
        }
        sql = String.format(SqlServerMetadataCons.SQL_QUERY_PRIMARY_KEY, quote(table), quote(schema));
        String primaryKey = null;
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                primaryKey = resultSet.getString(1);
            }
        }
        for (ColumnEntity columnEntity : columnEntities) {
            columnEntity.setPrimaryKey(StringUtils.equals(columnEntity.getName(), primaryKey) ? KEY_TRUE : KEY_FALSE);
        }
        return columnEntities;
    }
}
