package com.fluffypeople.managesieve;

import org.testng.annotations.*;

import java.io.*;

import static org.testng.Assert.*;

public class ManageSieveClientTest {

    @Test
    public void test_isConnected() {
        ManageSieveClient client = new ManageSieveClient();
        assertFalse(client.isConnected(), "Shouldn't be connected");
    }

    @Test
    public void test_ParseAsciiString() throws IOException, ParseException {
        ManageSieveClient client = new ManageSieveClient();

        String expected = "Hello World";
        String encoded = "{" + expected.getBytes("UTF-8").length + "}\r\n" + expected;

        StringReader in = new StringReader(encoded);
        StringWriter out = new StringWriter();

        client.setupForTesting(in, out);
        String actual = client.parseString();
        assertEquals(actual, expected);
    }

    @Test
    public void test_ParseUnicodeString() throws IOException, ParseException {
        ManageSieveClient client = new ManageSieveClient();

        String expected = "ÂÂβabc";
        String encoded = "{" + expected.getBytes("UTF-8").length + "}\r\n" + expected;

        StringReader in = new StringReader(encoded);
        StringWriter out = new StringWriter();

        client.setupForTesting(in, out);
        String actual = client.parseString();

        assertEquals(actual, expected);
    }
}