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
package com.fluffypeople.managesieve.xml;

import com.fluffypeople.managesieve.ParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.StreamTokenizer;
import java.io.StringReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Convert a Sieve script to its XML representation.
 *
 * @author "Osric Wilkinson" &lt;osric@fluffypeople.com&gt;
 */
public class SieveToXML {

    private static final Logger log = LoggerFactory.getLogger(SieveToXML.class);
    private StreamTokenizer in;
    private static final String[] CONTROL_NAMES = {"if", "elsif", "else", "stop", "require"};

    public XML convert(final String script) throws IOException, ParseException {
        return convert(new StringReader(script));
    }

    public XML convert(final Reader script) throws ParseException, IOException {
        XML xml = new XML();
        in = new StreamTokenizer(script);
        setupTokenizer();

        xml.start("sieve", "xmlns", "urn:ietf:params:xml:ns:sieve");
        commands(xml);
        xml.end();

        return xml;
    }

    private void commands(final XML xml) throws ParseException, IOException {
        log.debug("commands start");
        while (command(xml)) {
        }
        log.debug("commands end");
    }

    private boolean command(final XML xml) throws IOException, ParseException {
        log.debug("command");
        int token = in.nextToken();
        // First token should be an identifer
        if (token == StreamTokenizer.TT_WORD) {
            String name = in.sval;
            // Stricly, identifiers shouldn't start with a number
            // TODO: Check that

            String tag = nameIsControl(name) ? "control" : "action";
            xml.start(tag, "name", name);

            arguments(xml);

            token = in.nextToken();
            if (token == '{') {
                in.pushBack();
                block(xml);
            } else if (token == ';') {
                // end of command
            } else {
                raiseError("{ or ;", token, in.lineno());
            }
            xml.end();
            return true;

        } else {
            in.pushBack();
            return false;
        }
    }

    private void arguments(XML xml) throws ParseException, IOException {
        log.debug("arguments");
        while (argument(xml)) {
        }
        int token = in.nextToken();
        in.pushBack();
        if (token == '(') {
            test_list(xml);
        } else if (token == StreamTokenizer.TT_WORD) {
            test(xml);
        }
    }

    private boolean argument(XML xml) throws IOException, ParseException {
        log.debug("argument");
        int token = in.nextToken();
        in.pushBack();
        if (token == '[') {
            string_list(xml);
            return true;
        } else if (token == '"') {
            string(xml);
            return true;
        } else if (token == StreamTokenizer.TT_WORD && in.sval.equals("text")) {
            in.pushBack();
            string(xml);
            return true;
        } else if (token == StreamTokenizer.TT_NUMBER) {
            number(xml);
            return true;
        } else if (token == ':') {
            tag(xml);
            return true;
        } else {
            return false;
        }
    }

    private void string(XML xml) throws ParseException, IOException {
        log.debug("string");
        int token = in.nextToken();
        if (token == '"') {
            xml.add("str", in.sval);
        } else if (token == StreamTokenizer.TT_WORD) {
            if (in.sval.equals("text")) {
                token = in.nextToken();
                if (token == ':') {
                    // multi line string. Set tokenizer to ignore everything
                    // but line endings and # comments. 
                    in.resetSyntax();
                    in.ordinaryChars(0, 255);
                    in.commentChar('#');
                    in.whitespaceChars('\r', '\r');
                    in.whitespaceChars('\n', '\n');
                    in.eolIsSignificant(true);

                    // Read to end of line we're on
                    token = in.nextToken();
                    if (token != StreamTokenizer.TT_EOL) {
                        raiseError("EOL", token, in.lineno());
                    }

                    // OK< start of multiline string. Comments are no longer
                    // significant
                    in.ordinaryChar('#');

                    StringBuilder rawString = new StringBuilder();

                    while (true) {
                        StringBuilder line = new StringBuilder();
                        do {
                            token = in.nextToken();

                            if (token == StreamTokenizer.TT_WORD) {
                                // Unicode character
                                line.append(in.sval);
                            } else if (token == '\r' || token == '\n') {
                                // skip it
                            } else {
                                try {
                                    line.append(Character.toChars(token));
                                } catch (java.lang.IllegalArgumentException ex) {
                                    log.error("{} is not a valid char ",token);
                                    throw ex;
                                }
                            }
                        } while (token != StreamTokenizer.TT_EOL);
                        System.out.println("line: " + line.toString());

                        if (line.length() == 1 && line.codePointAt(0) == '.') {
                            // Found last line
                            break;
                        } else if (line.length() > 1 && line.codePointAt(0) == '.' && line.codePointAt(1) == '.') {
                            // Dot has been doubled, so delete the extra
                            line.deleteCharAt(0);
                        }
                        rawString.append(line).append("\r\n");
                    }

                    xml.add("str", rawString.toString());
                    setupTokenizer();
                } else {
                    raiseError(":", token, in.lineno());
                }
            } else {
                raiseError("'text'", token, in.lineno());
            }
        } else {
            raiseError("\"", token, in.lineno());
        }
    }

