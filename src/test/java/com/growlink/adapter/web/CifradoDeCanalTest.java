package com.growlink.adapter.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// ATRIBUTO confidencialidad (S3): lo que hace el front (cifrar el cuerpo con AES-GCM, envolver la llave con la
// publica del servicio) se repite aqui con las mismas primitivas, contra el login real y la cadena de seguridad.
// Se prueba que el servicio entiende lo cifrado, que responde cifrado (y que solo quien tiene la llave lo lee),
// que una peticion alterada se rechaza, y que sin cifrar todo sigue funcionando (llamadas entre servicios).
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CifradoDeCanalTest {

    private static final OAEPParameterSpec OAEP =
            new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void laLlavePublicaSePideSinSesion() throws Exception {
        MockHttpServletResponse r = mockMvc.perform(get("/api/seguridad/clave-publica")).andReturn().getResponse();

        assertThat(r.getStatus()).isEqualTo(200);
        JsonNode json = objectMapper.readTree(r.getContentAsString());
        assertThat(json.get("algoritmo").asText()).isEqualTo("RSA-OAEP-256");
        assertThat(json.get("clave").asText()).isNotBlank();
    }

    @Test
    void unLoginCifradoFuncionaYLaRespuestaVuelveCifradaYSoloSeLeeConLaLlave() throws Exception {
        SecretKey aes = nuevaLlaveAes();
        byte[] cuerpo = cifrar(aes, "{\"usuarioId\":1}");

        MockHttpServletResponse r = mockMvc.perform(post("/api/auth/login")
                        .header("X-GL-Clave", envolver(aes))
                        .header("X-GL-Tipo", "application/json")
                        .contentType("application/octet-stream")
                        .content(cuerpo))
                .andReturn().getResponse();

        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(r.getHeader("X-GL-Cifrado")).isEqualTo("1");
        assertThat(r.getHeader("X-GL-Tipo")).contains("application/json");
        byte[] cifrada = r.getContentAsByteArray();
        // en el canal no se ve nada del contenido
        assertThat(new String(cifrada, StandardCharsets.ISO_8859_1)).doesNotContain("token").doesNotContain("usuario");
        // con la llave de la peticion se lee la respuesta de siempre
        JsonNode json = objectMapper.readTree(descifrar(aes, cifrada));
        assertThat(json.get("token").asText()).isNotBlank();
        // y con otra llave no se puede
        SecretKey otra = nuevaLlaveAes();
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> descifrar(otra, cifrada));
    }

    @Test
    void unaPeticionAlteradaOConLlaveAjenaSeRechaza() throws Exception {
        SecretKey aes = nuevaLlaveAes();
        byte[] cuerpo = cifrar(aes, "{\"usuarioId\":1}");
        cuerpo[cuerpo.length - 1] ^= 0x01; // un solo bit cambiado: la etiqueta de GCM lo detecta

        MockHttpServletResponse alterada = mockMvc.perform(post("/api/auth/login")
                .header("X-GL-Clave", envolver(aes)).contentType("application/octet-stream").content(cuerpo))
                .andReturn().getResponse();
        MockHttpServletResponse basura = mockMvc.perform(post("/api/auth/login")
                .header("X-GL-Clave", Base64.getEncoder().encodeToString("no soy una llave".getBytes()))
                .contentType("application/octet-stream").content(cifrar(aes, "{}")))
                .andReturn().getResponse();

        assertThat(alterada.getStatus()).isEqualTo(400);
        assertThat(basura.getStatus()).isEqualTo(400);
    }

    @Test
    void sinCifrarTodoSigueFuncionando() throws Exception {
        MockHttpServletResponse r = mockMvc.perform(post("/api/auth/login")
                .contentType("application/json").content(objectMapper.writeValueAsString(Map.of("usuarioId", 1))))
                .andReturn().getResponse();

        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(r.getHeader("X-GL-Cifrado")).isNull();
        assertThat(objectMapper.readTree(r.getContentAsString()).get("token").asText()).isNotBlank();
    }

    @Test
    void unaRespuestaDeErrorTambienVuelveCifrada() throws Exception {
        SecretKey aes = nuevaLlaveAes();

        MockHttpServletResponse r = mockMvc.perform(post("/api/auth/login")
                .header("X-GL-Clave", envolver(aes)).header("X-GL-Tipo", "application/json")
                .contentType("application/octet-stream").content(cifrar(aes, "{\"usuarioId\":999999}")))
                .andReturn().getResponse();

        assertThat(r.getStatus()).isEqualTo(404);
        assertThat(r.getHeader("X-GL-Cifrado")).isEqualTo("1");
        assertThat(objectMapper.readTree(descifrar(aes, r.getContentAsByteArray())).has("message")).isTrue();
    }

    // ---- lo mismo que hace el front con Web Crypto ----

    private static SecretKey nuevaLlaveAes() throws Exception {
        KeyGenerator generador = KeyGenerator.getInstance("AES");
        generador.init(256);
        return generador.generateKey();
    }

    private String envolver(SecretKey aes) throws Exception {
        JsonNode json = objectMapper.readTree(
                mockMvc.perform(get("/api/seguridad/clave-publica")).andReturn().getResponse().getContentAsString());
        PublicKey publica = KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(json.get("clave").asText())));
        Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPPadding");
        rsa.init(Cipher.ENCRYPT_MODE, publica, OAEP);
        return Base64.getEncoder().encodeToString(rsa.doFinal(aes.getEncoded()));
    }

    private static byte[] cifrar(SecretKey aes, String texto) throws Exception {
        byte[] iv = new byte[12];
        new java.security.SecureRandom().nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, aes, new GCMParameterSpec(128, iv));
        byte[] ct = c.doFinal(texto.getBytes(StandardCharsets.UTF_8));
        byte[] salida = Arrays.copyOf(iv, 12 + ct.length);
        System.arraycopy(ct, 0, salida, 12, ct.length);
        return salida;
    }

    private static String descifrar(SecretKey aes, byte[] ivMasCifrado) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, aes, new GCMParameterSpec(128, ivMasCifrado, 0, 12));
        return new String(c.doFinal(ivMasCifrado, 12, ivMasCifrado.length - 12), StandardCharsets.UTF_8);
    }
}
