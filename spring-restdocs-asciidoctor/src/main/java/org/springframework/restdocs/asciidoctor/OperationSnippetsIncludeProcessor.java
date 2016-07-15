/*
 * Copyright 2014-2016 the original author or authors.
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

package org.springframework.restdocs.asciidoctor;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.asciidoctor.ast.DocumentRuby;
import org.asciidoctor.extension.IncludeProcessor;
import org.asciidoctor.extension.PreprocessorReader;

import org.springframework.util.FileCopyUtils;
import org.springframework.util.StringUtils;

/**
 * An {@link IncludeProcessor} that includes the generated snippets for a particular
 * operation.
 * <p>
 * Example usage:
 *
 * <pre>
 * include::srd:notes-list-example[snippets='response-fields,curl-request,http-response',level=4]
 * </pre> In this example, the snippets in the {@code notes-list-example} directory are
 *
 * eligible for inclusion. The snippets that are actually included in their order is
 * determined by the {@code snippets} attribute. In this case, three snippets will be
 * included:
 * <ol>
 * <li>{@code response-fields.adoc}</li>
 * <li>{@code curl-request}</li>
 * <li>{@code http-response}</li>
 * </ol>
 * Furthermore, the title for each snippet will have a level of 4.
 * <p>
 * Each snippet has a default title. The title can be customized using a document
 * attribute of the form <code>:srd-{name}-title:</code>. For example, to configure the
 * title of the CURL request snippet add the following to your {@code .adoc} file's
 * preamble:
 *
 * <pre>
 * :srd-curl-request-title: Example curl request
 * </pre>
 *
 * @author Andy Wilkinson
 */
public class OperationSnippetsIncludeProcessor extends IncludeProcessor {

	private static final String TARGET_PREFIX = "srd:";

	private static final Map<String, SnippetInclusion> SNIPPET_INCLUSIONS;

	static {
		Map<String, SnippetInclusion> inclusions = new HashMap<>();
		inclusions.put("curl-request",
				new SnippetInclusion("Curl request", "curl-request"));
		inclusions.put("http-request",
				new SnippetInclusion("HTTP request", "http-request"));
		inclusions.put("http-response",
				new SnippetInclusion("HTTP response", "http-response"));
		inclusions.put("httpie-request",
				new SnippetInclusion("HTTPie request", "httpie-request"));
		inclusions.put("links", new SnippetInclusion("Links", "links"));
		inclusions.put("request-fields",
				new SnippetInclusion("Request fields", "request-fields"));
		inclusions.put("response-fields",
				new SnippetInclusion("Response fields", "response-fields"));
		SNIPPET_INCLUSIONS = Collections.unmodifiableMap(inclusions);
	}

	@Override
	public boolean handles(String target) {
		return target.startsWith(TARGET_PREFIX);
	}

	@Override
	public void process(DocumentRuby document, PreprocessorReader reader, String target,
			Map<String, Object> attributes) {
		List<String> includedSnippets = getIncludedSnippets(attributes);
		if (includedSnippets.isEmpty()) {
			return;
		}
		File operationDir = getOperationDir(document,
				target.substring(TARGET_PREFIX.length()));
		StringWriter content = new StringWriter();
		PrintWriter printer = new PrintWriter(content);
		int level = getLevel(attributes);
		for (String includedSnippet : includedSnippets) {
			SnippetInclusion snippetInclusion = SNIPPET_INCLUSIONS.get(includedSnippet);
			if (snippetInclusion != null) {
				snippetInclusion.include(printer, operationDir, document, level);
			}
		}
		reader.push_include(content.toString(), target, target, 1, attributes);
	}

	private List<String> getIncludedSnippets(Map<String, Object> attributes) {
		Object snippetsObject = attributes.get("snippets");
		if (snippetsObject instanceof String) {
			return Arrays.asList(
					StringUtils.commaDelimitedListToStringArray((String) snippetsObject));
		}
		return Collections.emptyList();
	}

	private File getOperationDir(DocumentRuby document, String target) {
		File operationDir = new File(getSnippetsDir(document), target);
		if (!operationDir.isDirectory()) {
			throw new IllegalStateException(operationDir + " does not exist");
		}
		return operationDir;
	}

	private File getSnippetsDir(DocumentRuby document) {
		Object snippetsAttribute = document.getAttr("snippets");
		if (snippetsAttribute == null) {
			throw new IllegalStateException(
					"operation macro requires the snippets attribute");
		}
		return new File((String) snippetsAttribute);
	}

	private int getLevel(Map<String, Object> attributes) {
		Object levelObject = attributes.get("level");
		if (levelObject instanceof String) {
			return Integer.valueOf((String) levelObject);
		}
		return 1;
	}

	private static final class SnippetInclusion {

		private final String defaultTitle;

		private final String name;

		private SnippetInclusion(String title, String filename) {
			this.defaultTitle = title;
			this.name = filename;
		}

		private void include(PrintWriter printer, File operationDir,
				DocumentRuby document, int level) {
			String title = getTitle(document);
			printSectionAnchor(title, operationDir, printer);
			printTitle(title, level, printer);
			printSnippet(printer, operationDir);
		}

		private void printSectionAnchor(String title, File operationDir,
				PrintWriter printer) {
			printer.println("[[" + operationDir.getName() + "-"
					+ title.toLowerCase().replaceAll(" ", "-") + "]]");
		}

		private String getTitle(DocumentRuby document) {
			Object titleObject = document.getAttributes()
					.get("srd-" + this.name + "-title");
			if (titleObject instanceof String) {
				return (String) titleObject;
			}
			return this.defaultTitle;
		}

		private void printTitle(String title, int level, PrintWriter printer) {
			for (int i = 0; i < level; i++) {
				printer.print("=");
			}
			printer.println(" " + title);
			printer.println();
		}

		private void printSnippet(PrintWriter printer, File operationDir) {
			File snippetFile = new File(operationDir, this.name + ".adoc");
			try {
				printer.println(FileCopyUtils.copyToString(new FileReader(snippetFile)));
			}
			catch (IOException ex) {
				throw new IllegalStateException("Failed to include " + snippetFile, ex);
			}
		}

	}

}