    private void string_list(XML xml) throws ParseException, IOException {
        log.debug("string_list");
        int token = in.nextToken();
        if (token == '[') {
            xml.start("list");
            do {
                string(xml);
                token = in.nextToken();
            } while (token == ',');
            if (token != ']') {
                raiseError("]", token, in.lineno());
            }
            xml.end();
        } else {
            raiseError("[", token, in.lineno());
        }
    }

    private void tag(XML xml) throws ParseException, IOException {
        log.debug("tag");
        int token = in.nextToken();
        if (token == ':') {
            token = in.nextToken();
            if (token == StreamTokenizer.TT_WORD) {
                xml.add("tag", in.sval);
            } else {
                raiseError("WORD", token, in.lineno());
            }
        } else {
            raiseError(":", token, in.lineno());
        }
    }

    private void number(XML xml) throws ParseException, IOException {
        log.debug("number");
        int token = in.nextToken();
        if (token == StreamTokenizer.TT_NUMBER) {
            Long raw = (long) in.nval;

            token = in.nextToken();
            if (token == StreamTokenizer.TT_WORD) {
                String mult = in.sval;
                if (mult.equalsIgnoreCase("K")) {
                    raw *= 1024;
                } else if (mult.equalsIgnoreCase("M")) {
                    raw *= 1024 * 1024;
                } else if (mult.equalsIgnoreCase("G")) {
                    raw *= 1024 * 1024 * 1024;
                }
            } else {
                in.pushBack();
            }

            xml.add("num", Long.toString(raw, 10));
        } else {
            raiseError("NUM", token, in.lineno());
        }
    }

    private void block(XML xml) throws IOException, ParseException {
        log.debug("block");
        int token = in.nextToken();
        if (token == '{') {
            commands(xml);
            token = in.nextToken();
            if (token != '}') {
                raiseError("}", token, in.lineno());
            }
        } else {
            raiseError("{", token, in.lineno());
        }
        log.debug("block end");
    }

    private void test_list(XML xml) throws IOException, ParseException {
        log.debug("test_list");
        int token = in.nextToken();
        if (token == '(') {
            do {
                test(xml);
                token = in.nextToken();
            } while (token == ',');
            if (token != ')') {
                raiseError(")", token, in.lineno());
            }
        } else {
            raiseError("(", token, in.lineno());
        }
    }

    private void test(XML xml) throws ParseException, IOException {
        log.debug("test");
        int token = in.nextToken();
        if (token == StreamTokenizer.TT_WORD) {
            xml.start("test", "name", in.sval);
            arguments(xml);
            xml.end();
        }
    }

    private void raiseError(final String expecting, final int token, final int line) throws ParseException {
        StringBuilder message = new StringBuilder();
        message.append("Expecting ");
        message.append(expecting);
        message.append(" but got ");
        message.append(tokenToString(token));
        message.append(" at line ");
        message.append(Integer.toString(line, 10));
        throw new ParseException(message.toString());
    }

    private void setupTokenizer() {
        in.resetSyntax();

        in.whitespaceChars(0x0D, 0x0D); // CR
        in.whitespaceChars(0x0A, 0x0A); // LF
        in.whitespaceChars(0x09, 0x09); // HTAB
        in.whitespaceChars(0x20, 0x20); // SP

        in.wordChars(0x41, 0x5A); // A-Z
        in.wordChars(0x61, 0x7A); // a-z
        in.wordChars(0x30, 0x39); // 0-9
        in.wordChars(0x5F, 0x5F); // _

        in.quoteChar(0x22); // "
        in.commentChar('#');

        in.slashStarComments(true);
        in.eolIsSignificant(false);
        in.parseNumbers();
    }

    private boolean nameIsControl(final String name) {
        for (String c : CONTROL_NAMES) {
            if (c.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static String tokenToString(final int c) {
        if (c > 0) {
            return new String(Character.toChars(c));
        } else {
            switch (c) {
                case StreamTokenizer.TT_EOF:
                    return "EOF";
                case StreamTokenizer.TT_NUMBER:
                    return "NUMBER";
                case StreamTokenizer.TT_EOL:
                    return "EOL";
                case StreamTokenizer.TT_WORD:
                    return "WORD";
                default:
                    return "UNKNOWN";
            }
        }
    }
}
