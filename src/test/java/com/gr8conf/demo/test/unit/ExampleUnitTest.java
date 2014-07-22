package com.gr8conf.demo.test.unit;

import com.gr8conf.demo.ChatVerticle;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

public class ExampleUnitTest {

  @Test
  public void testVerticle() {
    ChatVerticle vert = new ChatVerticle();

    // Interrogate your classes directly....

    assertNotNull(vert);
  }
}
