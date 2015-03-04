/*
 * The MIT License
 *
 * Copyright 2013-2015 "Osric Wilkinson" <osric@fluffypeople.com>.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.fluffypeople.managesieve;

import java.io.IOException;
import java.io.Reader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Debug class to try and track what has been read through a stream.
 * @author "Osric Wilkinson" &lt;osric@fluffypeople.com&gt;
 */
public class NoisyReader extends Reader {
    
    private static final Logger log = LoggerFactory.getLogger(NoisyReader.class);
    
    private final Reader base;
    
    public NoisyReader(final Reader base) {
        this.base = base;
    }
    
    StringBuilder buffer = new StringBuilder();

    @Override
    public int read(char[] chars, int offset, int length) throws IOException {
        int result =  base.read(chars, offset, length);
        buffer.append(chars, offset, length);
        log.debug("Current buffer: {}", buffer);
        if (buffer.indexOf("\n") > -1) {
            int end = buffer.indexOf("\n");
            log.debug("Read line: {}", buffer.substring(0, end));
            buffer.delete(0, end + 1);
        }
        
        return result;
    }

    @Override
    public void close() throws IOException {
        base.close();
    }
    
}
