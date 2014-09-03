/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.http;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.util.internal.AppendableCharSequence;

import java.util.List;


/**
 * Decodes {@link ByteBuf}s into {@link HttpRequest}s and {@link HttpContent}s.
 *
 * <h3>Parameters that prevents excessive memory consumption</h3>
 * <table border="1">
 * <tr>
 * <th>Name</th><th>Meaning</th>
 * </tr>
 * <tr>
 * <td>{@code maxInitialLineLength}</td>
 * <td>The maximum length of the initial line (e.g. {@code "GET / HTTP/1.0"})
 *     If the length of the initial line exceeds this value, a
 *     {@link TooLongFrameException} will be raised.</td>
 * </tr>
 * <tr>
 * <td>{@code maxHeaderSize}</td>
 * <td>The maximum length of all headers.  If the sum of the length of each
 *     header exceeds this value, a {@link TooLongFrameException} will be raised.</td>
 * </tr>
 * <tr>
 * <td>{@code maxChunkSize}</td>
 * <td>The maximum length of the content or each chunk.  If the content length
 *     exceeds this value, the transfer encoding of the decoded request will be
 *     converted to 'chunked' and the content will be split into multiple
 *     {@link HttpContent}s.  If the transfer encoding of the HTTP request is
 *     'chunked' already, each chunk will be split into smaller chunks if the
 *     length of the chunk exceeds this value.  If you prefer not to handle
 *     {@link HttpContent}s in your handler, insert {@link HttpObjectAggregator}
 *     after this decoder in the {@link ChannelPipeline}.</td>
 * </tr>
 * </table>
 */
public class HttpRequestDecoder extends HttpObjectDecoder {

    /**
     * Creates a new instance with the default
     * {@code maxInitialLineLength (4096}}, {@code maxHeaderSize (8192)}, and
     * {@code maxChunkSize (8192)}.
     */
    public HttpRequestDecoder() {
    }

    /**
     * Creates a new instance with the specified parameters.
     */
    public HttpRequestDecoder(
            int maxInitialLineLength, int maxHeaderSize, int maxChunkSize) {
        super(maxInitialLineLength, maxHeaderSize, maxChunkSize, true);
    }

    public HttpRequestDecoder(
            int maxInitialLineLength, int maxHeaderSize, int maxChunkSize, boolean validateHeaders) {
        super(maxInitialLineLength, maxHeaderSize, maxChunkSize, true, validateHeaders);
    }


    //max length for option/delete method
    AppendableCharSequence method = new AppendableCharSequence(8);
    //http protocol spec HTTP/1.1
    AppendableCharSequence protocol = new AppendableCharSequence(10);
    //uri
    AppendableCharSequence uri = new AppendableCharSequence(100);
    
    
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) throws Exception {
        switch (state()) {
            case SKIP_CONTROL_CHARS: {
                try {
                    super.skipControlCharacters(buffer);
                    checkpoint(State.READ_INITIAL);
                } finally {
                    checkpoint();
                }
            }
            case READ_INITIAL: try {
                message = createMessage(buffer);

            } catch (Exception e) {
                out.add(invalidMessage(e));
                return;
            }
        }
        super.decode(ctx, buffer, out);
    }
    
    @Override
    protected HttpMessage createMessage(String[] initialLine) throws Exception {
        throw new IllegalStateException();
    }
    
    protected HttpMessage createMessage(ByteBuf requestBuffer) throws Exception {
        int size = 0;
        method.reset();
        char current;
        while (HttpConstants.SP != (current = (char) requestBuffer.getByte(size))) {
            if (size < super.maxInitialLineLength) {
                method.append(current);
            } else {
                checkpoint(State.SKIP_CONTROL_CHARS);
                return null;
            }
            size++;
        }
        uri.reset();
        size++; //Ignore whitespace
        while (HttpConstants.SP != (current = (char) requestBuffer.getByte(size))) {
            if (size < super.maxInitialLineLength) {
                uri.append(current);
            } else {
                checkpoint(State.SKIP_CONTROL_CHARS);
                return null;
            }
            size++;
        }
        protocol.reset();
        size++; //Ignore whitespace
        while (HttpConstants.LF != (current = (char) requestBuffer.getByte(size))) {
            if (size < super.maxInitialLineLength) {
                protocol.append(current);
            } else {
                checkpoint(State.SKIP_CONTROL_CHARS);
                return null;
            }
            size++;
        }
        //Optimize to use char sequence for uri
        return new DefaultHttpRequest(HttpVersion.valueOf(protocol.toString()),
                HttpMethod.valueOf(method.toString()), uri, validateHeaders);
    }

    static AppendableCharSequence bad_req = new AppendableCharSequence("/bad-request".length());
    static {
        bad_req.append("/bad-request".subSequence(0, "/bad-request".length()));
    }

    @Override
    protected HttpMessage createInvalidMessage() {
        return new DefaultHttpRequest(HttpVersion.HTTP_1_0, HttpMethod.GET, bad_req, validateHeaders);
    }

    @Override
    protected boolean isDecodingRequest() {
        return true;
    }
}
