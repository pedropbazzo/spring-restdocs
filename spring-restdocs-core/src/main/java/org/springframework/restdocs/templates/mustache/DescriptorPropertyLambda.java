/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.restdocs.templates.mustache;

import java.io.IOException;
import java.io.Writer;

import org.springframework.restdocs.mustache.Mustache.Lambda;
import org.springframework.restdocs.mustache.MustacheException;
import org.springframework.restdocs.mustache.Template.Fragment;

/**
 * @author awilkinson
 */
public class DescriptorPropertyLambda implements Lambda {

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * org.springframework.restdocs.mustache.Mustache.Lambda#execute(org.springframework.
	 * restdocs.mustache.Template.Fragment, java.io.Writer)
	 */
	@Override
	public void execute(Fragment fragment, Writer writer) throws IOException {
		try {
			writer.write(fragment.execute());
		}
		catch (MustacheException.Context ex) {
			Object key = ex.key;
			Object descriptor = fragment.context();
			System.out.println("No '" + key + "' found in descriptor '" + descriptor + "'");
		}
	}

}
