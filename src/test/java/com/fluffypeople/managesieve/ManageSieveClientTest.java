package com.fluffypeople.managesieve;

import org.testng.annotations.*;
import static org.testng.Assert.*;

public class ManageSieveClientTest {
   
    @Test
    public void test_isConnected() {
        ManageSieveClient client = new ManageSieveClient();
        assertFalse(client.isConnected(), "Shouldn't be connected");
    }
}