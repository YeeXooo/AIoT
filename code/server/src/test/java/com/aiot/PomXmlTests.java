package com.aiot;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("pom.xml dependency configuration")
class PomXmlTests {

    private static Document pom;
    private static XPath xpath;

    @BeforeAll
    static void parsePom() throws Exception {
        Path pomPath = Paths.get("pom.xml").toAbsolutePath().normalize();
        if (!pomPath.toFile().exists()) {
            throw new IllegalStateException("pom.xml not found at " + pomPath);
        }
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        pom = builder.parse(pomPath.toFile());
        xpath = XPathFactory.newInstance().newXPath();
    }

    @Nested
    @DisplayName("properties")
    class Properties {

        @Test
        @DisplayName("flyway.version is set to 10.10.0")
        void flywayVersion() throws Exception {
            String expr = "/project/properties/flyway.version/text()";
            String value = (String) xpath.evaluate(expr, pom, XPathConstants.STRING);
            assertEquals("10.10.0", value, "flyway.version must be 10.10.0");
        }

        @Test
        @DisplayName("java.version is set to 17")
        void javaVersion() throws Exception {
            String expr = "/project/properties/java.version/text()";
            String value = (String) xpath.evaluate(expr, pom, XPathConstants.STRING);
            assertEquals("17", value, "java.version must be 17");
        }
    }

    @Nested
    @DisplayName("dependencies")
    class Dependencies {

        @Nested
        @DisplayName("spring-boot-starter-websocket")
        class WebSocket {

            @Test
            @DisplayName("has correct groupId and artifactId")
            void coordinates() throws Exception {
                NodeList nodes = depNodes("org.springframework.boot", "spring-boot-starter-websocket");
                assertEquals(1, nodes.getLength(), "must have exactly one websocket dependency");
            }

            @Test
            @DisplayName("has default compile scope (no scope element)")
            void scopeIsCompile() throws Exception {
                Element dep = depElement("org.springframework.boot", "spring-boot-starter-websocket");
                assertNull(dep.getElementsByTagName("scope").item(0),
                        "websocket must have default scope (no explicit scope)");
            }

            @Test
            @DisplayName("has no version element")
            void versionNotHardcoded() throws Exception {
                Element dep = depElement("org.springframework.boot", "spring-boot-starter-websocket");
                assertNull(dep.getElementsByTagName("version").item(0),
                        "websocket version must be managed by parent BOM");
            }
        }

        @Nested
        @DisplayName("jackson-datatype-jsr310")
        class JacksonJsr310 {

            @Test
            @DisplayName("has correct groupId and artifactId")
            void coordinates() throws Exception {
                NodeList nodes = depNodes("com.fasterxml.jackson.datatype", "jackson-datatype-jsr310");
                assertEquals(1, nodes.getLength(), "must have exactly one jackson-datatype-jsr310 dependency");
            }

            @Test
            @DisplayName("has no version element")
            void versionNotHardcoded() throws Exception {
                Element dep = depElement("com.fasterxml.jackson.datatype", "jackson-datatype-jsr310");
                assertNull(dep.getElementsByTagName("version").item(0),
                        "jsr310 version must be managed by jackson-bom");
            }

            @Test
            @DisplayName("has default compile scope")
            void scopeIsCompile() throws Exception {
                Element dep = depElement("com.fasterxml.jackson.datatype", "jackson-datatype-jsr310");
                assertNull(dep.getElementsByTagName("scope").item(0),
                        "jsr310 must have default scope");
            }
        }

        @Nested
        @DisplayName("flyway-core")
        class FlywayCore {

            @Test
            @DisplayName("has correct groupId and artifactId")
            void coordinates() throws Exception {
                NodeList nodes = depNodes("org.flywaydb", "flyway-core");
                assertEquals(1, nodes.getLength(), "must have exactly one flyway-core dependency");
            }

            @Test
            @DisplayName("has no version element")
            void versionNotHardcoded() throws Exception {
                Element dep = depElement("org.flywaydb", "flyway-core");
                assertNull(dep.getElementsByTagName("version").item(0),
                        "flyway-core version must resolve via flyway.version property from BOM");
            }

            @Test
            @DisplayName("has default compile scope")
            void scopeIsCompile() throws Exception {
                Element dep = depElement("org.flywaydb", "flyway-core");
                assertNull(dep.getElementsByTagName("scope").item(0),
                        "flyway-core must have default scope");
            }
        }

        @Nested
        @DisplayName("flyway-database-postgresql")
        class FlywayPostgresql {

            @Test
            @DisplayName("has correct groupId and artifactId")
            void coordinates() throws Exception {
                NodeList nodes = depNodes("org.flywaydb", "flyway-database-postgresql");
                assertEquals(1, nodes.getLength(), "must have exactly one flyway-database-postgresql dependency");
            }

            @Test
            @DisplayName("has explicit version referencing flyway.version property")
            void versionIsFlywayVersionProperty() throws Exception {
                Element dep = depElement("org.flywaydb", "flyway-database-postgresql");
                Element versionEl = (Element) dep.getElementsByTagName("version").item(0);
                assertNotNull(versionEl, "flyway-database-postgresql must have explicit version");
                assertEquals("${flyway.version}", versionEl.getTextContent().trim(),
                        "version must be ${flyway.version} property reference");
            }

