/*
 * Copyright 2014-2020 the original author or authors.
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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.restdocs.mustache.MustacheException;
import org.springframework.restdocs.templates.Template;
import org.springframework.restdocs.templates.TemplateRenderingException;

/**
 * An adapter that exposes a compiled <a href="https://mustache.github.io">Mustache</a>
 * template as a {@link Template}.
 *
 * @author Andy Wilkinson
 */
public class MustacheTemplate implements Template {

	private final org.springframework.restdocs.mustache.Template delegate;

	private final Map<String, Object> context;

	private final MustacheTemplateSource source;

	/**
	 * Creates a new {@code MustacheTemplate} that adapts the given {@code delegate}.
	 * @param delegate the delegate to adapt
	 */
	public MustacheTemplate(org.springframework.restdocs.mustache.Template delegate) {
		this(delegate, Collections.<String, Object>emptyMap());
	}

	/**
	 * Creates a new {@code MustacheTemplate} that adapts the given {@code delegate}.
	 * During rendering, the given {@code context} and the context passed into
	 * {@link #render(Map)} will be combined and then passed to the delegate when it is
	 * {@link org.springframework.restdocs.mustache.Template#execute executed}.
	 * @param delegate the delegate to adapt
	 * @param context the context
	 */
	public MustacheTemplate(org.springframework.restdocs.mustache.Template delegate, Map<String, Object> context) {
		this(delegate, null, context);
	}

	/**
	 * Creates a new {@code MustacheTemplate} that adapts the given {@code delegate}.
	 * During rendering, the given {@code context} and the context passed into
	 * {@link #render(Map)} will be combined and then passed to the delegate when it is
	 * {@link org.springframework.restdocs.mustache.Template#execute executed}.
	 * @param delegate the delegate to adapt
	 * @param context the context
	 * @param source the source of the template for diagnostic purposes
	 * @since 2.0.5
	 */
	public MustacheTemplate(org.springframework.restdocs.mustache.Template delegate, MustacheTemplateSource source,
			Map<String, Object> context) {
		this.delegate = delegate;
		this.source = source;
		this.context = context;
	}

	@Override
	public String render(Map<String, Object> context) {
		Map<String, Object> combinedContext = new HashMap<>(this.context);
		combinedContext.putAll(context);
		try {
			return this.delegate.execute(combinedContext);
		}
		catch (MustacheException.Context ex) {
			if (this.source != null) {
				StringBuilder message = new StringBuilder(
						String.format("Failed to render '%s'. No value for '%s' on line %d:%n%n", this.source.getName(),
								ex.key, ex.lineNo));
				List<String> lines = this.source.getLines();
				for (int i = 0; i < lines.size(); i++) {
					message.append(String.format("%3s. %s%n", i + 1, lines.get(i)));
				}
				throw new TemplateRenderingException(message.toString(), ex);
			}
			throw ex;
		}
	}

}
