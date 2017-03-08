/*
 * Copyright 2014-2017 the original author or authors.
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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.restdocs.operation.Operation;
import org.springframework.restdocs.snippet.ModelCreationException;
import org.springframework.restdocs.snippet.SnippetException;
import org.springframework.restdocs.snippet.TemplatedSnippet;

/**
 * Abstract {@link TemplatedSnippet} subclass that provides a base for snippets that
 * document a RESTful resource's request or response structure.
 *
 * @author Andy Wilkinson
 */
abstract class AbstractStructureSnippet extends TemplatedSnippet {

	private final String type;

	protected AbstractStructureSnippet(String name, String type,
			Map<String, Object> attributes) {
		super(name + "-structure", attributes);
		this.type = type;
	}

	@Override
	protected Map<String, Object> createModel(Operation operation) {
		try {
			Structure value = new Structure(verifyContent(getContent(operation)));
			System.out.println(value);
			Map<String, Object> model = new HashMap<>();
			model.put("structure", value);
			return model;
		}
		catch (IOException ex) {
			throw new ModelCreationException(ex);
		}
	}

	private byte[] verifyContent(byte[] content) {
		if (content.length == 0) {
			throw new SnippetException("Cannot document " + this.type + " fields as the "
					+ this.type + " body is empty");
		}
		return content;
	}

	/**
	 * Returns the content of the request or response extracted form the given
	 * {@code operation}.
	 *
	 * @param operation The operation
	 * @return The content
	 * @throws IOException if the content cannot be extracted
	 */
	protected abstract byte[] getContent(Operation operation) throws IOException;

	static class Structure {

		private final Node root;

		private final Object content;

		private Structure(byte[] contentBytes)
				throws JsonParseException, JsonMappingException, IOException {
			this.content = new ObjectMapper().readValue(contentBytes, Object.class);
			this.root = createNode(null, null, this.content);
		}

		@SuppressWarnings("unchecked")
		private static Node createNode(String name, Node parent, Object value) {
			String parentPath = parent == null ? "" : parent.path;
			if (value instanceof List) {
				String path = parentPath + (name == null ? "[]" : name + "[]");
				return new ArrayNode(name, path, (List<Object>) value);
			}
			String path = name == null ? parentPath : parentPath + "['" + name + "']";
			if (value instanceof Map) {
				return new ObjectNode(name, path, (Map<String, Object>) value);
			}
			return new ScalarNode(name, path);
		}

		@Override
		public String toString() {
			StringWriter stringWriter = new StringWriter();
			final PrintWriter printer = new PrintWriter(stringWriter);
			this.root.visit(new NodeVisitor() {

				private int depth = 0;

				@Override
				public void visit(ArrayNode node) {
					printIndentedName(node);
					printer.println("[ ");
					this.depth++;
				}

				@Override
				public void endVisit(ArrayNode node) {
					this.depth--;
					printer.println(getIndent() + "]");
				}

				@Override
				public void visit(ObjectNode node) {
					printIndentedName(node);
					printer.println("{");
					this.depth++;
				}

				@Override
				public void endVisit(ObjectNode node) {
					this.depth--;
					printer.println(getIndent() + "}");
				}

				@Override
				public void visit(ScalarNode node) {
					printIndentedName(node);
					Object extracted = new JsonFieldProcessor().extract(
							JsonFieldPath.compile(node.getPath()),
							Structure.this.content);
					JsonFieldType fieldType = null;
					if (extracted instanceof Collection) {
						for (Object item : (Collection<?>) extracted) {
							JsonFieldType typeOfItem = determineFieldType(item);
							if (fieldType == null) {
								fieldType = typeOfItem;
							}
							else if (fieldType != typeOfItem) {
								fieldType = JsonFieldType.VARIES;
								break;
							}
						}
					}
					else {
						fieldType = determineFieldType(extracted);
					}
					printer.println(fieldType);
				}

				private JsonFieldType determineFieldType(Object fieldValue) {
					if (fieldValue == null) {
						return JsonFieldType.NULL;
					}
					if (fieldValue instanceof String) {
						return JsonFieldType.STRING;
					}
					if (fieldValue instanceof Boolean) {
						return JsonFieldType.BOOLEAN;
					}
					return JsonFieldType.NUMBER;
				}

				@Override
				public void endVisit(ScalarNode node) {

				}

				private String getIndent() {
					StringWriter writer = new StringWriter();
					for (int i = 0; i < this.depth; i++) {
						writer.append("    ");
					}
					return writer.toString();
				}

				private void printIndentedName(Node node) {
					printer.print(getIndent());
					if (node.getName() != null) {
						printer.print(node.getName() + ": ");
					}
				}

			});

			return stringWriter.toString();

		}

		private static abstract class Node {

			private final String name;

			private final String path;

			protected Node(String name, String path) {
				this.name = name;
				this.path = path;
			}

			public String getName() {
				return this.name;
			}

			public String getPath() {
				return this.path;
			}

			abstract void visit(NodeVisitor visitor);
		}

		private static final class ScalarNode extends Node {

			protected ScalarNode(String name, String path) {
				super(name, path);
			}

			@Override
			public boolean equals(Object o) {
				return o instanceof ScalarNode;
			}

			@Override
			public int hashCode() {
				return 31;
			}

			@Override
			void visit(NodeVisitor visitor) {
				visitor.visit(this);
			}

		}

		private static final class ArrayNode extends Node {

			private Set<Node> children = new LinkedHashSet<>();

			public ArrayNode(String name, String path, List<Object> value) {
				super(name, path);
				for (Object source : value) {
					this.children.add(Structure.createNode(null, this, source));
				}
			}

			@Override
			void visit(NodeVisitor visitor) {
				visitor.visit(this);
				for (Node child : this.children) {
					child.visit(visitor);
				}
				visitor.endVisit(this);
			}

		}

		private static final class ObjectNode extends Node {

			private Map<String, Node> children = new LinkedHashMap<>();

			public ObjectNode(String name, String path, Map<String, Object> value) {
				super(name, path);
				for (Entry<String, Object> entry : value.entrySet()) {
					this.children.put(entry.getKey(),
							Structure.createNode(entry.getKey(), this, entry.getValue()));
				}
			}

			@Override
			void visit(NodeVisitor visitor) {
				visitor.visit(this);
				for (Entry<String, Node> entry : this.children.entrySet()) {
					entry.getValue().visit(visitor);
				}
				visitor.endVisit(this);
			}

			@Override
			public int hashCode() {
				return this.children.keySet().hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				if (this == obj) {
					return true;
				}
				if (obj == null) {
					return false;
				}
				if (getClass() != obj.getClass()) {
					return false;
				}
				ObjectNode other = (ObjectNode) obj;
				if (!this.children.equals(other.children)) {
					return false;
				}
				return true;
			}

		}

		private interface NodeVisitor {

			void visit(ObjectNode node);

			void visit(ArrayNode node);

			void visit(ScalarNode node);

			void endVisit(ArrayNode node);

			void endVisit(ObjectNode node);

			void endVisit(ScalarNode node);

		}

	}

}
