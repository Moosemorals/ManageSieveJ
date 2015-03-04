/*
 * The MIT License
 *
 * Copyright 2013-2015 "Osric Wilkinson" <osric@fluffypeople.com>.
 * Copyright 2015 Linagora
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

import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Store the response from the Manage Sieve server. <p> Generally this will be
 * either OK (indicating success), NO (indicating failure) or BYE (indicating
 * the server is closing the connection). Some responses include a <code>Code</code>
 * giving more detail (which may have a <code>subCode()</code>.
 *
 * @author "Osric Wilkinson" &lt;osric@fluffypeople.com&gt;
 * @author Linagora
 */
public class ManageSieveResponse {

    private static final Logger log = LoggerFactory.getLogger(ManageSieveResponse.class);

    /**
     * Type of the response.
     */
    public enum Type {

        OK, NO, BYE
    };

    /**
     * Primary response code.
     */
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
        QUOTA(false),
        extension(false);
        private final boolean hasParam;

        private Code(boolean hasParam) {
            this.hasParam = hasParam;
        }

        public boolean hasParam() {
            return hasParam;
        }

        public static Code fromString(final String raw) {
            log.debug("Constructing code from string: {}", raw);
            String tweaked = raw.replaceAll("-", "_");
            log.debug("Tweaked version is {}", tweaked);
            try {
                return Code.valueOf(tweaked.toUpperCase());
            } catch (IllegalArgumentException ex) {
                return Code.extension;
            }
        }
    }
    private Type type;
    private Code code;
    private String[] subCodes;
    private String message;
    private String param;

    /**
     * Package only constructor. Users are not expected to make instances of
     * this class.
     */
    ManageSieveResponse() {
    }

    /**
     * Is this an OK response. Shorthand for (this.getType() ==
     * SieveResponse.Type.OK)
     *
     * @return true if is an OK response, false otherwise
     */
    public boolean isOk() {
        return type == Type.OK;
    }

    /**
     * Is this a NO response. Shorthand for (this.getType() ==
     * SieveResponse.Type.NO)
     *
     * @return true if is an OK response, false otherwise
     */
    public boolean isNo() {
        return type == Type.NO;
    }

    /**
     * Is this a BYE response. Shorthand for (this.getType() ==
     * SieveResponse.Type.BYE)
     *
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

    /**
     * Get the list of any sub-codes that makeup this response. May be null. If
     * this is not null the first element will be the string representation of
     * {@link #getCode()}.
     *
     * @return Array of String sub-codes. May be null.
     */
    public String[] getSubCodes() {
        return Arrays.copyOf(subCodes, subCodes.length);
    }

    /**
     * Return any "Human readable" message from the response.
     */
    public String getMessage() {
        return message;
    }

    /**
     * Parse a string to set the type of this response.
     *
     * @param type String potential type, should be one of "OK", "NO", "BYE".
     * @throws ParseException if the response type is not recognised.
     */
    void setType(final String type) throws ParseException {
        try {
            this.type = Type.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ParseException("Invalid response type: " + type); 
        }
    }

    /**
     * Parse a string to set the code of this response. Sets both {@link #code}
     * and {@link #subCodes}.
     *
     * @param raw
     */
    void setCode(final String raw) {
        log.debug("Raw code: {}", raw);
        subCodes = raw.split("/");
        this.code = Code.fromString(subCodes[0]);
    }

    /**
     * Set any parameters associated with the code
     *
     * @param param String to use
     */
    void setParam(final String param) {
        this.param = param;
    }

    public String getParam() {
        return param;
    }

    /**
     * Set the "Human readable" message. This message SHOULD be shown to the
     * user.
     */
    void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append(type.toString());
        if (code != null) {
            result.append(" (").append(code.toString()).append(")");
        }
        if (message != null) {
            result.append(" \"").append(message).append("\"");
        }

        return result.toString();
    }
}
