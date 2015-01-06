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

package org.apache.synapse.mediators.builtin;

import java.util.List;

import org.apache.axiom.om.OMException;
import org.apache.axiom.om.OMNode;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axis2.AxisFault;
import org.apache.synapse.Mediator;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseLog;
import org.apache.synapse.commons.json.JSONProviderUtil;
import org.apache.synapse.commons.json.JsonUtil;
import org.apache.synapse.config.xml.SynapsePath;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.mediators.AbstractMediator;
import org.apache.synapse.mediators.base.SequenceMediator;
import org.apache.synapse.mediators.eip.EIPUtils;
import org.apache.synapse.mediators.eip.Target;
import org.apache.synapse.util.MessageHelper;
import org.apache.synapse.util.xpath.SynapseJsonPath;
import org.apache.synapse.util.xpath.SynapseXPath;
import org.jaxen.JaxenException;

public class ForEachMediator extends AbstractMediator {

	/** The jsonpath or xpath that will list the elements to be splitted */
	private SynapsePath expression = null;

	/** The target for the newly splitted messages */
	private Target target = null;

	public boolean mediate(MessageContext synCtx) {
		SynapseLog synLog = getLog(synCtx);

		if (synLog.isTraceOrDebugEnabled()) {
			synLog.traceOrDebug("Start : Foreach mediator");
			// synLog.traceOrDebug("FE*=Message : " + synCtx.getEnvelope());
			// synLog.traceTrace("FE*=Message : " + synCtx.getEnvelope());
		}

		if (expression == null) {
			synLog.error("ForEach: expression is null");
			return false;
		} else {
			synLog.traceOrDebug("ForEach: expression = " +
			                    expression.toString());

			if (!validateSequence(synCtx)) {
				synLog.error("ForEach: Target cannot contain an endpoint OR Sequence is invalid/null :: cannot contain Call, Send or Callout mediators");
				return false;
			} else {

				if (expression.getPathType().equals(SynapsePath.JSON_PATH)) {

					if (synLog.isTraceOrDebugEnabled()) {
						synLog.traceOrDebug("Splitting with JsonPath : " +
						                    expression);

					}

					try {
						Object splitElements = expression.evaluate(synCtx);
						// ((SynapseJsonPath)expression).remove(synCtx);

						Object jsonPayload = EIPUtils.getRootJSONObject(synCtx);

						int msgNumber = 0;
						int msgCount = 0;
						if (splitElements != null &&
						    splitElements instanceof List) {
							List splitElementList = (List) splitElements;
							msgCount = splitElementList.size();

							if (splitElementList.get(0) instanceof List) {
								splitElementList =
								                   (List) splitElementList.get(0);
								msgCount = splitElementList.size();
							}

							if (synLog.isTraceOrDebugEnabled()) {
								synLog.traceOrDebug("Splitting with Json : " +
								                    expression +
								                    " resulted in " + msgCount +
								                    " elements");
							}

							for (Object o : splitElementList) {
								// ((SynapseJsonPath)expression).remove(jsonPayload,
								// splitElementList, o);
								if (synLog.isTraceOrDebugEnabled()) {
									synLog.traceOrDebug("Submitting " +
									                    msgNumber + " of " +
									                    msgCount +
									                    " messages for processing in sequentially, in a general loop");
								}
								MessageContext iteratedMsgCtx =
								                                getIteratedMessage(synCtx,
								                                                   o);

								target.mediate(iteratedMsgCtx);
								// synLog.traceOrDebug("FE*=" + msgNumber +
								// "iteratedMsgCtxEnv=" +
								// iteratedMsgCtx.getEnvelope());

								// need to replace the first and append the
								// remaining
								// for the case where $.list or $.list[*] for
								// all
								// other
								// cases replace works
								// if (msgNumber == 0) {
								((SynapseJsonPath) expression).replace(jsonPayload,
								                                       EIPUtils.getRootJSONObject(iteratedMsgCtx));
								// } else {
								// ((SynapseJsonPath)
								// expression).appendToParent(jsonPayload,
								// EIPUtils.getRootJSONObject(iteratedMsgCtx));
								// }

								// synLog.traceOrDebug("FE*=" + msgNumber +
								// "jsonPayload=" + jsonPayload);
								msgNumber++;
							}

							JsonUtil.newJsonPayload(((Axis2MessageContext) synCtx).getAxis2MessageContext(),
							                        JSONProviderUtil.objectToString(jsonPayload),
							                        true, true);

						} else {
							handleException("Json Expression doesn't match any elements",
							                synCtx);
						}

					} catch (JaxenException e) {
						handleException("Error evaluating split JsonPath expression : " +
						                        expression, e, synCtx);
					} catch (AxisFault e) {
						handleException("Error creating an iterated copy of the message",
						                e, synCtx);
					} catch (OMException e) {
						handleException("Error creating an iterated copy of the message",
						                e, synCtx);
					}
					return true;
				} else if (expression.getPathType().equals(SynapsePath.X_PATH)) {

					if (synLog.isTraceOrDebugEnabled()) {
						synLog.traceOrDebug("Splitting with Xpath : " +
						                    expression);

					}
					try {

						SOAPEnvelope envelope = synCtx.getEnvelope();
						// get the iteration elements and iterate through the
						// list,
						// this call will also detach all the iteration elements
						List<?> splitElements =
						                        EIPUtils.getDetachedMatchingElements(envelope,
						                                                             synCtx,
						                                                             (SynapseXPath) expression);

						int msgCount = splitElements.size();
						int msgNumber = 0;

						if (synLog.isTraceOrDebugEnabled()) {
							synLog.traceOrDebug("Splitting with XPath : " +
							                    expression + " resulted in " +
							                    msgCount + " elements");
						}

						// iterate through the list
						for (Object o : splitElements) {
							if (!(o instanceof OMNode)) {
								handleException("Error splitting message with XPath : " +
								                        expression +
								                        " - result not an OMNode",
								                synCtx);
							}

							if (synLog.isTraceOrDebugEnabled()) {
								synLog.traceOrDebug("Submitting " + msgNumber +
								                    " of " + msgCount +
								                    " messages for processing in sequentially, in a general loop");
							}

							MessageContext iteratedMsgCtx =
							                                getIteratedMessage(synCtx,
							                                                   envelope,
							                                                   (OMNode) o);

							target.mediate(iteratedMsgCtx);
							EIPUtils.includeEnvelope(envelope,
							                         iteratedMsgCtx.getEnvelope(),
							                         synCtx,
							                         (SynapseXPath) expression);
							synCtx.setEnvelope(envelope);
						}

					} catch (JaxenException e) {
						handleException("Error evaluating split XPath expression : " +
						                        expression, e, synCtx);
					} catch (AxisFault af) {
						handleException("Error creating an iterated copy of the message",
						                af, synCtx);
					}
					if (synLog.isTraceOrDebugEnabled()) {
						synLog.traceOrDebug("End : For Each mediator");
					}
					return true;
				} else {
					handleException("ForEach : Error evaluating  expression : " +
					                        expression, synCtx);
					return false;
				}
			}
		}
	}

