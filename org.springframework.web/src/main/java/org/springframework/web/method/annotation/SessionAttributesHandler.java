/*
 * Copyright 2002-2011 the original author or authors.
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

package org.springframework.web.method.annotation;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionAttributeStore;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.context.request.WebRequest;

/**
 * Manages controller-specific session attributes declared via 
 * {@link SessionAttributes @SessionAttributes}. Actual storage is 
 * performed via {@link SessionAttributeStore}.
 * 
 * <p>When a controller annotated with {@code @SessionAttributes} adds 
 * attributes to its model, those attributes are checked against names and 
 * types specified via {@code @SessionAttributes}. Matching model attributes 
 * are saved in the HTTP session and remain there until the controller calls 
 * {@link SessionStatus#setComplete()}.
 * 
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public class SessionAttributesHandler {

	private final Set<String> attributeNames = new HashSet<String>();

	private final Set<Class<?>> attributeTypes = new HashSet<Class<?>>();

	private final Set<String> resolvedAttributeNames = Collections.synchronizedSet(new HashSet<String>(4));

	private final SessionAttributeStore sessionAttributeStore;

	/**
	 * Creates a new instance for a controller type. Session attribute names/types
	 * are extracted from a type-level {@code @SessionAttributes} if found.
	 * @param handlerType the controller type
	 * @param sessionAttributeStore used for session access
	 */
	public SessionAttributesHandler(Class<?> handlerType, SessionAttributeStore sessionAttributeStore) {
		Assert.notNull(sessionAttributeStore, "SessionAttributeStore may not be null.");
		this.sessionAttributeStore = sessionAttributeStore;
		
		SessionAttributes annotation = AnnotationUtils.findAnnotation(handlerType, SessionAttributes.class);
		if (annotation != null) {
			this.attributeNames.addAll(Arrays.asList(annotation.value())); 
			this.attributeTypes.addAll(Arrays.<Class<?>>asList(annotation.types()));
		}		
	}

	/**
	 * Whether the controller represented by this instance has declared session 
	 * attribute names or types of interest via {@link SessionAttributes}. 
	 */
	public boolean hasSessionAttributes() {
		return ((this.attributeNames.size() > 0) || (this.attributeTypes.size() > 0)); 
	}
	
	/**
	 * Whether the attribute name and/or type match those specified in the
	 * controller's {@code @SessionAttributes} annotation. 
	 * 
	 * <p>Attributes successfully resolved through this method are "remembered"
	 * and used in {@link #retrieveAttributes(WebRequest)} and 
	 * {@link #cleanupAttributes(WebRequest)}. In other words, retrieval and 
	 * cleanup only affect attributes previously resolved through here.
	 * 
	 * @param attributeName the attribute name to check; must not be null
	 * @param attributeType the type for the attribute; or {@code null}
	 */
	public boolean isHandlerSessionAttribute(String attributeName, Class<?> attributeType) {
		Assert.notNull(attributeName, "Attribute name must not be null");
		if (this.attributeNames.contains(attributeName) || this.attributeTypes.contains(attributeType)) {
			this.resolvedAttributeNames.add(attributeName);
			return true;
		}
		else {
			return false;
		}
	}

	/**
	 * Stores a subset of the given attributes in the session. Attributes not 
	 * declared as session attributes via {@code @SessionAttributes} are ignored. 
	 * @param request the current request
	 * @param attributes candidate attributes for session storage
	 */
	public void storeAttributes(WebRequest request, Map<String, ?> attributes) {
		for (String name : attributes.keySet()) {
			Object value = attributes.get(name);
			Class<?> attrType = (value != null) ? value.getClass() : null;
			
			if (isHandlerSessionAttribute(name, attrType)) {
				this.sessionAttributeStore.storeAttribute(request, name, value);
			}
		}
	}
	
	/**
	 * Retrieve "known" attributes from the session -- i.e. attributes listed 
	 * in {@code @SessionAttributes} and previously stored in the in the model 
	 * at least once. 
	 * @param request the current request
	 * @return a map with handler session attributes; possibly empty.
	 */
	public Map<String, Object> retrieveAttributes(WebRequest request) {
		Map<String, Object> attributes = new HashMap<String, Object>();
		for (String name : this.resolvedAttributeNames) {
			Object value = this.sessionAttributeStore.retrieveAttribute(request, name);
			if (value != null) {
				attributes.put(name, value);
			}
		}
		return attributes;
	}

	/**
	 * Cleans "known" attributes from the session - i.e. attributes listed
	 * in {@code @SessionAttributes} and previously stored in the in the model 
	 * at least once.
	 * @param request the current request
	 */
	public void cleanupAttributes(WebRequest request) {
		for (String attributeName : this.resolvedAttributeNames) {
			this.sessionAttributeStore.cleanupAttribute(request, attributeName);
		}
	}

	/**
	 * A pass-through call to the underlying {@link SessionAttributeStore}.
	 * @param request the current request
	 * @param attributeName the name of the attribute of interest
	 * @return the attribute value or {@code null}
	 */
	Object retrieveAttribute(WebRequest request, String attributeName) {
		return this.sessionAttributeStore.retrieveAttribute(request, attributeName);
	}
	
}