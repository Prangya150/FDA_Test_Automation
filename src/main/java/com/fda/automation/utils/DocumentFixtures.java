package com.fda.automation.utils;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves the placeholder documents under src/test/resources/documents/ to absolute
 * filesystem paths for Selenium file-input uploads (WebElement.sendKeys(path) - this
 * types the path directly into the hidden &lt;input type="file"&gt; and never opens an OS
 * file-picker dialog, so it works headless). Content is a minimal valid PDF; business
 * realism doesn't matter here, only that a real file exists at the given path.
 */
public class DocumentFixtures {

    private DocumentFixtures() {
    }

    public static final String IDENTIDAD = "identidad.pdf";
    public static final String COMPROBANTE_DOMICILIO = "comprobante-domicilio.pdf";
    public static final String CONSTANCIA_FISCAL = "constancia-fiscal.pdf";
    public static final String ACTA_CONSTITUTIVA = "acta-constitutiva.pdf";
    public static final String PODER_NOTARIAL = "poder-notarial.pdf";
    public static final String OPINION_CUMPLIMIENTO_SAT = "opinion-cumplimiento-sat.pdf";
    public static final String LICENCIA_COMERCIAL = "licencia-comercial.pdf";
    public static final String ESTADO_CUENTA = "estado-cuenta.pdf";

    public static String pathTo(String fileName) {
        URL resource = DocumentFixtures.class.getClassLoader().getResource("documents/" + fileName);
        if (resource == null) {
            throw new IllegalArgumentException("Document fixture not found on classpath: documents/" + fileName);
        }
        try {
            Path path = Paths.get(resource.toURI());
            return path.toAbsolutePath().toString();
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to resolve document fixture path for " + fileName, e);
        }
    }
}
