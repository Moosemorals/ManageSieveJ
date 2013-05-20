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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StreamTokenizer;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;
import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;

/**
 * Manage the connection with a Manage sieve server.
 *
 * @author "Osric Wilkinson" <osric@fluffypeople.com>
 */
public class SieveServer {

    private static final Logger log = Logger.getLogger(SieveServer.class);
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private Socket socket = null;
    private SSLSocket secureSocket = null;
    private ServerCapabilities cap;
    private static final char DQUOTE = '"';
    private static final char LEFT_CURRLY_BRACE = '{';
    private static final char LEFT_BRACKET = '(';
    private static final char RIGHT_BRACKET = ')';
    private static final String CRLF = "\r\n";
    private static final char SP = ' ';
    private StreamTokenizer in;
    private InputStream byteStream;
    private PrintWriter out;

    /**
     *
     */
    public SieveServer() {
    }

    /**
     * Get the current known server capabilties. Will return null if called
     * before the server has been connected.
     *
     * @return
     */
    public ServerCapabilities getCapabilities() {
        return cap;
    }

    /**
     * Connect to remote server
     *
     * @return SieveResponse OK on connect, NO on connection problems
     * @throws IOException if there are underliying IO issues
     * @throws ParseException if we can't parse the response from the server
     */
    public SieveResponse connect() throws IOException, ParseException {
        socket = new Socket(InetAddress.getByName("localhost"), 4190);

        setupAfterConnect(socket);
        return parseCapabilities();
    }

    /**
     * Upgrade connection to TLS. Should be called before authenticating,
     * especialy if you are using the PLAIN scheme.
     *
     * @return SieveResponse OK on succesful upgrade, NO on error or if the
     * server doesn't support SSL
     * @throws IOException
     * @throws ParseException
     */
    public SieveResponse startTLS() throws IOException, ParseException {
        if (!socket.isConnected()) {
            SieveResponse response = new SieveResponse();
            response.setType(SieveResponse.Type.NO);
            response.setMessage("Can't upgrade to SSL: Socket not conencted");
            return response;
        } else if (cap == null || !cap.hasTLS()) {
            SieveResponse response = new SieveResponse();
            response.setType(SieveResponse.Type.NO);
            response.setMessage("Can't upgrade to SSL: Server doesn't support SSL");
            return response;
        }

        try {
            // Create a trust manager that does not validate certificate chains
            final TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(final X509Certificate[] chain, final String authType) {
                    }

                    @Override
                    public void checkServerTrusted(final X509Certificate[] chain, final String authType) {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }
                }
            };

