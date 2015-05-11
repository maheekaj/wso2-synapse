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

package org.apache.synapse.mediators.eip;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMNode;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseException;
import org.apache.synapse.util.xpath.SynapseJsonPath;
import org.apache.synapse.util.xpath.SynapseXPath;
import org.jaxen.JaxenException;

/**
 * Utility methods for the EIP mediators
 */
public class EIPUtils {

    private static final Log log = LogFactory.getLog(EIPUtils.class);
    
    private static SynapseJsonPath rootJsonPath=null;
    static{
    	try{
    		rootJsonPath=new SynapseJsonPath("$.");
    	}catch(JaxenException e){
    	}
    }
    
    /**
     * Return the set of elements specified by the XPath over the given envelope
     *
     * @param envelope SOAPEnvelope from which the elements will be extracted
     * @param expression SynapseXPath expression describing the elements to be extracted
     * @return List OMElements in the envelope matching the expression
     * @throws JaxenException if the XPath expression evaluation fails
     */
    public static List getMatchingElements(SOAPEnvelope envelope, SynapseXPath expression)
        throws JaxenException {
        return getMatchingElements(envelope,null,expression);
    }

    /**
     * Return the set of elements specified by the XPath over the given envelope
     *
     * @param envelope SOAPEnvelope from which the elements will be extracted
     * @param expression SynapseXPath expression describing the elements to be extracted
     * @return List OMElements in the envelope matching the expression
     * @throws JaxenException if the XPath expression evaluation fails
     */
    public static List getMatchingElements(SOAPEnvelope envelope,MessageContext synCtxt, SynapseXPath expression)
        throws JaxenException {

        Object o = expression.evaluate(envelope, synCtxt);
        if (o instanceof OMNode) {
            List list = new ArrayList();
            list.add(o);
            return list;
        } else if (o instanceof List) {
            return (List) o;
        } else {
            return new ArrayList();
        }
    }

    /**
     * Return the set of detached elements specified by the XPath over the given envelope
     *
     * @param envelope SOAPEnvelope from which the elements will be extracted
     * @param expression SynapseXPath expression describing the elements to be extracted
     * @return List detached OMElements in the envelope matching the expression
     * @throws JaxenException if the XPath expression evaluation fails
     */
    public static List<OMNode> getDetachedMatchingElements(SOAPEnvelope envelope, MessageContext synCtxt,
                                                           SynapseXPath expression)
        throws JaxenException {

        List<OMNode> elementList = new ArrayList<OMNode>();
        Object o = expression.evaluate(envelope, synCtxt);
        if (o instanceof OMNode) {
            elementList.add(((OMNode) o).detach());
        } else if (o instanceof List) {
            for (Object elem : (List) o) {
                if (elem instanceof OMNode) {
                    elementList.add(((OMNode) elem).detach());
                }
            }
        }
        return elementList;
    }

    /**
     * Merge two SOAP envelopes using the given XPath expression that specifies the
     * element that enriches the first envelope from the second
     *
     * @param envelope   SOAPEnvelope to be enriched with the content
     * @param enricher   SOAPEnvelope from which the enriching element will be extracted
     * @param expression SynapseXPath describing the enriching element
     * @throws JaxenException on failing of processing the xpath
     */
    public static void enrichEnvelope(SOAPEnvelope envelope, SOAPEnvelope enricher,
                                      MessageContext synCtxt,
                                      SynapseXPath expression) throws JaxenException {

        OMElement enrichingElement;
        List elementList = getMatchingElements(envelope, synCtxt, expression);
        List list = getMatchingElements(enricher, synCtxt, expression);
        if ((checkNotEmpty(elementList) && checkNotEmpty(list))
            || (!checkNotEmpty(elementList) && checkNotEmpty(list))) {
            if (checkNotEmpty(elementList)) {
                // attach at parent of the first result from the XPath, or to the SOAPBody
                Object o = elementList.get(0);

                if (o instanceof OMElement &&
                    ((OMElement) o).getParent() != null &&
                    ((OMElement) o).getParent() instanceof OMElement) {
                    enrichingElement = (OMElement) ((OMElement) o).getParent();
                    OMElement body = envelope.getBody();
                    if (!isBody(body, enrichingElement)) {
                        OMElement nonBodyElem = enrichingElement;
                        enrichingElement = envelope.getBody();
                        addChildren(elementList, enrichingElement);
                        while (!isBody(body, (OMElement) nonBodyElem.getParent())) {
                            nonBodyElem = (OMElement) nonBodyElem.getParent();
                        }
                        nonBodyElem.detach();
                    }
                }
            }
            enrichingElement = envelope.getBody();
            if (list != null) {
                addChildren(list, enrichingElement);
            }
        } else {
            throw new SynapseException("Could not find matching elements to aggregate.");
        }
    }
    