	private boolean validateSequence(MessageContext synCtx) {

		SequenceMediator sequence = target.getSequence();
		boolean valid = true;
		if (sequence != null) {
			valid = validateSequenceMediatorList(sequence);
		} else {
			String sequenceRef = target.getSequenceRef();
			if (sequenceRef != null) {
				SequenceMediator refSequence =
				                               (SequenceMediator) synCtx.getSequence(sequenceRef);

				if (refSequence != null) {
					valid = validateSequenceMediatorList(refSequence);
				} else {
					valid = false;
				}
			} else if ((target.getEndpoint() != null) ||
			           (target.getEndpointRef() != null)) {
				valid = false;
			} else {
				valid = false;
			}
		}
		return valid;

	}

	private boolean validateSequenceMediatorList(SequenceMediator seqMediator) {
		boolean valid = true;
		List<Mediator> mediators = seqMediator.getList();
		for (Mediator m : mediators) {
			if (m instanceof CallMediator) {
				valid = false;
				break;
			} else if (m instanceof CalloutMediator) {
				valid = false;
				break;
			} else if (m instanceof SendMediator) {
				valid = false;
				break;
			}
		}
		return valid;
	}

	/**
	 * This method is for JSON messages
	 * 
	 * @param synCtx
	 * @param msgNumber
	 * @param msgCount
	 * @param node
	 * @return
	 * @throws AxisFault
	 * @throws JaxenException
	 */
	private MessageContext getIteratedMessage(MessageContext synCtx, Object node)
	                                                                             throws AxisFault,
	                                                                             JaxenException {
		// clone the message for the mediation in iteration
		MessageContext newCtx = MessageHelper.cloneMessageContext(synCtx);

		// Initially set the extracted object as root and send if payload is not
		// preserved
		Object rootObject = node;

		// write the new JSON message to the stream
		JsonUtil.newJsonPayload(((Axis2MessageContext) newCtx).getAxis2MessageContext(),
		                        JSONProviderUtil.objectToString(rootObject),
		                        true, true);
		return newCtx;
	}

	/**
	 * Create a new message context using the given original message context,
	 * the envelope
	 * and the split result element.
	 * 
	 * @param synCtx
	 *            - original message context
	 * @param envelope
	 *            - envelope to be used in the iteration
	 * @param o
	 *            - element which participates in the iteration replacement
	 * @return newCtx created by the iteration
	 * @throws AxisFault
	 *             if there is a message creation failure
	 * @throws JaxenException
	 *             if the expression evauation failure
	 */

	private MessageContext getIteratedMessage(MessageContext synCtx,
	                                          SOAPEnvelope envelope, OMNode o)
	                                                                          throws AxisFault,
	                                                                          JaxenException {

		MessageContext newCtx = MessageHelper.cloneMessageContext(synCtx);

		SOAPEnvelope newEnvelope = MessageHelper.cloneSOAPEnvelope(envelope);

		if (newEnvelope.getBody() != null) {

			if (newEnvelope.getBody().getFirstElement() != null) {
				newEnvelope.getBody().getFirstElement().detach();
			}
			newEnvelope.getBody().addChild(o);
		}

		newCtx.setEnvelope(newEnvelope);

		return newCtx;
	}

	public Target getTarget() {
		return target;
	}

	public void setTarget(Target target) {
		this.target = target;
	}

	public SynapsePath getExpression() {
		return expression;
	}

	public void setExpression(SynapsePath expression) {

		this.expression = expression;
	}

}
