/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dtstack.chunjun.connector.hbase14.sink;

/**
 * @author jiangbo
 * @date 2019/7/25
 */
public class ConstantFunction implements IFunction {

    private Object value;

    public ConstantFunction() {}

    public ConstantFunction(Object value) {
        this.value = value;
    }

    @Override
    public String evaluate(Object val) {
        return String.valueOf(value);
    }

    public void setValue(Object value) {
        this.value = value;
    }
}
