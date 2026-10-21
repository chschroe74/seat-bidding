package de.gigaworks.seatbidding.floorplan;

import de.gigaworks.seatbidding.exception.ApplicationProblem;
import de.gigaworks.seatbidding.round.SeatBiddingConfiguration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

@ApplicationScoped
public class SvgSanitizer {

    private static final Pattern DRAW_IO_SVG_11_DOCTYPE = Pattern.compile(
            "<!DOCTYPE\\s+svg\\s+PUBLIC\\s+\"-//W3C//DTD SVG 1\\.1//EN\"\\s+" +
                    "\"http://www\\.w3\\.org/Graphics/SVG/1\\.1/DTD/svg11\\.dtd\"\\s*>");
    private static final Set<String> ELEMENTS = Set.of("svg", "g", "path", "rect", "circle", "ellipse", "line",
            "polyline", "polygon", "text", "tspan", "defs", "clipPath", "mask", "linearGradient",
            "radialGradient", "stop", "pattern", "title", "desc");
    private static final Set<String> ATTRIBUTES = Set.of("viewBox", "x", "y", "x1", "y1", "x2", "y2", "cx",
            "cy", "r", "rx", "ry", "width", "height", "d", "points", "transform", "fill", "fill-opacity",
            "stroke", "stroke-width", "stroke-opacity", "opacity", "clip-path", "mask", "offset", "stop-color",
            "stop-opacity", "font-family", "font-size", "font-weight", "text-anchor", "dominant-baseline",
            "preserveAspectRatio", "patternUnits", "patternContentUnits", "patternTransform", "xmlns", "id",
            "class");

    @Inject
    SeatBiddingConfiguration configuration;

    public SanitizedSvg sanitize(byte[] bytes) {
        if (bytes.length == 0) {
            throw ApplicationProblem.badRequest("INVALID_SVG_SIZE", "Invalid floor plan", "The SVG file size is invalid.");
        }
        if (bytes.length > configuration.floorPlan().maxUploadBytes()) {
            throw new ApplicationProblem(413, "SVG_TOO_LARGE", "Floor plan is too large",
                    "The SVG exceeds the configured upload limit.");
        }
        byte[] normalized = normalizeDrawIoDoctype(bytes);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            return executor.submit(() -> parse(normalized)).get(configuration.floorPlan().parseTimeout().toMillis(),
                    TimeUnit.MILLISECONDS);
        }
        catch (ApplicationProblem problem) {
            throw problem;
        }
        catch (ExecutionException exception) {
            if (exception.getCause() instanceof ApplicationProblem problem) {
                throw problem;
            }
            throw invalidSvg();
        }
        catch (Exception exception) {
            throw invalidSvg();
        }
    }

    private static byte[] normalizeDrawIoDoctype(byte[] bytes) {
        String source;
        try {
            source = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        }
        catch (java.nio.charset.CharacterCodingException exception) {
            return bytes;
        }
        var matcher = DRAW_IO_SVG_11_DOCTYPE.matcher(source);
        if (!matcher.find()) {
            return bytes;
        }
        return matcher.replaceFirst("").getBytes(StandardCharsets.UTF_8);
    }

    private static ApplicationProblem invalidSvg() {
        return ApplicationProblem.badRequest("INVALID_SVG", "Invalid floor plan",
                "The SVG contains malformed, unsupported, or unsafe content.");
    }

    private SanitizedSvg parse(byte[] bytes) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new DefaultHandler() {
            @Override
            public void warning(SAXParseException exception) throws SAXException {
                throw exception;
            }

            @Override
            public void error(SAXParseException exception) throws SAXException {
                throw exception;
            }

            @Override
            public void fatalError(SAXParseException exception) throws SAXException {
                throw exception;
            }
        });
        var document = builder.parse(new ByteArrayInputStream(bytes));
        var root = document.getDocumentElement();
        if (!"svg".equals(root.getLocalName())) {
            throw new IllegalArgumentException("root");
        }
        String[] parts = root.getAttribute("viewBox").trim().split("[ ,]+");
        if (parts.length != 4) {
            throw new IllegalArgumentException("viewBox");
        }
        var minX = new BigDecimal(parts[0]);
        var minY = new BigDecimal(parts[1]);
        var width = new BigDecimal(parts[2]);
        var height = new BigDecimal(parts[3]);
        if (width.signum() <= 0 || height.signum() <= 0) {
            throw new IllegalArgumentException("viewBox");
        }
        int[] counts = new int[2];
        sanitizeNode(root, counts);
        if (counts[0] > configuration.floorPlan().maxElements()
                || counts[1] > configuration.floorPlan().maxPathCommands()) {
            throw new IllegalArgumentException("complexity");
        }
        var transformers = TransformerFactory.newInstance();
        transformers.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        transformers.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        var transformer = transformers.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
        var writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        String svg = writer.toString();
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(svg.getBytes(StandardCharsets.UTF_8)));
        return new SanitizedSvg(svg, hash, minX, minY, width, height);
    }

    private void sanitizeNode(Node node, int[] counts) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            var element = (Element) node;
            if ("style".equals(element.getLocalName())) {
                element.getParentNode().removeChild(element);
                return;
            }
            if (!ELEMENTS.contains(element.getLocalName())) {
                throw new IllegalArgumentException("element");
            }
            counts[0]++;
            for (int i = element.getAttributes().getLength() - 1; i >= 0; i--) {
                var attribute = element.getAttributes().item(i);
                String name = attribute.getNodeName();
                String value = attribute.getNodeValue().trim().toLowerCase();
                boolean unsafeUrl = value.contains("url(") && !value.matches("url\\(#[a-z_][a-z0-9_.:-]*\\)");
                String normalizedName = name.toLowerCase();
                if (normalizedName.startsWith("on") || normalizedName.equals("href")
                        || normalizedName.endsWith(":href") || unsafeUrl || value.contains("javascript:")
                        || value.contains("data:")) {
                    throw new IllegalArgumentException("active content");
                }
                if (!ATTRIBUTES.contains(name)) {
                    element.removeAttributeNode((org.w3c.dom.Attr) attribute);
                }
            }
            if ("path".equals(element.getLocalName())) {
                counts[1] += (int) element.getAttribute("d").chars().filter(Character::isLetter).count();
            }
        }
        for (Node child = node.getFirstChild(); child != null;) {
            Node next = child.getNextSibling();
            sanitizeNode(child, counts);
            child = next;
        }
    }

    public record SanitizedSvg(String content, String sha256, BigDecimal minX, BigDecimal minY, BigDecimal width,
            BigDecimal height) {
    }
}