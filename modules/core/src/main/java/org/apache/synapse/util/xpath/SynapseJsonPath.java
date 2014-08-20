/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *   * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.apache.synapse.util.xpath;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.ParentAware;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.commons.json.JsonUtil;
import org.apache.synapse.config.SynapsePropertiesLoader;
import org.apache.synapse.config.xml.SynapsePath;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.jaxen.JaxenException;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.internal.PathTokenizer;

public class SynapseJsonPath extends SynapsePath {

    private static final Log log = LogFactory.getLog(SynapseJsonPath.class);

    private String enableStreamingJsonPath = SynapsePropertiesLoader.loadSynapseProperties().
    getProperty(SynapseConstants.STREAMING_JSONPATH_PROCESSING);

    private JsonPath jsonPath;

    private boolean isWholeBody = false;

    public SynapseJsonPath(String jsonPathExpression)  throws JaxenException {
        super(jsonPathExpression, SynapsePath.JSON_PATH, log);
        this.contentAware = true;
        this.expression = jsonPathExpression;
        jsonPath = JsonPath.compile(jsonPathExpression);
        // Check if the JSON path expression evaluates to the whole payload. If so no point in evaluating the path.
        if ("$".equals(jsonPath.getPath().trim()) || "$.".equals(jsonPath.getPath().trim())) {
            isWholeBody = true;
        }
        this.setPathType(SynapsePath.JSON_PATH);
    }

    public String stringValueOf(final String jsonString) {
        if (jsonString == null) {
            return "";
        }
        if (isWholeBody) {
            return jsonString;
        }
        Object read;
        read = jsonPath.read(jsonString);
        return (null == read ? "null" : read.toString());
    }

    public String stringValueOf(MessageContext synCtx) {
        org.apache.axis2.context.MessageContext amc = ((Axis2MessageContext) synCtx).getAxis2MessageContext();
        InputStream stream;
        if (!JsonUtil.hasAJsonPayload(amc) || "true".equals(enableStreamingJsonPath)) {
            try {
                if (null == amc.getEnvelope().getBody().getFirstElement()) {
                    // Get message from PT Pipe.
                    stream = getMessageInputStreamPT(amc);
                    if (stream == null) {
                        stream = JsonUtil.getJsonPayload(amc);
                    } else {
                        JsonUtil.newJsonPayload(amc, stream, true, true);
                    }
                } else {
                    // Message Already built.
                    stream = JsonUtil.toJsonStream(amc.getEnvelope().getBody().getFirstElement());
                }
                return stringValueOf(stream);
            } catch (IOException e) {
                handleException("Could not find JSON Stream in PassThrough Pipe during JSON path evaluation.", e);
            }
        } else {
            stream = JsonUtil.getJsonPayload(amc);
            return stringValueOf(stream);
        }
        return "";
    }

    public String stringValueOf(final InputStream jsonStream) {
        if (jsonStream == null) {
            return "";
        }
        if (isWholeBody) {
            try {
                return IOUtils.toString(jsonStream);
            } catch(IOException e) {
                log.error("#stringValueOf. Could not convert JSON input stream to String.");
                return "";
            }
        }
        Object read;
        try {
            read = jsonPath.read(jsonStream);
            if (log.isDebugEnabled()) {
                log.debug("#stringValueOf. Evaluated JSON path <" + jsonPath.getPath() + "> : <" + (read == null ? null : read.toString()) + ">");
            }
            return (null == read ? "null" : read.toString());
        } catch (IOException e) {
            handleException("Error evaluating JSON Path <" + jsonPath.getPath() + ">", e);
        } catch (Exception e) { // catch invalid json paths that do not match with the existing JSON payload.
            log.error("#stringValueOf. Error evaluating JSON Path <" + jsonPath.getPath() + ">. Returning empty result. Error>>> " + e.getLocalizedMessage());
            return "";
        }
        if (log.isDebugEnabled()) {
            log.debug("#stringValueOf. Evaluated JSON path <" + jsonPath.getPath() + "> : <null>.");
        }
        return "";
    }

    public String getJsonPathExpression() {
        return expression;
    }

    public void setJsonPathExpression(String jsonPathExpression) {
        this.expression = jsonPathExpression;
    }
    
    /**
     * Read the JSON Stream and returns a list of string representations of return JSON elements from JSON path.
     */
	@Override
	public Object evaluate(Object object) throws JaxenException {
		List result = null;
		MessageContext synCtx = null;
		if (object != null && object instanceof MessageContext) {
			synCtx = (MessageContext) object;
			result = listValueOf(synCtx);
		}
		if (result == null)
			result = new ArrayList();
		return result;
	}
    
    /* 
     * Read JSON stream and return and object
     */
	private List listValueOf(MessageContext synCtx) {
		org.apache.axis2.context.MessageContext amc = ((Axis2MessageContext) synCtx).getAxis2MessageContext();
		InputStream stream;
		if (!JsonUtil.hasAJsonPayload(amc) || "true".equals(enableStreamingJsonPath)) {
			try {
				if (null == amc.getEnvelope().getBody().getFirstElement()) {
					// Get message from PT Pipe.
					stream = getMessageInputStreamPT(amc);
					if (stream == null) {
						stream = JsonUtil.getJsonPayload(amc);
					} else {
						JsonUtil.newJsonPayload(amc, stream, true, true);
					}
				} else {
					// Message Already built.
					stream = JsonUtil.toJsonStream(amc.getEnvelope().getBody().getFirstElement());
				}
				return listValueOf(stream);
			} catch (IOException e) {
				handleException("Could not find JSON Stream in PassThrough Pipe during JSON path evaluation.",
				                e);
			}
		} else {
			stream = JsonUtil.getJsonPayload(amc);
			return listValueOf(stream);
		}
		return null;

	}
	
