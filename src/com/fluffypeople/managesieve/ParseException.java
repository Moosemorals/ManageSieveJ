
package com.fluffypeople.managesieve;

/**
 *
 * @author "Osric Wilkinson" <osric@fluffypeople.com>
 */
public class ParseException extends Exception {

    /**
     * Creates a new instance of
     * <code>ParseException</code> without detail message.
     */
    public ParseException() {
    }

    /**
     * Constructs an instance of
     * <code>ParseException</code> with the specified detail message.
     *
     * @param msg the detail message.
     */
    public ParseException(String msg) {
        super(msg);
    }
}
