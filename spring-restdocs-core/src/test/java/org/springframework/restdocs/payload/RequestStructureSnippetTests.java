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

import org.junit.Test;

import org.springframework.restdocs.AbstractSnippetTests;
import org.springframework.restdocs.templates.TemplateFormat;

/**
 * Tests for {@link RequestStructureSnippet}.
 *
 * @author Andy Wilkinson
 */
public class RequestStructureSnippetTests extends AbstractSnippetTests {

	public RequestStructureSnippetTests(String name, TemplateFormat templateFormat) {
		super(name, templateFormat);
	}

	@Test
	public void basicStuffWorks() throws IOException {
		new RequestStructureSnippet()
				.document(this.operationBuilder.request("http://localhost")
						.content(
								"{\"a\": {\"b\": 5, \"c\": [ { \"d\": 6 }, { \"d\" : true} ]}, \"e\": [ 1, 2, 3, 4 ], \"f\": [ true, false, 5 ] }")
				.build());
	}

}
