/*
 * The MIT License
 *
 * Copyright 2013 "Osric Wilkinson" <osric@fluffypeople.com>.
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

/**
 *
 * @author "Osric Wilkinson" <osric@fluffypeople.com>
 */
public class SieveResponse {

    public enum Type {

        OK, NO, BYE
    };

    public enum Code {

        AUTH_TOO_WEAK(false),
        ENCRYPT_NEEDED(false),
        SASL(true),
        REFERRAL(true),
        TRANSITION_NEEDED(false),
        TRYLATER(false),
        ACTIVE(false),
        NONEXISTENT(false),
        ALREADYEXITS(false),
        WARNINGS(false),
        TAG(true),
        extension(false);
        private final boolean hasParam;

        private Code(boolean hasParam) {
            this.hasParam = hasParam;
        }

        public boolean hasParam() {
            return hasParam;
        }
    }
    private Type type;
    private Code code;
    private String message;
    private String param;

    public SieveResponse() {
    }

    /**
     * Shorthand for (this.getType() == SieveResponse.Type.OK)
     * @return true if is an OK response, false otherwise
     */
    public boolean isOk() {
        return type == Type.OK;
    }
    /**
     * Shorthand for (this.getType() == SieveResponse.Type.NO)
     * @return true if is an OK response, false otherwise
     */
    
    public boolean isNo() {
        return type == Type.NO;
    }
    
        /**
     * Shorthand for (this.getType() == SieveResponse.Type.BYE)
     * @return true if is an OK response, false otherwise
     */

    public boolean isBye() {
        return type == Type.BYE;
    }
    
    
    public Type getType() {
        return type;
    }

    public Code getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public void setType(final String type) throws ParseException {
        try {
            this.type = Type.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ParseException("Invalid response type");
        }
    }

    public void setType(Type type) {
        this.type = type;
    }

    public void setCode(final String raw) throws ParseException {
        String tweaked = raw.replaceAll("-", "_");
        try {
            this.code = Code.valueOf(tweaked.toUpperCase());
        } catch (IllegalArgumentException ex) {
            this.code = Code.extension;
        }
    }
    
    public void setCode(Code code) {
        this.code = code;
    }

    public void setParam(final String param) {
        this.param = param;
    }
    
    public String getParam() {
        return param;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
}
