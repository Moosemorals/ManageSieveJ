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

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Holds deatils about the a servers capabilities.
 * @author "Osric Wilkinson" &lt;osric@fluffypeople.com&gt;
 */
public class ServerCapabilities {

    private String implementationName = null;
    private final Set<String> SASLMethods;
    private final Set<String> sieveExtensions;
    private boolean tls = false;
    private int maxRedirects = 0;
    private final Set<String> notify;
    private String language = null;
    private String owner = null;
    private String version = null;
    
    public ServerCapabilities() {
        SASLMethods = new LinkedHashSet<String>();
        sieveExtensions = new HashSet<String>();
        notify = new HashSet<String>();
    }
    
    public void setImplementationName(final String name) {
        this.implementationName = name;
    }
    
    public String getImplementationName() {
        return implementationName;
    }
    
    public void setSASLMethods(final String raw) {
	SASLMethods.clear();
        parseString(SASLMethods, raw);
    }
    
    public boolean hasSASLMethod(final String method) {
        return SASLMethods.contains(method);
    }
    
    public String[] getSASLMethods() {
        String[] result = new String[SASLMethods.size()];
        return SASLMethods.toArray(result);
    }
    
    public void setSieveExtensions(final String raw) {
	sieveExtensions.clear();
        parseString(sieveExtensions, raw);
    }
    
    public boolean hasSieveExtension(final String extension) {
        return sieveExtensions.contains(extension);
    }
    
    public void setHasTLS(final boolean tls) {
        this.tls = tls;
    }
    
    public boolean hasTLS() {
        return tls;
    }

    public void setNotify(final String raw) {
	notify.clear();
        parseString(notify, raw);
    }
    
    public void setMaxRedirects(final int maxRedirects) {
        this.maxRedirects = maxRedirects;
    }
    
    public int getMaxRedirects() {
        return maxRedirects;
    }
    
    public void setLanguage(final String language) {
        this.language = language;
    }
    
    public String getLanguage() {
        return language;
    }
    
    public void setOwner(final String owner) {
        this.owner = owner;
    }
    
    public String getOwner() {
        return owner;
    }
    
    public void setVersion(final String version) {
        this.version = version;
    }
    
    public String getVersion() {
        return version;
    }
       
    public boolean hasNoitfy(final String method) {
        return notify.contains(method.toLowerCase());
    }
    
    /**
     * Checks to see if the server is valid.
     * @return boolean true if the version is 1.0, and sieve extensions and implementation
     * have been set, false otherwise
     */
    public boolean isValid() {
        if (version == null || !version.equals("1.0")) {
            return false;
        }
        if (implementationName == null || implementationName.isEmpty()) {
            return false;
        }
        if (sieveExtensions.isEmpty()) {
            return false;
        }
        return true;
    }
    
    private static void parseString(final Set<String> target, final String raw) {
        String[] parts = raw.split("\\s+");
        target.addAll(Arrays.asList(parts));
    }
        
}
