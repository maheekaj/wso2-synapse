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

import java.util.Properties;

import org.apache.axiom.om.OMElement;
import org.apache.synapse.Mediator;
import org.apache.synapse.MessageContext;
import org.apache.synapse.commons.json.JsonUtil;
import org.apache.synapse.config.xml.IterateMediatorFactory;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.util.xpath.SynapseJsonPath;

/**
 *
 */
public class IterateMediatorJSONTest extends AbstractSplitMediatorTestCase {

	protected void setUp() throws Exception {
		super.setUp();
		String jsonPayload =
		                     "{\"getquote\" : [{\"symbol\":\"WSO2\"},{\"symbol\":\"WSO2\"}]}";
		JsonUtil.newJsonPayload(((Axis2MessageContext) testCtx).getAxis2MessageContext(),
		                        jsonPayload, true, true);
		fac = new IterateMediatorFactory();
	}

    protected void tearDown() throws Exception {
        super.tearDown();
        fac = null;
    }

    public void testIterationScenarioOne() throws Exception {
        Mediator iterate = fac.createMediator(createOMElement("<iterate " +
            "expression=\"json-eval(getquote[*])\" xmlns=\"http://ws.apache.org/ns/synapse\">" +
            "<target soapAction=\"urn:iterate\" sequence=\"seqRef\"/></iterate>"), new Properties());
        helperMediator.clearMediatedContexts();
        iterate.mediate(testCtx);
        while(helperMediator.getMediatedContext(1) == null) {
            Thread.sleep(100);
        }
        MessageContext mediatedCtx = helperMediator.getMediatedContext(0);
        assertEquals(mediatedCtx.getSoapAction(), "urn:iterate");
        OMElement formerBody = mediatedCtx.getEnvelope().getBody().getFirstElement();
        mediatedCtx = helperMediator.getMediatedContext(1);
        assertEquals(mediatedCtx.getSoapAction(), "urn:iterate");
        
        SynapseJsonPath jsonPath=new SynapseJsonPath("$");
        String part = jsonPath.stringValueOf(mediatedCtx);
        if (formerBody == null) {
            assertEquals("{\"symbol\":\"WSO2\"}", part);
        }
    }

    public void testIterationWithPreservePayload() throws Exception {
        Mediator iterate = fac.createMediator(createOMElement("<iterate " +
            "expression=\"json-eval(getquote[*])\" preservePayload=\"true\" attachPath=\"json-eval(getquote)\" " +
            "xmlns=\"http://ws.apache.org/ns/synapse\"><target soapAction=\"urn:iterate\" " +
            "sequence=\"seqRef\"/></iterate>"), new Properties());
        iterate.mediate(testCtx);
        while(helperMediator.getMediatedContext(1) == null) {
            Thread.sleep(100);
        }
        MessageContext mediatedCtx = helperMediator.getMediatedContext(0);
        assertEquals(mediatedCtx.getSoapAction(), "urn:iterate");
        OMElement formerBody = mediatedCtx.getEnvelope().getBody().getFirstElement();
        mediatedCtx = helperMediator.getMediatedContext(1);
        assertEquals(mediatedCtx.getSoapAction(), "urn:iterate");
        
        SynapseJsonPath jsonPath=new SynapseJsonPath("$");
        String part = jsonPath.stringValueOf(mediatedCtx);
        if (formerBody == null) {
        	assertEquals("{\"getquote\":{\"symbol\":\"WSO2\"}}", part);
        }
    }
}