            // Install the all-trusting trust manager
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            // Create an ssl socket factory with our all-trusting manager
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            sendCommand("STARTTLS");
            SieveResponse resp = parseResponse();
            if (resp.isOk()) {
                secureSocket = (SSLSocket) sslSocketFactory.createSocket(socket, socket.getInetAddress().getHostAddress(), socket.getPort(), true);
                setupAfterConnect(secureSocket);
                return parseCapabilities();

            } else {
                return resp;
            }
        } catch (KeyManagementException ex) {
            throw new IOException("Could not seutp SSL socket", ex);
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException("Could not setup SSL socket", ex);
        }
    }

    /**
     * Authenticate against the remote server using SASL.
     *
     * The CallbackHandler should be setup apropriatly, for example:
     *
     * <pre>
     * {@code
     * CallbackHandler cbh = new CallbackHandler() {
     *
     * @Override
     *     public void handle(Callback[] clbcks) throws IOException,  UnsupportedCallbackException {
     *         for (Callback cb : clbcks) {
     *             if (cb instanceof NameCallback) {
     *                 NameCallback name = (NameCallback) cb;
     *                 name.setName("user");
     *             } else if (cb instanceof PasswordCallback) {
     *                 PasswordCallback passwd = (PasswordCallback) cb;
     *                 passwd.setPassword("secret".toCharArray());
     *             }
     * }
     * }
     * }; }
     * </pre>
     * @param cbh CallbackHandler[] list of calbacks that will be called by the
     * SASL code
     * @return SieveResponse from the server, OK is aithenticated, NO means a
     * problem
     * @throws SaslException
     * @throws IOException
     * @throws ParseException
     */
    public SieveResponse authenticate(final CallbackHandler cbh) throws SaslException, IOException, ParseException {

        SaslClient sc = Sasl.createSaslClient(cap.getSASLMethods(), null, "sieve", "hathor.basement", null, cbh);

        String mechanism = escapeString(sc.getMechanismName());
        if (sc.hasInitialResponse()) {
            byte[] ir = sc.evaluateChallenge(new byte[0]);
            String ready = new String(Base64.encodeBase64(ir));
            ready = encodeString(ready.trim());
            sendCommand("AUTHENTICATE", mechanism, ready);
        } else {
            sendCommand("AUTHENTICATE", mechanism);
        }

        int token;
        SieveResponse resp = null;
        do {
            token = in.nextToken();
            if (token == DQUOTE) {
                // String - so more data for the auth sequence
                in.pushBack();
                String msg = parseString();
                byte[] response = sc.evaluateChallenge(msg.getBytes());
                sendLine(encodeString(new String(response)));
            } else if (token == StreamTokenizer.TT_WORD) {
                in.pushBack();
                resp = parseResponse();
                break;
            } else {
                throw new ParseException("Expecting DQUOTE/WORD, got " + tokenToString(token) + " at line " + in.lineno());
            }
        } while (!sc.isComplete());

        // Complete
        sc.dispose();
        return resp;
    }

    /**
     * Fetch the list of scripts and load them into scripts. The current
     * contents of scripts will be lost.
     * @param scripts LIst<SieveScript> non-null List of scripts. Will be cleared if not zero length, even if there is a problem
     * @return SieveResponse OK - list was fetched, NO - there was a problem.
     * @throws IOException
     * @throws ParseException
     */
    public SieveResponse listscripts(List<SieveScript> scripts) throws IOException, ParseException {
        if (!scripts.isEmpty()) {
            scripts.clear();
        }
        sendCommand("LISTSCRIPTS");
        while (true) {
            int token = in.nextToken();
            switch (token) {
                case DQUOTE:
                case LEFT_CURRLY_BRACE:
                    in.pushBack();

                    String scriptName = parseString();
                    boolean isActive = false;
                    token = in.nextToken();
                    if (token == StreamTokenizer.TT_WORD) {
                        if (in.sval.equals("ACTIVE")) {
                            // active script;
                            isActive = true;
                        } else {
                            throw new ParseException("Unexpected word " + in.sval + " at line " + in.lineno());
                        }
                        token = in.nextToken();
                    }

                    if (token == StreamTokenizer.TT_EOL) {
                        scripts.add(new SieveScript(scriptName, null, isActive));
                    } else {
                        throw new ParseException("Expected EOL, got  " + tokenToString(token) + " at line " + in.lineno());
                    }
                    break;
                case StreamTokenizer.TT_WORD:
                    in.pushBack();
                    return parseResponse();
                default:
                    throw new ParseException("Unexpected token " + tokenToString(token) + " at line " + in.lineno());
            }
        }
    }


    /**
     * Send a script to the server. The server will try to parse the script, and
     * will include any parsing errors in a "human readable" message in the NO
     * response.
     *
     * @param script String seive script to upload.
     * @return OK if the script is added, NO on error
     * @throws IOException
     * @throws ParseException
     */
    public SieveResponse putscript(final SieveScript script) throws IOException, ParseException {
        String encodedName = encodeString(script.getName());
        String encodedBody = encodeString(script.getBody());
        sendCommand("PUTSCRIPT", encodedName, encodedBody);
        return parseResponse();
    }
    
    /**
     * Get a script from the server. The name of the script is taken from the script param, and
     * the body is stored in the object
     * @param script SieveScript to fetch/update
     * @return OK or NO response.
     */
    public SieveResponse getScript(SieveScript script) throws IOException, ParseException {
        String encodedName = encodeString(script.getName());
        sendCommand("GETSCRIPT", encodedName);
        script.setBody(parseString());
        int token = in.nextToken();
        if (token != StreamTokenizer.TT_EOL) {
            throw new ParseException("Expecting EOL but got " + tokenToString(token) + " at line " + in.lineno());
        }
        log.debug("Got scirpt, parsing response");
        return parseResponse();
    }
    
    public SieveResponse deletescript(final SieveScript target) throws IOException, ParseException {
        String encodedName = encodeString(target.getName());
        sendCommand("DELETESCRIPT", encodedName);
        return parseResponse();
    }

    private SieveResponse parseCapabilities() throws IOException, ParseException {
        cap = new ServerCapabilities();

        while (true) {
            int token = in.nextToken();
            switch (token) {
                case StreamTokenizer.TT_WORD:
                    // Unquoted word - end of capabilites
                    in.pushBack();
                    return parseResponse();
                case DQUOTE:
                case LEFT_CURRLY_BRACE:
                    // Capabilities can be either literal or quoted
                    in.pushBack();
                    String word = parseString();
                    if (word.equalsIgnoreCase("IMPLEMENTATION")) {
                        cap.setImplementationName(parseString());
                    } else if (word.equalsIgnoreCase("SASL")) {
                        cap.setSASLMethods(parseString());
                    } else if (word.equalsIgnoreCase("SIEVE")) {
                        cap.setSieveExtensions(parseString());
                    } else if (word.equalsIgnoreCase("MAXREDIRECTS")) {
                        token = in.nextToken();
                        if (token == StreamTokenizer.TT_NUMBER) {
                            cap.setMaxRedirects((int) in.nval);
                        } else {
                            throw new ParseException("Expecting NUMBER got " + tokenToString(token) + " at " + in.lineno());
                        }
                    } else if (word.equalsIgnoreCase("NOTIFY")) {
                        cap.setNotify(parseString());
                    } else if (word.equalsIgnoreCase("STARTTLS")) {
                        cap.setHasTLS(true);
                    } else if (word.equalsIgnoreCase("LANGUAGE")) {
                        cap.setLanguage(parseString());
                    } else if (word.equalsIgnoreCase("VERSION")) {
                        cap.setVersion(parseString());
                    } else if (word.equalsIgnoreCase("OWNER")) {
                        cap.setOwner(parseString());
                    } else {
                        // Unknown capability, read until EOL
                        while (token != StreamTokenizer.TT_EOL) {
                            token = in.nextToken();
                        }
                        in.pushBack();
                    }
                    token = in.nextToken();
                    if (token != StreamTokenizer.TT_EOL) {
                        throw new ParseException("Expecing EOL got " + token + " at " + in.lineno());
                    }
                    break;

                default:
                    System.out.println("Generic char: " + Character.toChars(token)[0]);
                    break;
            }
        }
    }

    private SieveResponse parseResponse() throws IOException, ParseException {
        SieveResponse resp = new SieveResponse();
        int token = in.nextToken();
        if (token == StreamTokenizer.TT_WORD) {
            // Get the type (OK NO BYTE)
            resp.setType(in.sval);
            token = in.nextToken();
            // Check for reason code
            if (token == LEFT_BRACKET) {
                token = in.nextToken();
                if (token == StreamTokenizer.TT_WORD) {
                    resp.setCode(in.sval);
                } else {
                    throw new ParseException("Expecting LEFT_BRACKET got " + tokenToString(token) + " at line " + in.lineno());
                }
                token = in.nextToken();
                if (token != RIGHT_BRACKET) {
                    throw new ParseException("Expecting RIGHT_BRACKET got " + tokenToString(token) + " at line " + in.lineno());
                }
            } else {
                in.pushBack();
            }
            // Check for human readable message
            token = in.nextToken();
            if (token != StreamTokenizer.TT_EOL) {
                in.pushBack();
                resp.setMessage(parseString());
                token = in.nextToken();
            }

            // Done, end of line            
            if (token != StreamTokenizer.TT_EOL) {
                throw new ParseException("Expecting EOL got " + tokenToString(token) + " at line " + in.lineno());
            }

        } else {
            throw new ParseException("Expecting WORD got " + tokenToString(token) + " at line " + in.lineno());
        }
        return resp;
    }

    private String parseString() throws IOException, ParseException {
        int token = in.nextToken();
        if (token == DQUOTE) {
            return in.sval;
        } else if (token == '{') {
            token = in.nextToken();
            if (token != StreamTokenizer.TT_NUMBER) {
                throw new ParseException("Expecting NUMBER got " + tokenToString(token) + " at line " + in.lineno());
            }
            int length = (int) in.nval;
            token = in.nextToken();
            if (token != '}') {
                throw new ParseException("Expecing } got " + tokenToString(token) + " at line " + in.lineno());
            }
            token = in.nextToken();
            if (token != StreamTokenizer.TT_EOL) {
                throw new ParseException("Expecting EOL got " + tokenToString(token) + " at line " + in.lineno());
            }
            byte[] rawString = new byte[length];
            byteStream.read(rawString, 0, length);
            String result =  new String(rawString, UTF8);
            
            log.debug("Read escaped string: " + result);
            return result;

//            setTokenizerString();
//            for (int i = 0; i < length; i++) {
//                token = in.nextToken();
//                if (token == StreamTokenizer.TT_EOF) {
//                    throw new ParseException("Unexpected EOF reading string at line " + in.lineno());
//                }
//                result.append(tokenToString(token));
//            }
//            setTokenizerNormal();
            
        } else {
            throw new ParseException("Expecing DQUOTE or {, got " + tokenToString(token) + " at line " + in.lineno());
        }
    }

    private String escapeString(final String raw) {
        StringBuilder result = new StringBuilder();
        result.append(DQUOTE);
        result.append(raw);
        result.append(DQUOTE);
        return result.toString();
    }

    /**
     * Turn a string into a {'length'+}.... form
     *
     * @param raw String to convert
     * @return converted String
     */
    private String encodeString(final String raw) {
        StringBuilder result = new StringBuilder();

        result.append("{");
        result.append(Integer.toString(raw.getBytes(UTF8).length));
        result.append("}");
        result.append(CRLF);
        result.append(raw);

        return result.toString();
    }

    private void sendCommand(final String command, String... param) throws IOException {
        StringBuilder line = new StringBuilder();
        line.append(command);
        if (param != null) {
            for (int i = 0; i < param.length; i++) {
                line.append(SP);
                line.append(param[i]);
            }
        }
        sendLine(line.toString());
    }

    private void sendLine(final String line) throws IOException {
        out.print(line);
        out.print(CRLF);
        out.flush();

        if (out.checkError()) {
            throw new IOException("Unknown error writing to server");
        }
    }

    private void setupAfterConnect(Socket sock) throws IOException {
        byteStream = sock.getInputStream();
        in = new StreamTokenizer(new InputStreamReader(byteStream));
        setTokenizerNormal();
        out = new PrintWriter(new OutputStreamWriter(sock.getOutputStream()));
    }

    private void setTokenizerNormal() {
        in.resetSyntax();

        in.whitespaceChars(0x20, 0x20);
        in.whitespaceChars(0x0A, 0x0A);
        in.whitespaceChars(0x0D, 0x0D);

        in.wordChars(0x23, 0x27);
        in.wordChars(0x2A, 0x5B);
        in.wordChars(0x5D, 0x7A);
        in.wordChars(0x7C, 0x7C);
        in.wordChars(0x7E, 0x7E);

        in.quoteChar(DQUOTE);
        in.parseNumbers();
        in.eolIsSignificant(true);
    }

    private void setTokenizerString() {
        in.resetSyntax();
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