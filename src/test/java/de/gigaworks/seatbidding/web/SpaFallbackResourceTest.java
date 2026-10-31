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

    @Test
    void fallbackSupportsNestedClientRoutesButNotApiOrManagementRoutes() throws Exception {
        var thread = Thread.currentThread();
        var original = thread.getContextClassLoader();
        thread.setContextClassLoader(new IndexClassLoader(original));
        try {
            var resource = new SpaFallbackResource();
            try (var response = resource.index("assignments/17/floor-plan")) {
                assertEquals(200, response.getStatus());
            }
            try (var response = resource.index("settings/reminders/skip")) {
                assertEquals(200, response.getStatus());
            }
            try (var response = resource.index("api/unknown")) {
                assertEquals(404, response.getStatus());
            }
            try (var response = resource.index("q/unknown")) {
                assertEquals(404, response.getStatus());
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