package com.fluffypeople.managesieve;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Holds deatils about the a servers capabilities.
 * @author "Osric Wilkinson" <osric@fluffypeople.com>
 */
public class ServerCapabilities {

    private String implementationName = null;
    private Set<String> SASLMethods;
    private Set<String> sieveExtensions;
    private boolean tls =false;
    private int maxRedirects = 0;
    private Set<String> notify;
    private String language = null;
    private String owner = null;
    private String version = null;
    
    public ServerCapabilities() {
        SASLMethods = new HashSet<String>();
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