    /**
     * This method will extract all the matching message parts from enricherContext and concatenate into rootJsonObject.
     * @param rootJsonObject Root JSON Object
     * @param enricherContext new message context
     * @param expression expression to extract message parts
     * @return Updated Root JSON Object
     * @throws JaxenException
     */
	public static Object enrichJSONSStream(Object rootJsonObject, MessageContext enricherContext,
	                                       SynapseJsonPath expression) throws JaxenException {
		Object newItemsObj = expression.evaluate(enricherContext);
		if (newItemsObj instanceof List && !((List) newItemsObj).isEmpty()) {
			if (rootJsonObject != null) {
				for (Object item : (List) newItemsObj) {
					rootJsonObject = expression.appendToParent(rootJsonObject, item);
				}
			}
		}
		return rootJsonObject;
	}
    
    public static Object getRootJSONObject(MessageContext messageContext) throws JaxenException{
    	return getRootJSON(messageContext);
    }
    
    public static Object getRootJSONObject(String json) throws JaxenException{
    	return getRootJSON(json);
    }
    
    private static Object getRootJSON(Object object) throws JaxenException{
    	Object root=null;
    	Object objectList=rootJsonPath.evaluate(object);
    	if (objectList instanceof List) {
			List list = (List) objectList;
			if (list != null && !list.isEmpty()){
				root = list.get(0);
			}
    	}
        return root;
    }

    private static boolean isBody(OMElement body, OMElement enrichingElement) {
        try {
            return (body.getLocalName().equals(enrichingElement.getLocalName()) &&
                    body.getNamespace().getNamespaceURI().equals(enrichingElement.getNamespace().getNamespaceURI()));
        } catch (NullPointerException e) {
            return false;
        }
    }

    private static boolean checkNotEmpty(List list) {
        return list != null && !list.isEmpty();
    }

    private static void addChildren(List list, OMElement element) {
        Iterator itr = list.iterator();
        Object o;
        while (itr.hasNext()) {
            o = itr.next();
            if (o != null && o instanceof OMElement) {
                element.addChild((OMElement) o);
            }
            itr.remove();
        }
    }

    /**
     * Util functions related to EIP Templates
     */
    public static String getTemplatePropertyMapping(String templateName, String parameter) {
        return templateName + ":" + parameter;
    }

    public static void createSynapseEIPTemplateProperty(MessageContext synCtxt, String templateName,
                                                        String  paramName, Object value) {
        String targetSynapsePropName = getTemplatePropertyMapping(templateName,paramName);
        synCtxt.setProperty(targetSynapsePropName,value);
    }

    /**
     * Enclose children of the soap body with a specific element
     *
     * @param envelope SOAPEnvelope which is to be enclosed
     * @param encloseElement enclosing element
     * @return modified SOAPEnvelope
     */
    public static SOAPEnvelope encloseWithElement (SOAPEnvelope envelope, OMElement encloseElement) {
        Iterator itr = envelope.getBody().getChildElements();
        Object o;
        while (itr.hasNext()) {
            o = itr.next();
            if (o != null && o instanceof OMElement) {
                encloseElement.addChild((OMElement) o);
            }
        }
        envelope.getBody().addChild(encloseElement);
        return envelope;
    }

    public static Object stringtoJSON(String str) throws JaxenException {
        SynapseJsonPath root = new SynapseJsonPath("$");
        HashMap<String, Object> result = root.getJsonElement(IOUtils.toInputStream(str));
        return result.get("evaluatedJsonElement");
    }
}