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

    @DataProvider(name = "FunkyStrings")
    public Object[][] createStrings() {
        return new String[][] {
                {"Hello World"}, // Basic Ascii
                {"ÂÂβabc"}, // Simple unicode
                {""}, // Empty string
                {"{11}\r\nHello World"}, // Nested
                {"\u0601\u0602\u0603\u0604\u0605\u061C\u06DD\u070F\u180E\u200B\u200C\u200D\u200E\u200F\u202A\u202B\u202C\u202D\u202E\u2060\u2061\u2062\u2063\u2064\u2066\u2067\u2068\u2069\u206A\u206B\u206C\u206D\u206E\u206F\uFEFF\uFFF9\uFFFA\uFFFB\uD804\uDCBD"}, // Unicode additional control characters
                {"ÅÍÎÏ˝ÓÔ\uF8FFÒÚÆ☃¡™£¢∞§¶•ªº–≠"}, // More funky unicode
                {"田中さんにあげて下さい"}, // Two byte chars
                {"表ポあA鷗ŒéＢ逍Üßªąñ丂㐀\uD840\uDC00"}, // Extreme unicode
                {"❤️ \uD83D\uDC94 \uD83D\uDC8C \uD83D\uDC95 \uD83D\uDC9E \uD83D\uDC93 \uD83D\uDC97 \uD83D\uDC96 \uD83D\uDC98 \uD83D\uDC9D \uD83D\uDC9F \uD83D\uDC9C \uD83D\uDC9B \uD83D\uDC9A \uD83D\uDC99"}, // Emoji
                {"בְּרֵאשִׁית, בָּרָא אֱלֹהִים, אֵת הַשָּׁמַיִם, וְאֵת הָאָרֶץ"}, // Right to left

        };
    }

    @Test(dataProvider = "FunkyStrings")
    public void test_ParseUnicodeString(String expected) throws IOException, ParseException {
        ManageSieveClient client = new ManageSieveClient();

        String encoded = "{" + expected.getBytes("UTF-8").length + "}\r\n" + expected;

        StringReader in = new StringReader(encoded);
        StringWriter out = new StringWriter();

        client.setupForTesting(in, out);
        String actual = client.parseString();

        assertEquals(actual, expected);
    }
}
