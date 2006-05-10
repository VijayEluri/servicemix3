/*
 * Copyright 2005-2006 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.servicemix.jdbc.adapter;

import org.apache.servicemix.jdbc.Statements;


/**
 * Implements all the default JDBC operations that are used
 * by the JDBCPersistenceAdapter.
 * <p/>
 * Subclassing is encouraged to override the default
 * implementation of methods to account for differences
 * in JDBC Driver implementations.
 * <p/>
 * The JDBCAdapter inserts and extracts BLOB data using the
 * getBytes()/setBytes() operations.
 * <p/>
 * The databases/JDBC drivers that use this adapter are:
 * <ul>
 * <li></li>
 * </ul>
 *
 * @version $Revision: 1.1 $
 */
public class PostgresqlJDBCAdapter extends BytesJDBCAdapter {

    public void setStatements(Statements statements) {
        statements.setBinaryDataType("BYTEA");
        super.setStatements(statements);
    }

}
