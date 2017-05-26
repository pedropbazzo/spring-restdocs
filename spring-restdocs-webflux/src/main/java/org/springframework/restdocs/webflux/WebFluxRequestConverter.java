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

package org.springframework.restdocs.webflux;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import reactor.core.publisher.Flux;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ReactiveHttpInputMessage;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.MultipartHttpMessageReader;
import org.springframework.http.codec.multipart.Part;
import org.springframework.http.codec.multipart.SynchronossPartHttpMessageReader;
import org.springframework.restdocs.operation.OperationRequest;
import org.springframework.restdocs.operation.OperationRequestFactory;
import org.springframework.restdocs.operation.OperationRequestPart;
import org.springframework.restdocs.operation.OperationRequestPartFactory;
import org.springframework.restdocs.operation.QueryStringParser;
import org.springframework.restdocs.operation.RequestConverter;
import org.springframework.test.web.reactive.server.ExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.ClassUtils;
import org.springframework.util.LinkedMultiValueMap;

/**
 * A {@link RequestConverter} for creating an {@link OperationRequest} derived from an
 * {@link ExchangeResult}.
 *
 * @author Andy Wilkinson
 */
class WebFluxRequestConverter implements RequestConverter<ExchangeResult> {

	private final QueryStringParser queryStringParser = new QueryStringParser();

	@Override
	public OperationRequest convert(ExchangeResult result) {
		return new OperationRequestFactory().create(result.getUrl(), result.getMethod(),
				result.getRequestBodyContent(), extractRequestHeaders(result),
				this.queryStringParser.parse(result.getUrl()),
				extractRequestParts(result));
	}

	private HttpHeaders extractRequestHeaders(ExchangeResult result) {
		HttpHeaders extracted = new HttpHeaders();
		extracted.putAll(result.getRequestHeaders());
		extracted.remove(WebTestClient.WEBTESTCLIENT_REQUEST_ID);
		return extracted;
	}

	private List<OperationRequestPart> extractRequestParts(ExchangeResult result) {
		if (!ClassUtils.isPresent(
				"org.springframework.http.codec.multipart.NioMultipartParserListener",
				getClass().getClassLoader())) {
			return Collections.emptyList();
		}
		return new MultipartHttpMessageReader(new SynchronossPartHttpMessageReader())
				.readMono(null, new ExchangeResultReactiveHttpInputMessage(result), null)
				.onErrorReturn(new LinkedMultiValueMap<>()).block().values().stream()
				.flatMap((parts) -> parts.stream().map(this::createOperationRequestPart))
				.collect(Collectors.toList());
	}

	private OperationRequestPart createOperationRequestPart(Part part) {
		ByteArrayOutputStream content = readPartBodyContent(part);
		return new OperationRequestPartFactory().create(part.name(),
				part instanceof FilePart ? ((FilePart) part).filename() : null,
				content.toByteArray(), part.headers());
	}

	private ByteArrayOutputStream readPartBodyContent(Part part) {
		ByteArrayOutputStream contentStream = new ByteArrayOutputStream();
		DataBufferUtils.write(part.content(), contentStream).blockFirst();
		return contentStream;
	}

	private final class ExchangeResultReactiveHttpInputMessage
			implements ReactiveHttpInputMessage {

		private final ExchangeResult result;

		private ExchangeResultReactiveHttpInputMessage(ExchangeResult result) {
			this.result = result;
		}

		@Override
		public HttpHeaders getHeaders() {
			return this.result.getRequestHeaders();
		}

		@Override
		public Flux<DataBuffer> getBody() {
			DefaultDataBuffer buffer = new DefaultDataBufferFactory().allocateBuffer();
			buffer.write(this.result.getRequestBodyContent());
			return Flux.fromArray(new DataBuffer[] { buffer });
		}

	}

}
