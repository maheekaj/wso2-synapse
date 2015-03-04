/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.synapse.commons.json;

import org.apache.axis2.AxisFault;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.ObjectWriter;

/**
 * This class contains some util methods to use with JSON providers which are
 * used in JSONPath library
 */
public class JSONProviderUtil {
    private static final Log logger = LogFactory.getLog(JSONProviderUtil.class);
    // withDefaultPrettyPrinter() will format the JSON with indenting the
    // content and adds additional overhead. Use writer().withDefaultPrettyPrinter()
    private static ObjectWriter objectWriter = new ObjectMapper().writer();

    /**
     * When we use Jackson as JSON parser, it uses java Map and List classes and
     * toString methods does not format the content as a valid JSON. This method
     * will return the JSON format string by reading the contents of any given
     * object.
     *
     * @param object input object to convert as JSON String
     * @return JSON String of the given object or null
     */
    public static String objectToString(Object object){
        String json = null;
        try {
            json = objectWriter.writeValueAsString(object);
        } catch (Exception e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Error while converting object to JSON String", e);
            }
            // null should be returned if the object cannot be passed as a JSON. Since ignore exceptions
        }
        return json;
    }
}
