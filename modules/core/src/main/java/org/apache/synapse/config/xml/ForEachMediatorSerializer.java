/*
 * Copyright (c) 2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 * 
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.synapse.config.xml;

import org.apache.axiom.om.OMElement;
import org.apache.synapse.Mediator;
import org.apache.synapse.mediators.builtin.ForEachMediator;
import org.apache.synapse.util.xpath.SynapseXPath;

/**
 * <foreach> </foreach>
 */
public class ForEachMediatorSerializer extends AbstractMediatorSerializer {

	public String getMediatorClassName() {
		return ForEachMediator.class.getName();
	}

	@Override
	protected OMElement serializeSpecificMediator(Mediator m) {
		if (!(m instanceof ForEachMediator)) {
			handleException("Unsupported mediator passed in for serialization : " +
			                m.getType());
		}

		OMElement forEachElem = fac.createOMElement("foreach", synNS);
		saveTracingState(forEachElem, m);

		ForEachMediator forEachMed = (ForEachMediator) m;

		if (forEachMed.getExpression() != null) {
			SynapseXPathSerializer.serializeXPath(forEachMed.getExpression(),
			                                    forEachElem, "expression");
		} else {
			handleException("Missing expression of the ForEach which is required.");
		}

		forEachElem.addChild(TargetSerializer.serializeTarget(forEachMed.getTarget()));

		return forEachElem;
	}
}