	private List listValueOf(final InputStream jsonStream) {
        if (jsonStream == null) {
            return null;
        }
        List result=new ArrayList();
        Object object;
        try {
        	object = jsonPath.read(jsonStream);
            if (log.isDebugEnabled()) {
                log.debug("#listValueOf. Evaluated JSON path <" + jsonPath.getPath() + "> : <" + (object == null ? null : object.toString()) + ">");
            }
            
            if(object !=null){
            	if(object instanceof JSONArray){
                	JSONArray arr = (JSONArray)object;
                	for (Object obj : arr) {
                		result.add(obj!=null?obj:"null");
                    }
            	}else{
            		result.add(object);
            	}
            }
        } catch (IOException e) {
            handleException("Error evaluating JSON Path <" + jsonPath.getPath() + ">", e);
        } catch (Exception e) { // catch invalid json paths that do not match with the existing JSON payload.
            log.error("#listValueOf. Error evaluating JSON Path <" + jsonPath.getPath() + ">. Returning empty result. Error>>> " + e.getLocalizedMessage());
        }
        if (log.isDebugEnabled()) {
            log.debug("#listValueOf. Evaluated JSON path <" + jsonPath.getPath() + "> : <null>.");
        }
        return result;
    }
	
	/**
	 * This method will evaluate the JSON expression and return the first parent object matching object.
	 * @param rootObject Root JSON Object or Array to evaluate
	 * @return Parent object of the evaluation result
	 */
	public Object findParent(Object rootObject){
		Object parent=null;
		Object obj=jsonPath.find(rootObject);
		if(obj!=null && obj instanceof ParentAware){
			parent=((ParentAware)obj).getParent();
		}else{
    		PathTokenizer tokenizer=new PathTokenizer(jsonPath.getPath());
    		tokenizer.removeLastPathToken();
    		StringBuilder sb=new StringBuilder();
    		List<String> fragments=tokenizer.getFragments();
    		for(int i=0;i<fragments.size();i++){
    			sb.append(fragments.get(i));
    			if(i<fragments.size()-1)
    				sb.append(".");
    		}
    		JsonPath tempPath=JsonPath.compile(sb.toString());
    		parent= tempPath.find(rootObject);
		}
		return parent;
	}
	
	/**
	 * This method will insert given child Object (JSONObject or JSONArray) to the matching path of the root object. Updated root object will be return back to the caller.
	 * 
	 * @param rootObject Root JSON Object or Array
	 * @param child new JSON object to be insert
	 * @return Updated Root Object
	 */
	public Object append(Object rootObject,Object child){
		Object parent=findParent(rootObject);
		return append(rootObject, parent, child);
	}
	
	/**
	 * This method will insert given child Object (JSONObject or JSONArray) to given parent object. Updated root object will be return back to the caller.
	 * 
	 * @param rootObject Root JSON Object or Array
	 * @param parent Parent JSON Object or Array
	 * @param child New item to insert
	 * @return Updated Root Object
	 */
	public Object append(Object rootObject, Object parent, Object child){
		if(rootObject!=null && rootObject.equals(parent)){
			JSONArray array=new JSONArray();
			array.add(rootObject);
			rootObject = array;
			parent=array;
		}
		if(parent !=null && parent instanceof JSONArray){
			((JSONArray)parent).add(child);
		}else if(parent!=null && parent instanceof JSONObject){
			Object currentChild=jsonPath.find(rootObject);
			if(currentChild!=null){
				if(currentChild instanceof JSONArray){
					rootObject = append(rootObject, currentChild, child);
				}else{
					JSONObject obj=(JSONObject)parent;
    				for(String key:obj.keySet()){
    					if(currentChild.equals(obj.get(key))){
    						JSONArray array=new JSONArray();
    						array.add(currentChild);
    						obj.put(key, array);
    						rootObject = append(rootObject, array, child);
    					}
    				}
				}
			}
		}
		return rootObject;
	}
	
	/**
	 * This method will remove given child object from the given parent object. Updated rootObject will be return back to the caller
	 * @param rootObject Root JSON Object or Array
	 * @param parent Parent JSON Object or Array
	 * @param child item to remove
	 * @return Updated Root Object
	 */
	public Object remove(Object rootObject, Object parent, Object child){
		if(parent !=null && parent instanceof JSONArray){
			((JSONArray)parent).remove(child);
		}else if(parent!=null && parent instanceof JSONObject){
			if(((JSONObject)parent).containsValue(child)){
				for(String key:((JSONObject)parent).keySet()){
					if(child.equals(((JSONObject)parent).get(key))){
						((JSONObject)parent).remove(key);
						break;
					}
				}
			}
		}
		return rootObject;
	}
	
	
	/**
	 * This method will be replace first matching item with given child object. Updated root object will be return back to the caller
	 * @param rootObject Root JSON Object or Array
	 * @param newChild New item to replace
	 * @return Updated Root Object
	 */
	public Object replace(Object rootObject, Object newChild){
		Object child=jsonPath.find(rootObject);
		Object parent=findParent(rootObject);
		if(parent !=null && parent instanceof JSONArray){
			((JSONArray)parent).remove(child);
			((JSONArray)parent).add(newChild);
		}else if(parent!=null && parent instanceof JSONObject){
			if(((JSONObject)parent).containsValue(child)){
				for(String key:((JSONObject)parent).keySet()){
					if(child.equals(((JSONObject)parent).get(key))){
						((JSONObject)parent).remove(key);
						((JSONObject)parent).put(key, newChild);
						break;
					}
				}
			}
		}
		return rootObject;
	}
}
