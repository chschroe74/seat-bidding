package de.gigaworks.seatbidding.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SpaFallbackResourceTest {

    @Test
    void fallbackHtmlRequiresImmediateRevalidation() throws Exception {
        var thread = Thread.currentThread();
        var original = thread.getContextClassLoader();
        thread.setContextClassLoader(new IndexClassLoader(original));
        try {
            try (var response = new SpaFallbackResource().index("assignments")) {
                assertEquals(200, response.getStatus());
                assertEquals("no-cache", response.getHeaderString("Cache-Control"));
            }
        } finally {
            thread.setContextClassLoader(original);
        }
    }

    private static final class IndexClassLoader extends ClassLoader {

        private IndexClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if ("META-INF/resources/index.html".equals(name)) {
                return new ByteArrayInputStream("<html></html>".getBytes(StandardCharsets.UTF_8));
            }
            return super.getResourceAsStream(name);
        }
    }
}