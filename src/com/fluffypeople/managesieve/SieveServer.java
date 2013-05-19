package com.fluffypeople.managesieve;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StreamTokenizer;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;
import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;

/**
 *
 * @author "Osric Wilkinson" <osric@fluffypeople.com>
 */
public class SieveServer {

    private static final Logger log = Logger.getLogger(SieveServer.class);
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
    private PrintWriter out;

    public SieveServer() {
    }

    public ServerCapabilities getCapabilities() {
        return cap;
    }

    public SieveResponse connect() throws IOException, ParseException {
        socket = new Socket(InetAddress.getByName("localhost"), 4190);

        setupAfterConnect(socket);
        return parseCapabilities();
    }

    public SieveResponse startTLS() throws IOException, ParseException {
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

    public SieveResponse authenticate(final String username, final String password) throws SaslException, IOException, ParseException {

        CallbackHandler cbh = new CallbackHandler() {
            @Override
            public void handle(Callback[] clbcks) throws IOException, UnsupportedCallbackException {
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
        Provider[] providers = Security.getProviders();

        for (Provider p : providers) {
            log.debug(p.getName());
        }

        String[] methods = cap.getSASLMethods();

        SaslClient sc = Sasl.createSaslClient(cap.getSASLMethods(), null, "sieve", "hathor.basement", null, cbh);

        String mechanism = escapeString(sc.getMechanismName());
        log.debug("trying to auth with " + mechanism);
        if (sc.hasInitialResponse()) {
            byte[] ir = sc.evaluateChallenge(new byte[0]);
            String ready = new String(Base64.encodeBase64(ir));
            ready = escapeString(ready.trim());
            log.debug("Inital response: " + ready);
            sendCommand("AUTHENTICATE", mechanism, ready);
        } else {
            sendCommand("AUTHENTICATE", mechanism);
        }

        int token;
        SieveResponse resp = null;
        do {

            log.debug("Trying next auth line");
            token = in.nextToken();
            if (token == DQUOTE) {
                // String - so more data for the auth sequence
                in.pushBack();
                String msg = parseString();
                log.debug("got " + msg + " from server");
                byte[] response = sc.evaluateChallenge(msg.getBytes());
                sendLine(encodeString(new String(response)));
            } else if (token == StreamTokenizer.TT_WORD) {
                in.pushBack();
                resp = parseResponse();
                break;
            } else {
                throw new ParseException("Expecting DQUOTE/WORD, got " + tokenToString(token) + " at line " + in.lineno());
            }
        } while (!sc.isComplete()) ;
        
        // Complete
        sc.dispose();
        
        
        return resp;
    }

    private void setupAfterConnect(Socket sock) throws IOException {
        in = new StreamTokenizer(new InputStreamReader(sock.getInputStream()));
        setTokenizerNormal();
        out = new PrintWriter(new OutputStreamWriter(sock.getOutputStream()));
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
        result.append(Integer.toString(raw.length()));
        result.append("}");
        result.append(CRLF);
        result.append(raw);

        return result.toString();
    }

    private void sendCommand(final String command, String... param) {
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

    private void sendLine(final String line) {
        log.debug("Sending: " + line);
        out.print(line);
        out.print(CRLF);
        out.flush();
    }

    private SieveResponse parseCapabilities() throws IOException, ParseException {
        cap = new ServerCapabilities();

        while (true) {
            int token = in.nextToken();
            log.debug("Got token " + tokenToString(token));
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
        log.debug("Parsing response");
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
            }
            // Check for human readable message
            token = in.nextToken();
            if (token != StreamTokenizer.TT_EOL) {
                in.pushBack();

                log.debug("Getting human maessge");
                resp.setMessage(parseString());
                log.debug("got human message");
                token = in.nextToken();
            }

            // Done, end of line            
            if (token != StreamTokenizer.TT_EOL) {
                throw new ParseException("Expecting EOL got " + tokenToString(token) + " at line " + in.lineno());
            }

        } else {
            throw new ParseException("Expecting WORD got " + tokenToString(token) + " at line " + in.lineno());
        }
        log.debug("Done parsing repsonse");
        return resp;
    }

    private String parseString() throws IOException, ParseException {
        log.debug("ParseString");
        int token = in.nextToken();
        if (token == DQUOTE) {
            log.debug("Got quote, returning " + in.sval);
            return in.sval;
        } else if (token == '{') {
            log.debug("Got {, parsing");
            token = in.nextToken();
            if (token != StreamTokenizer.TT_NUMBER) {
                throw new ParseException("Expecting NUMBER got " + tokenToString(token) + " at line " + in.lineno());
            }
            int length = (int) in.nval;
            StringBuilder result = new StringBuilder();
            token = in.nextToken();
            if (token != '}') {
                throw new ParseException("Expecing } got " + tokenToString(token) + " at line " + in.lineno());
            }
            token = in.nextToken();
            if (token != StreamTokenizer.TT_EOL) {
                throw new ParseException("Expecting EOL got " + tokenToString(token) + " at line " + in.lineno());
            }
            setTokenizerString();
            for (int i = 0; i < length; i++) {
                token = in.nextToken();
                if (token == StreamTokenizer.TT_EOF) {
                    throw new ParseException("Unexpected EOF reading string at line " + in.lineno());
                }
                result.append(Character.toChars(token)[0]);
            }
            setTokenizerNormal();
            return result.toString();
        } else {
            throw new ParseException("Expecing DQUOTE or {, got " + tokenToString(token) + " at line " + in.lineno());
        }
    }

    private void setTokenizerNormal() {
        in.resetSyntax();

        in.whitespaceChars(0x20, 0x20);
        in.whitespaceChars(0x0A, 0x0A);
        in.whitespaceChars(0x0D, 0x0D);

        in.wordChars(0x23, 0x27);
        in.wordChars(0x2A, 0x5B);
        in.wordChars(0x5D, 0x7A);
        in.wordChars(0x7C, 0x7E);

        in.quoteChar(DQUOTE);

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