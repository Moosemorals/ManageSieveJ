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

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.security.cert.Certificate;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.x500.X500Principal;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A client for the Manage Sieve protocol. Manage sieve (<a
 * href="http://tools.ietf.org/html/rfc5804">rfc5804</a>) is used to manage the
 * sieve mail filtering scripts on a server. (Sieve is defined in <a
 * href="http://tools.ietf.org/html/rfc5228">rfc5228</a>).
 * <p>
 * This class manages the client side of the connection. The basic pattern is
 * connect, upgrade to TLS, authenticate, issue commands, logout, close
 * connection.
 * <p>
 * Most commands take string arguments and return {@link ManageSieveResponse}
 * objects. {@link #putscript} takes an {@link SieveScript} as an argument and
 * stores the result in that object.
 *
 * @author "Osric Wilkinson" &lt;osric@fluffypeople.com&gt;
 * @author Linagora
 */
public class ManageSieveClient {

    private static final Logger log = LoggerFactory.getLogger(ManageSieveClient.class);
    private static final String CHARSET_PROPERTY = "sieve.charset";
    private static final String CHARSET_DEFAULT = "UTF-8";
    private static final char DQUOTE = '"';
    private static final char LEFT_CURRLY_BRACE = '{';
    private static final char LEFT_BRACKET = '(';
    private static final char RIGHT_BRACKET = ')';
    private static final String CRLF = "\r\n";
    private static final char SP = ' ';
    private final static Pattern ESCAPE_RE = Pattern.compile("([\"\\\\])");
    private final static int DQUOTE_LENGTH = 1;
    private final static int MAX_ESCAPED_STRING_LENGTH = 1024;
    private Socket socket = null;
    private ServerCapabilities cap;
    private StreamTokenizer in;
    private PrintWriter out;
    private String hostname;
    private int socketTimeout = 0; // Default socket timeout is zero, or don't time out.
	private final Charset charset;
	
    /**
     * Public constructor.
     */
    public ManageSieveClient() {
        Charset charsetTemp;
        final String charsetName = System.getProperty(CHARSET_PROPERTY, CHARSET_DEFAULT);
        try {
            charsetTemp = Charset.forName(charsetName);
            log.info("Using charset: {}", charsetName);
        } catch (IllegalCharsetNameException e) {
            charsetTemp = Charset.forName(CHARSET_DEFAULT);
            log.warn("Charset {} not available, using {} instead!", charsetName, CHARSET_DEFAULT);
        } catch (IllegalArgumentException e) {
            charsetTemp = Charset.forName(CHARSET_DEFAULT);
            log.warn("Invalid charset, using {} instead!", CHARSET_DEFAULT);
        }
        charset = charsetTemp;
    }

    /**
     * Get the current known server capabilities. Will return null if called
     * before the server has been connected.
     */
    public ServerCapabilities getCapabilities() {
        return cap;
    }

    /**
     * <p> Returns setting for SO_TIMEOUT. 0 returns implies that the option is
     * disabled (i.e., timeout of infinity).</p>
     * <p>
     * If the socket isn't connected, return the cached value that will be set
     * once the socket does connect.</p>
     *
     * @return the setting for SO_TIMEOUT
     * @throws SocketException - if there is an error in the underlying
     *                         protocol, such as a TCP error.
     * @see java.net.Socket#getSoTimeout()
     */
    public int getSocketTimeout() throws SocketException {
        return socket != null ? socket.getSoTimeout() : socketTimeout;
    }

    /**
     * <p>Set SO_TIMEOUT. Updates a connected socket (and is stored for use when a
     * socket connects).</p>
     * <p>
     * From <code>Socket.setSoTimeout</code>: "Enable/disable SO_TIMEOUT with
     * the specified timeout, in milliseconds. With this option set to a
     * non-zero timeout, a read() call on the InputStream associated with this
     * Socket will block for only this amount of time. If the timeout expires, a
     * java.net.SocketTimeoutException is raised, though the Socket is still
     * valid. The option must be enabled prior to entering the blocking
     * operation to have effect. The timeout must be &gt; 0. A timeout of zero is
     * interpreted as an infinite timeout."</p>
     *
     * @param timeout the specified timeout, in milliseconds.
     * @throws SocketException if there is an error in the underlying protocol,
     *                         such as a TCP error.
     * @see java.net.Socket#setSoTimeout(int)
     */
    public void setSocketTimeout(int timeout) throws SocketException {
        this.socketTimeout = timeout;
        if (socket != null) {
            socket.setSoTimeout(timeout);
        }
    }

    /**
     * Connect to remote server
     *
     * @return ManageSieveResponse OK on connect, NO on connection problems
     * @throws IOException    if there are underlying IO issues
     * @throws ParseException if we can't parse the response from the server
     */
    public synchronized ManageSieveResponse connect(final String host, final int port) throws IOException, ParseException {
        hostname = host;
        socket = new Socket(InetAddress.getByName(hostname), port);

        setupAfterConnect(socket);
        return parseCapabilities();
    }

    /**
     * Returns true if the underlying socket is connected.
     */
    public synchronized boolean isConnected() {
        return socket != null && socket.isConnected();
    }

    /**
     * Upgrade connection to TLS. Should be called before authenticating,
     * especially if you are using the PLAIN scheme.
     *
     * @return ManageSieveResponse OK on successful upgrade, NO on error or if
     * the server doesn't support SSL
     * @throws IOException
     * @throws ParseException
     */
    public synchronized ManageSieveResponse starttls() throws IOException, ParseException {
        return starttls((SSLSocketFactory) SSLSocketFactory.getDefault(), true);
    }

    /**
     * Upgrade connection to TLS. Should be called before authenticating,
     * especially if you are using the PLAIN scheme.
     *
     * @param sslSocketFactory
     * @return ManageSieveResponse OK on successful upgrade, NO on error or if
     * the server doesn't support SSL
     * @throws IOException
     * @throws ParseException
     */
    public synchronized ManageSieveResponse starttls(final SSLSocketFactory sslSocketFactory, final boolean rfcCheck) throws IOException, ParseException {
        sendCommand("STARTTLS");
        ManageSieveResponse resp = parseResponse();
        if (resp.isOk()) {
            final SSLSocket secureSocket = (SSLSocket) sslSocketFactory.createSocket(socket, socket.getInetAddress().getHostAddress(), socket.getPort(), true);
            if (rfcCheck) {
                // The manage sieve rfc says we should check that the name in the certificate
                // matches the hostname that we want. RFC: http://www.ietf.org/rfc/rfc5804.txt
                Certificate[] peerCertificates = secureSocket.getSession().getPeerCertificates();
                boolean certificateMatchesHostname = hasHostnameMatchingCertificate(peerCertificates);
                if (!certificateMatchesHostname) {
                    throw new IOException("Secure connect failed: non of the provided certificates matches the hostname " + hostname);
                }
            }
            setupAfterConnect(secureSocket);
            return parseCapabilities();

        } else {
            return resp;
        }
    }

    /**
     * Checks whether any of the provided certificates matches the hostname that
     * we use to connect to.
     *
     * @param certificates
     * @return true if any of the provided certificates has a matching Common Name
     */
    private boolean hasHostnameMatchingCertificate(Certificate[] certificates) {
        for (Certificate certificate : certificates) {
            if (certificate instanceof X509Certificate) {
                X509Certificate x509Certificate = (X509Certificate) certificate;

                // Check matching subjectAlternativeName
                Collection<List<?>> subjectAlternativeNames = null;
                try {
                    subjectAlternativeNames = x509Certificate.getSubjectAlternativeNames();
                } catch (CertificateParsingException e) {
                    log.warn("Could not check certificate's subjectAlternativeNames", e);
                }

                if (subjectAlternativeNames != null) {
                    for (List<?> subjectAlternativeName : subjectAlternativeNames) {
                        log.debug("Checking subjectAlternativeName '{}' against hostname", subjectAlternativeName);
                        // TODO support wildcard hostnames
                        if (hostname.equals(subjectAlternativeName.get(1))) {
                            return true;
                        }
                    }
                }

                // Check matching CN value in subject
                X500Principal subjectPrincipal = x509Certificate.getSubjectX500Principal();
                String certificateCN = getHostnameFromCert(subjectPrincipal);
                log.debug("Checking certificate CN '{}' against hostname", certificateCN);
                // TODO support wildcard hostnames
                if (hostname.equals(certificateCN)) {
                    return true;
                }
            } else {
                log.warn("Unexpected certificate: {}", certificate.getType());
            }
        }

        return false;
    }

    /**
     * Authenticate against the remote server using SASL.
     * <p>
     * The CallbackHandler should be setup appropriately, for example:
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
     * @param cbh    CallbackHandler[] list of call backs that will be called by
     *               the SASL code
     * @param authId the authorization ID
     * @return ManageSieveResponse from the server, OK is authenticated, NO
     * means a problem
     * @throws SaslException
     * @throws IOException
     * @throws ParseException
     */
    public synchronized ManageSieveResponse authenticate(final CallbackHandler cbh, String authId) throws IOException, ParseException {

        SaslClient sc = Sasl.createSaslClient(cap.getSASLMethods(), authId, "sieve", hostname, null, cbh);

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
        label:
        do {
            token = in.nextToken();
            switch (token) {
                case DQUOTE:
                    // String - so more data for the auth sequence
                    in.pushBack();
                    String msg = parseString();
                    byte[] response = sc.evaluateChallenge(msg.getBytes());
                    sendLine(encodeString(new String(response)));
                    break;
                case StreamTokenizer.TT_WORD:
                    in.pushBack();
                    resp = parseResponse();
                    break label;
                default:
                    throw new ParseException("Expecting DQUOTE/WORD, got " + tokenToString(token) + " at line " + in.lineno());
            }
        } while (!sc.isComplete());

        // Complete
        sc.dispose();
        return resp;
    }

    /**
     * Authenticate against the remote server using SAS, using the given
     * username and password.
     *
     * @param username String username to authenticate with.
     * @param password String password to authenticate with.
     * @return OK on success, NO otherwise.
     */
    public synchronized ManageSieveResponse authenticate(final String username, final String password) throws IOException, ParseException {
        return authenticate(username, password, null);
    }

    /**
     * Authenticate against the remote server using SAS, using the given
     * username and password.
     *
     * @param username String username to authenticate with.
     * @param password String password to authenticate with.
     * @param authId   String authentication ID (may be null).
     * @return OK on success, NO otherwise.
     */
    public synchronized ManageSieveResponse authenticate(final String username, final String password, String authId) throws IOException, ParseException {
        CallbackHandler cbh = new CallbackHandler() {

            @Override
            public void handle(Callback[] clbcks) {
                for (Callback cb : clbcks) {
                    if (cb instanceof NameCallback) {
                        NameCallback name = (NameCallback) cb;
                        name.setName(username);
                    } else if (cb instanceof PasswordCallback) {
                        PasswordCallback passwd = (PasswordCallback) cb;
                        passwd.setPassword(password.toCharArray());
                    }
                }
            }
        };
        return authenticate(cbh, authId);
    }

    /**
     * "This command lists the scripts the user has on the server". The results
     * are stored into the @code{List&lt;SieveScript&gt;} passed in. Any existing
     * contents of this list will be lost. Up to one of the scripts listed will
     * be marked active.
     *
     * @param scripts non-null List of scripts. Will be
     *                cleared if not zero length, even if there is a problem
     * @return ManageSieveResponse OK - list was fetched, NO - there was a
     * problem.
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
     * @return OK if there is space, NO on error
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
     * active until SETACTIVE is used).
     * <p>
     * The server will check the script is valid before storing (and overwriting
     * if needed) the script, and will return parse errors in the "Human
     * readable" part of the response.
     * <p>
     * Even if the script is valid the response may contain WARNINGS.
     *
     * @param name String name of the script
     * @param body String body of the script
     * @return OK if the script is added, NO on error
     * @throws IOException
     * @throws ParseException
     */
    public synchronized ManageSieveResponse putscript(final String name, final String body) throws IOException, ParseException {
        String encodedName = escapeString(name);
        String encodedBody = encodeString(body);
        sendCommand("PUTSCRIPT", encodedName, encodedBody);
        return parseResponse();
    }

    /**
     * "This command gets the contents of the specified script". The name of the
     * script is taken from the script parameter, and the body is stored in the
     * object
     *
     * @param script SieveScript to fetch/update
     * @return OK or NO response.
     */
    public synchronized ManageSieveResponse getScript(SieveScript script) throws IOException, ParseException {
        String encodedName = escapeString(script.getName());
        sendCommand("GETSCRIPT", encodedName);
        ResponseAndPayload responseAndPayload = this.parseResponseWithPayload();
        script.setBody(responseAndPayload.getPayload());
        return responseAndPayload.getResponse();
    }

    /**
     * "This command is used to delete a user's Sieve script".
     *
     * @param name String name of the script to delete
     * @return OK if the script was deleted, NO otherwise
     * @throws IOException
     * @throws ParseException
     */
    public synchronized ManageSieveResponse deletescript(final String name) throws IOException, ParseException {
        String encodedName = escapeString(name);
        sendCommand("DELETESCRIPT", encodedName);
        return parseResponse();
    }

    /**
     * "This command sets a script active". The active script is the one used by
     * the MDA to filter incoming mail. It is not an error to have no scripts
     * active, or to set the same script active twice.
     * <p>
     * Use the empty string ("") to set no scripts active.
     *
     * @param name String name of the script to set active
     * @return OK on success, NO on error
     * @throws IOException
     * @throws ParseException
     */
    public synchronized ManageSieveResponse setactive(final String name) throws IOException, ParseException {
        String encodedName = escapeString(name);
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
                    // Unquoted word - end of capabilities
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
                        throw new ParseException("Expecting EOL got " + tokenToString(token) + " at " + in.lineno());
                    }
                    break;

                default:
                    throw new ParseException("Unexpected token " + token + " at " + in.lineno());
            }
        }
    }

    private ManageSieveResponse parseResponse() throws IOException, ParseException {
        in.nextToken();
        return parseResponseFromCurrentToken();
    }

    private ManageSieveResponse parseResponseFromCurrentToken() throws IOException, ParseException {
        ManageSieveResponse resp = new ManageSieveResponse();
        int token = in.ttype;
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

    private ResponseAndPayload parseResponseWithPayload() throws IOException, ParseException {
        int token = in.nextToken();
        String payload;
        ManageSieveResponse response;
        if (token == StreamTokenizer.TT_WORD) {
            payload = null;
            response = parseResponseFromCurrentToken();
        } else {
            payload = parseStringFromCurrentToken();
            int nextToken = in.nextToken();
            if (nextToken != StreamTokenizer.TT_EOL) {
                throw new ParseException("Expecting EOL but got " + tokenToString(token)
                        + " at line " + in.lineno());
            }
            response = parseResponse();
        }
        return new ResponseAndPayload(response, payload);
    }

    String parseString() throws IOException, ParseException {
        in.nextToken();
        return parseStringFromCurrentToken();
    }

    private byte[] growByteArray(byte[] array) {
        byte[] newArray = new byte[array.length * 2];
        System.arraycopy(array, 0, newArray, 0, array.length);
        return newArray;
    }

    private String parseStringFromCurrentToken() throws IOException, ParseException {
        int token = in.ttype;
        switch (token) {
            case DQUOTE:
                return in.sval;
            case '{':
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
                    throw new ParseException("Expecting } got " + tokenToString(token) + " at line " + in.lineno());
                }
                token = in.nextToken();
                if (token != StreamTokenizer.TT_EOL) {
                    throw new ParseException("Expecting EOL got " + tokenToString(token) + " at line " + in.lineno());
                }
                // Drop out of the tokenizer to read the raw bytes...

                log.debug("Raw string: reading {} bytes", length);
                in.resetSyntax();
                int count = 0;
                byte[] buff = new byte[1024];

                while (count < length) {
                    token = in.nextToken();
                    String tokenString;
                    if (token == StreamTokenizer.TT_EOF) {
                        // Let the caller deal with the EOF
                        in.pushBack();
                        break;
                    } else if (token == StreamTokenizer.TT_WORD) {
                        // Token is a string (StreamTokenizer calls some unicode chars strings)
                        tokenString = in.sval;
                    } else {
                        // Token is a java char, could be one or two bytes
                        tokenString = Character.toString((char) token);
                    }

                    // Add the UTF-8 bytes of tokenString to the buffer,
                    // growing it if needed
                    byte[] tokenBytes = tokenString.getBytes(charset);
                    if (count + tokenBytes.length >= buff.length) {
                        buff = growByteArray(buff);
                    }
                    System.arraycopy(tokenBytes, 0, buff, count, tokenBytes.length);
                    count += tokenBytes.length;
                }

                // Remember to reset the tokenizer now we're done
                setupTokenizer();

                return new String(buff, 0, count, charset);
            default:
                throw new ParseException("Expecting DQUOTE or {, got " + tokenToString(token) + " at line " + in.lineno());
        }
    }

    private String escapeString(final String raw) {
        StringBuilder result = new StringBuilder();
        result.append(DQUOTE);
        Matcher matcher = ESCAPE_RE.matcher(raw);
        String escaped = matcher.replaceAll("\\\\$1");
        if ((escaped.getBytes(charset).length - DQUOTE_LENGTH) > MAX_ESCAPED_STRING_LENGTH) {
            throw new IllegalArgumentException(String.format(
                    "The maximum size of of an escaped string should be <= %d",
                    MAX_ESCAPED_STRING_LENGTH));
        }
        result.append(escaped);
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
        result.append(Integer.toString(raw.getBytes(charset).length));
        result.append("+}");
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
        log.debug("Sending line: " + line);
        out.print(line);
        out.print(CRLF);
        out.flush();

        if (out.checkError()) {
            throw new IOException("Unknown error writing to server");
        }
    }

    private void setupAfterConnect(Socket sock) throws IOException {
        sock.setSoTimeout(socketTimeout);
        final BufferedInputStream byteStream = new BufferedInputStream(sock.getInputStream());
        in = new StreamTokenizer(new InputStreamReader(byteStream, charset));
        setupTokenizer();
        out = new PrintWriter(new OutputStreamWriter(sock.getOutputStream(), charset));
    }

    void setupForTesting(Reader from, Writer to) {
        in = new StreamTokenizer(from);
        out = new PrintWriter(to);

        setupTokenizer();
    }

    private void setupTokenizer() {
        in.resetSyntax();

        in.whitespaceChars(0x20, 0x20); // Space
        in.whitespaceChars(0x0A, 0x0A); // Newline
        in.whitespaceChars(0x0D, 0x0D); // Carriage return

        in.wordChars(0x23, 0x27);
        in.wordChars(0x2A, 0x5B);
        in.wordChars(0x5D, 0x7A);
        in.wordChars(0x7C, 0x7C);
        in.wordChars(0x7E, 0x7E);

        in.quoteChar(DQUOTE);
        in.parseNumbers();
        in.eolIsSignificant(true);
    }

    private String tokenToString(final int c) {
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
                    return ("WORD [" + in.sval + "]");
                default:
                    return "UNKNOWN";
            }
        }
    }

    private String getHostnameFromCert(X500Principal principal) {
        String raw = principal.getName("CANONICAL");
        for (String phrase : raw.split(",")) {
            String[] parts = phrase.split("=");
            String key = parts[0];
            String value = parts[1];
            if (key.equals("cn")) {
                return value;
            }
        }
        return null;
    }
}
