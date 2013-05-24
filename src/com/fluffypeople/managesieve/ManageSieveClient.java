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
 * A client for the Manage Sieve protocol. 
 * Manage sieve (<a href="http://tools.ietf.org/html/rfc5804">rfc5804</a>)
 * is used to manage the sieve mail filtering scripts on a server. (Sieve is defined
 * in <a href="http://tools.ietf.org/html/rfc5228">rfc5228>/a>).
 * <p>
 * This class manages the client side of the connection. The basic pattern is 
 * connect, upgrade to TLS, authenticate, issue commands, logout, close connection.
 * <p>
 * Most commands take string arguments and return {@link SieveResponse} objects.
 * {@link #putscript} takes an {@link SieveScript} as an argument and stores
 * the result in that object.
 * 
 * @author "Osric Wilkinson" <osric@fluffypeople.com>
 */
public class ManageSieveClient {

    private static final Logger log = Logger.getLogger(ManageSieveClient.class);
    private static final Charset UTF8 = Charset.forName("UTF-8");
    
    private static final char DQUOTE = '"';
    private static final char LEFT_CURRLY_BRACE = '{';
    private static final char LEFT_BRACKET = '(';
    private static final char RIGHT_BRACKET = ')';
    private static final String CRLF = "\r\n";
    private static final char SP = ' ';

    private Socket socket = null;
    private SSLSocket secureSocket = null;
    private ServerCapabilities cap;
    private StreamTokenizer in;
    private InputStream byteStream;
    private PrintWriter out;
    private String hostname;

    /**
     * Public constructor.
     */
    public ManageSieveClient() {
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
     * @throws IOException if there are underlying IO issues
     * @throws ParseException if we can't parse the response from the server
     */
    public synchronized ManageSieveResponse connect(final String host, final int port) throws IOException, ParseException {
        hostname = host;
        socket = new Socket(InetAddress.getByName(hostname), port);

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
    public synchronized ManageSieveResponse startTLS() throws IOException, ParseException {
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
            ManageSieveResponse resp = parseResponse();
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
     * <pre>
     * <code>
     *
     * CallbackHandler cbh = new CallbackHandler() {
     *     public void handle(Callback[] clbcks) throws IOException,  UnsupportedCallbackException {
     *         for (Callback cb : clbcks) {
     *             if (cb instanceof NameCallback) {
     *                 NameCallback name = (NameCallback) cb;
     *                 name.setName("user");
     *             } else if (cb instanceof PasswordCallback) {
     *                 PasswordCallback passwd = (PasswordCallback) cb;
     *                 passwd.setPassword("secret".toCharArray());
     *             }
     *         }
     *     }
     * };
     * </code>
     * </pre>
     *
     * @param cbh CallbackHandler[] list of calbacks that will be called by the
     * SASL code
     * @return SieveResponse from the server, OK is aithenticated, NO means a
     * problem
     * @throws SaslException
     * @throws IOException
     * @throws ParseException
     */
    public synchronized ManageSieveResponse authenticate(final CallbackHandler cbh) throws SaslException, IOException, ParseException {

        SaslClient sc = Sasl.createSaslClient(cap.getSASLMethods(), null, "sieve", hostname, null, cbh);

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
        ManageSieveResponse resp = null;
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
     * "This command lists the scripts the user has on the server". The results
     * are stored into the @code{List<SieveScript>} passed in. Any
     * existing contents of this list will be lost. Up to one of the scripts listed
     * will be marked active.
     *
     * @param scripts @code{List<SieveScript>} non-null List of scripts. Will be
     * cleared if not zero length, even if there is a problem
     * @return SieveResponse OK - list was fetched, NO - there was a problem.
     * @throws IOException
     * @throws ParseException
     */
    public synchronized ManageSieveResponse listscripts(List<SieveScript> scripts) throws IOException, ParseException {
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
     * "The HAVESPACE command is used to query the server for available space".
     * Specify the name of a script, and the size (in bytes). The server will
     * check if creating a script with the given name and size will be within
     * the users quota. Note that an OK response doesn't guarantee that a
     * PUTSCRIPT command will work, since the server may be updated in another
     * thread in the meantime.
     *
     * @param name
     * @param size
     * @return
     * @throws IOException
     * @throws ParseException
     */
    public synchronized ManageSieveResponse havespace(final String name, final long size) throws IOException, ParseException {
        String escapedName = escapeString(name);
        String number = Long.toString(size, 10);
        sendCommand("HAVESPACE", escapedName, number);
        return parseResponse();
    }

    /**
     * "The PUTSCRIPT command is used by the client to submit a Sieve script to
     * the server". This will overwrite any existing script with the same name.
     * The active state of the named script is not changed (so replacing an
     * active script leaves that script active, otherwise the script will not be
     * active until SETACTIVE is used). <p> The server will check the script is
     * valid before storing (and overwriting if needed) the script, and will
     * return parse errors in the "Human readable" part of the response. <p>
     * Even if the script is valid the response may contain WARNINGS.
     *
     *
     * @param name String name of the script
     * @param body String body of the script
     * @return OK if the script is added, NO on error
     * @throws IOException
     * @throws ParseException
     */
    public synchronized ManageSieveResponse putscript(final String name, final String body) throws IOException, ParseException {
        String encodedName = encodeString(name);
        String encodedBody = encodeString(body);
        sendCommand("PUTSCRIPT", encodedName, encodedBody);
        return parseResponse();
    }

    /**
     * "This command gets the contents of the specified script". The name of the
     * script is taken from the script param, and the body is stored in the
     * object
     *
     * @param script SieveScript to fetch/update
     * @return OK or NO response.
     */
    public synchronized ManageSieveResponse getScript(final String name, String body) throws IOException, ParseException {
        String encodedName = encodeString(name);
        sendCommand("GETSCRIPT", encodedName);
        body = parseString();
        int token = in.nextToken();
        if (token != StreamTokenizer.TT_EOL) {
            throw new ParseException("Expecting EOL but got " + tokenToString(token) + " at line " + in.lineno());
        }
        log.debug("Got scirpt, parsing response");
        return parseResponse();
    }

    /**
     * "This command is used to delete a user's Sieve script".
     * @param name String name of the script to delete
     * @return OK if the script was deleted, NO otherwise
     * @throws IOException
     * @throws ParseException 
     */
    public synchronized ManageSieveResponse deletescript(final String name) throws IOException, ParseException {
        String encodedName = encodeString(name);
        sendCommand("DELETESCRIPT", encodedName);
        return parseResponse();
    }

    /**
     * "This command sets a script active". The active script is the one
     * used by the MDA to filter incomming mail. It is not an error to have no
     * scripts active, or to set the same script active twice.
     * <p>
     * Use the empty string ("") to set no scripts active.
     * 
     * @param name String name of the script to set active
     * @return
     * @throws IOException
     * @throws ParseException 
     */
    public synchronized ManageSieveResponse setactive(final String name) throws IOException, ParseException {
        String encodedName = encodeString(name);
        sendCommand("SETACTIVE", encodedName);
        return parseResponse();
    }

    public synchronized ManageSieveResponse logout() throws IOException, ParseException {
        sendCommand("LOGOUT");
        return parseResponse();
    }

    public synchronized ManageSieveResponse renamescript(final String oldName, final String newName) throws IOException, ParseException {
        String encodedOldName = encodeString(oldName);
        String encodedNewName = encodeString(newName);
        sendCommand("RENAMESCRIPT", encodedOldName, encodedNewName);
        return parseResponse();
    }

    public synchronized ManageSieveResponse checkscript(final String script) throws IOException, ParseException {
        String encodedScript = encodeString(script);
        sendCommand("CHECKSCRIPT", encodedScript);
        return parseResponse();
    }

    public synchronized ManageSieveResponse noop(final String tag) throws IOException, ParseException {
        if (tag != null) {
            String encodedTag = encodeString(tag);
            sendCommand("NOOP", encodedTag);
        } else {
            sendCommand("NOOP");
        }
        return parseResponse();
    }

    public synchronized ManageSieveResponse capability() throws IOException, ParseException {
        sendCommand("CAPABILITY");
        parseCapabilities();
        return parseResponse();
    }

    private ManageSieveResponse parseCapabilities() throws IOException, ParseException {
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
                        throw new ParseException("Expecing EOL got " + tokenToString(token) + " at " + in.lineno());
                    }
                    break;

                default:
                    throw new ParseException("Unexpected token " + token + " at " + in.lineno());
            }
        }
    }

    private ManageSieveResponse parseResponse() throws IOException, ParseException {
        ManageSieveResponse resp = new ManageSieveResponse();
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
                if (resp.getCode().hasParam()) {
                    resp.setParam(parseString());
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
            // "Literal" String - {<length>}CRLF<length bytes of string>
            token = in.nextToken();
            if (token != StreamTokenizer.TT_NUMBER) {
                throw new ParseException("Expecting NUMBER got " + tokenToString(token) + " at line " + in.lineno());
            }
            // Irritatingly, the tokenizer will parse a double here, even
            // if we only want an int. Sigh.
            int length = (int) in.nval;
            token = in.nextToken();
            if (token != '}') {
                throw new ParseException("Expecing } got " + tokenToString(token) + " at line " + in.lineno());
            }
            token = in.nextToken();
            if (token != StreamTokenizer.TT_EOL) {
                throw new ParseException("Expecting EOL got " + tokenToString(token) + " at line " + in.lineno());
            }
            // Drop out of the tokenizer to read the raw bytes...
            byte[] rawString = new byte[length];
            byteStream.read(rawString, 0, length);
            //.. and convert them to a UTF encoded string.
            String result = new String(rawString, UTF8);
            return result;
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
        setupTokenizer();
        out = new PrintWriter(new OutputStreamWriter(sock.getOutputStream()));
    }

    private void setupTokenizer() {
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