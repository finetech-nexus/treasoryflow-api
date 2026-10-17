package fr.nbank.treasoryflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CashManagementTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper json;

    @Test
    void worksheetCarriesForecastsAndPhysicalPoolBlocksRestrictedCash() throws Exception {
        JsonNode sheet = body(get("/api/v1/cash/worksheet?entityId=EU-BANK&asOf=2026-10-17"));
        assertThat(sheet.get("horizon").get(0).get("closing").decimalValue()).isEqualByComparingTo("4000000.00");
        assertThat(sheet.get("horizon").get(3).get("closing").decimalValue()).isEqualByComparingTo("7000000.00");
        assertThat(sheet.get("horizon").get(5).get("closing").decimalValue()).isEqualByComparingTo("-3000000.00");

        JsonNode sweep = body(post("/api/v1/cash/pools/POOL-EUR/sweep?asOf=2026-10-17", null));
        assertThat(sweep.get("executableAmount").decimalValue()).isEqualByComparingTo("0");
        assertThat(sweep.get("blockedAmount").decimalValue()).isEqualByComparingTo("1000000.00");
        assertThat(sweep.get("lines").get(0).get("executable").asBoolean()).isFalse();
        assertThat(sweep.get("lines").get(0).get("reason").asText()).contains("n'est pas librement transférable");
    }

    @Test
    void intercompanyPaymentFromTunisiaStaysDraftAndSupplierPaymentNeedsFourEyes() throws Exception {
        String blockedPayload = """
                {"entityId":"TN-BANK","debitAccountId":"TN-NOSTRO-EUR","beneficiaryName":"Périmètre européen",
                 "beneficiaryEntityId":"EU-BANK","currency":"EUR","amount":1000,"valueDate":"2026-10-17","purpose":"INTERCOMPANY"}
                """;
        JsonNode created = body(post("/api/v1/cash/payments", blockedPayload));
        MvcResult blocked = mockMvc.perform(post("/api/v1/cash/payments/" + created.get("id").asText() + "/submit", null))
                .andExpect(status().isConflict()).andReturn();
        JsonNode outcome = json.readTree(blocked.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(outcome.get("blocked").asBoolean()).isTrue();
        assertThat(outcome.get("payment").get("status").asText()).isEqualTo("DRAFT");

        String supplier = """
                {"entityId":"EU-BANK","debitAccountId":"EU-TARGET-EUR","beneficiaryName":"Fournisseur",
                 "currency":"EUR","amount":1000,"valueDate":"2026-10-17","purpose":"SUPPLIER"}
                """;
        JsonNode payment = body(post("/api/v1/cash/payments", supplier));
        JsonNode pending = body(post("/api/v1/cash/payments/" + payment.get("id").asText() + "/submit", null));
        assertThat(pending.get("payment").get("status").asText()).isEqualTo("PENDING_VALIDATION");
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/cash/payments/" + payment.get("id").asText() + "/approve")
                .headers(headers("sara.trader", "TREASURER", "TN-BANK,EU-BANK"))).andExpect(status().isForbidden());
        JsonNode approved = json.readTree(mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/cash/payments/" + payment.get("id").asText() + "/approve")
                .headers(headers("karim.validator", "VALIDATOR", "TN-BANK,EU-BANK"))).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(approved.get("status").asText()).isEqualTo("VALIDATED");
    }

    private JsonNode body(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder post(String path, String payload) {
        var request = MockMvcRequestBuilders.post(path).headers(headers("sara.trader", "TREASURER", "TN-BANK,EU-BANK"));
        if (payload != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(payload);
        }
        return request;
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder get(String path) {
        return MockMvcRequestBuilders.get(path).headers(headers("sara.trader", "TREASURER", "TN-BANK,EU-BANK"));
    }

    private HttpHeaders headers(String actor, String roles, String scope) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Actor", actor);
        headers.add("X-Roles", roles);
        headers.add("X-Entity-Scope", scope);
        return headers;
    }
}