            @Test
            @DisplayName("has runtime scope")
            void scopeIsRuntime() throws Exception {
                Element dep = depElement("org.flywaydb", "flyway-database-postgresql");
                Element scopeEl = (Element) dep.getElementsByTagName("scope").item(0);
                assertNotNull(scopeEl, "flyway-database-postgresql must have explicit scope");
                assertEquals("runtime", scopeEl.getTextContent().trim(),
                        "scope must be runtime");
            }
        }

        @Nested
        @DisplayName("lombok")
        class Lombok {

            @Test
            @DisplayName("has correct groupId and artifactId")
            void coordinates() throws Exception {
                NodeList nodes = depNodes("org.projectlombok", "lombok");
                assertEquals(1, nodes.getLength(), "must have exactly one lombok dependency");
            }

            @Test
            @DisplayName("has no version element")
            void versionNotHardcoded() throws Exception {
                Element dep = depElement("org.projectlombok", "lombok");
                assertNull(dep.getElementsByTagName("version").item(0),
                        "lombok version must be managed by parent POM");
            }

            @Test
            @DisplayName("has provided scope")
            void scopeIsProvided() throws Exception {
                Element dep = depElement("org.projectlombok", "lombok");
                Element scopeEl = (Element) dep.getElementsByTagName("scope").item(0);
                assertNotNull(scopeEl, "lombok must have explicit scope");
                assertEquals("provided", scopeEl.getTextContent().trim(),
                        "lombok scope must be provided");
            }
        }

        @Nested
        @DisplayName("cross-dependency invariants")
        class Invariants {

            @Test
            @DisplayName("flyway-core and flyway-database-postgresql both resolve via same flyway.version property")
            void flywayVersionsConsistent() throws Exception {
                Element core = depElement("org.flywaydb", "flyway-core");
                Element pg = depElement("org.flywaydb", "flyway-database-postgresql");

                boolean coreUsesProperty = core.getElementsByTagName("version").getLength() == 0;
                Element pgVersionEl = (Element) pg.getElementsByTagName("version").item(0);
                boolean pgUsesProperty = pgVersionEl != null
                        && "${flyway.version}".equals(pgVersionEl.getTextContent().trim());

                assertTrue(coreUsesProperty,
                        "flyway-core must rely on property/BOM (no explicit version)");
                assertTrue(pgUsesProperty,
                        "flyway-database-postgresql must reference ${flyway.version}");
            }

            @Test
            @DisplayName("no duplicate dependencies introduced")
            void noDuplicates() throws Exception {
                String[][] depCoords = {
                        {"org.springframework.boot", "spring-boot-starter-websocket"},
                        {"com.fasterxml.jackson.datatype", "jackson-datatype-jsr310"},
                        {"org.flywaydb", "flyway-core"},
                        {"org.flywaydb", "flyway-database-postgresql"},
                        {"org.projectlombok", "lombok"},
                        {"org.springframework.boot", "spring-boot-starter"},
                        {"org.springframework.boot", "spring-boot-starter-data-jpa"},
                        {"org.springframework.boot", "spring-boot-starter-web"},
                        {"org.springframework.boot", "spring-boot-starter-validation"},
                        {"org.postgresql", "postgresql"},
                        {"org.springframework.boot", "spring-boot-starter-test"},
                        {"com.h2database", "h2"},
                };
                for (String[] coord : depCoords) {
                    NodeList nodes = depNodes(coord[0], coord[1]);
                    assertEquals(1, nodes.getLength(),
                            coord[0] + ":" + coord[1] + " must appear exactly once");
                }
            }
        }
    }

    @Nested
    @DisplayName("unchanged invariants from baseline")
    class UnchangedBaseline {

        @Test
        @DisplayName("postgresql driver has runtime scope")
        void postgresqlDriverScopeRuntime() throws Exception {
            Element dep = depElement("org.postgresql", "postgresql");
            Element scopeEl = (Element) dep.getElementsByTagName("scope").item(0);
            assertNotNull(scopeEl, "postgresql driver must have scope");
            assertEquals("runtime", scopeEl.getTextContent().trim());
        }

        @Test
        @DisplayName("h2 has runtime scope (dev datasource)")
        void h2ScopeRuntime() throws Exception {
            Element dep = depElement("com.h2database", "h2");
            Element scopeEl = (Element) dep.getElementsByTagName("scope").item(0);
            assertNotNull(scopeEl, "h2 must have scope");
            assertEquals("runtime", scopeEl.getTextContent().trim());
        }

        @Test
        @DisplayName("spring-boot-starter-test has test scope")
        void starterTestScopeTest() throws Exception {
            Element dep = depElement("org.springframework.boot", "spring-boot-starter-test");
            Element scopeEl = (Element) dep.getElementsByTagName("scope").item(0);
            assertNotNull(scopeEl, "spring-boot-starter-test must have scope");
            assertEquals("test", scopeEl.getTextContent().trim());
        }
    }

    private static Element depElement(String groupId, String artifactId) throws Exception {
        String expr = String.format(
                "/project/dependencies/dependency[groupId='%s' and artifactId='%s']",
                groupId, artifactId);
        return (Element) xpath.evaluate(expr, pom, XPathConstants.NODE);
    }

    private static NodeList depNodes(String groupId, String artifactId) throws Exception {
        String expr = String.format(
                "/project/dependencies/dependency[groupId='%s' and artifactId='%s']",
                groupId, artifactId);
        return (NodeList) xpath.evaluate(expr, pom, XPathConstants.NODESET);
    }
}
