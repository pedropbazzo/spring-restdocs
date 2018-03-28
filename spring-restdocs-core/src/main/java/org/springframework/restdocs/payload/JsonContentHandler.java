/*
 * Copyright 2014-2018 the original author or authors.
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

package org.springframework.restdocs.payload;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * A {@link ContentHandler} for JSON content.
 *
 * @author Andy Wilkinson
 */
class JsonContentHandler implements ContentHandler {

	private final JsonFieldProcessor fieldProcessor = new JsonFieldProcessor();

	private final JsonFieldTypeResolver fieldTypeResolver = new JsonFieldTypeResolver();

	private final ObjectMapper objectMapper = new ObjectMapper()
			.enable(SerializationFeature.INDENT_OUTPUT);

	private final byte[] rawContent;

	JsonContentHandler(byte[] content) {
		this.rawContent = content;
		readContent();
	}

	@Override
	public List<FieldDescriptor> findMissingFields(
			List<FieldDescriptor> fieldDescriptors) {
		List<FieldDescriptor> missingFields = new ArrayList<>();
		Object payload = readContent();
		for (FieldDescriptor fieldDescriptor : fieldDescriptors) {
			if (!fieldDescriptor.isOptional()
					&& !this.fieldProcessor.hasField(fieldDescriptor.getPath(), payload)
					&& !isNestedBeneathMissingOptionalField(fieldDescriptor,
							fieldDescriptors, payload)) {
				missingFields.add(fieldDescriptor);
			}
		}

		return missingFields;
	}

	private boolean isNestedBeneathMissingOptionalField(FieldDescriptor missing,
			List<FieldDescriptor> fieldDescriptors, Object payload) {
		List<FieldDescriptor> candidates = new ArrayList<>(fieldDescriptors);
		candidates.remove(missing);
		for (FieldDescriptor candidate : candidates) {
			if (candidate.isOptional()
					&& missing.getPath().startsWith(candidate.getPath())
					&& !this.fieldProcessor.hasField(candidate.getPath(), payload)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public String getUndocumentedContent(List<FieldDescriptor> fieldDescriptors) {
		Object content = readContent();
		for (FieldDescriptor fieldDescriptor : fieldDescriptors) {
			if (describesSubsection(fieldDescriptor)) {
				this.fieldProcessor.removeSubsection(fieldDescriptor.getPath(), content);
			}
			else {
				this.fieldProcessor.remove(fieldDescriptor.getPath(), content);
			}
		}
		if (!isEmpty(content)) {
			try {
				return this.objectMapper.writeValueAsString(content);
			}
			catch (JsonProcessingException ex) {
				throw new PayloadHandlingException(ex);
			}
		}
		return null;
	}

	private boolean describesSubsection(FieldDescriptor fieldDescriptor) {
		return fieldDescriptor instanceof SubsectionDescriptor;
	}

	private Object readContent() {
		try {
			return new ObjectMapper().readValue(this.rawContent, Object.class);
		}
		catch (IOException ex) {
			throw new PayloadHandlingException(ex);
		}
	}

	private boolean isEmpty(Object object) {
		if (object instanceof Map) {
			return ((Map<?, ?>) object).isEmpty();
		}
		return ((List<?>) object).isEmpty();
	}

	@Override
	public Object determineFieldType(FieldDescriptor fieldDescriptor) {
		if (fieldDescriptor.getType() == null) {
			return this.fieldTypeResolver.resolveFieldType(fieldDescriptor,
					readContent());
		}
		if (!(fieldDescriptor.getType() instanceof JsonFieldType)
				&& !(fieldDescriptor.getType() instanceof Collection)) {
			return fieldDescriptor.getType();
		}
		JsonFieldType actualFieldType = resolveFieldType(fieldDescriptor);
		if (actualFieldType == null) {
			return fieldDescriptor.getType();
		}
		if (fieldDescriptor.getType() instanceof Collection) {
			return determineFieldType(fieldDescriptor, actualFieldType,
					(Collection<?>) fieldDescriptor.getType());
		}
		return determineFieldType(fieldDescriptor, actualFieldType,
				(JsonFieldType) fieldDescriptor.getType());
	}

	private JsonFieldType resolveFieldType(FieldDescriptor fieldDescriptor) {
		try {
			return this.fieldTypeResolver.resolveFieldType(fieldDescriptor,
					readContent());
		}
		catch (FieldDoesNotExistException ex) {
			return null;
		}
	}

	private Object determineFieldType(FieldDescriptor fieldDescriptor,
			JsonFieldType actualFieldType, Collection<?> fieldTypes) {
		List<JsonFieldType> jsonFieldTypes = fieldTypes.stream()
				.filter(JsonFieldType.class::isInstance).map(JsonFieldType.class::cast)
				.collect(Collectors.toList());
		if (jsonFieldTypes.size() != fieldTypes.size()) {
			return fieldTypes;
		}
		if (hasMatchingFieldType(actualFieldType, fieldDescriptor.isOptional(),
				jsonFieldTypes)) {
			return jsonFieldTypes;
		}
		throw new FieldTypesDoNotMatchException(fieldDescriptor, actualFieldType);
	}

	private Object determineFieldType(FieldDescriptor fieldDescriptor,
			JsonFieldType actualFieldType, JsonFieldType descriptorFieldType) {
		if (hasMatchingFieldType(actualFieldType, fieldDescriptor.isOptional(),
				Arrays.asList(descriptorFieldType))) {
			return descriptorFieldType;
		}
		throw new FieldTypesDoNotMatchException(fieldDescriptor, actualFieldType);
	}

	private boolean hasMatchingFieldType(JsonFieldType actualFieldType, boolean optional,
			List<JsonFieldType> descriptorFieldTypes) {
		try {
			for (JsonFieldType descriptorFieldType : descriptorFieldTypes) {
				if (descriptorFieldType == JsonFieldType.VARIES
						|| descriptorFieldType == actualFieldType
						|| (optional && actualFieldType == JsonFieldType.NULL)) {
					return true;
				}
			}
			return false;
		}
		catch (FieldDoesNotExistException ex) {
			return true;
		}
	}

}
