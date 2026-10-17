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
class ConnectivityTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper json;

    @Test
    void tunisianRailsFollowAmountSalaryAndTheEditableFloor() throws Exception {
        assertThat(route("TN-BANK", "TND", "150000", "SUPPLIER", null).get("channelCode").asText()).isEqualTo("SGMT");
        assertThat(route("TN-BANK", "TND", "1000", "SUPPLIER", null).get("channelCode").asText()).isEqualTo("SIBTEL");
        assertThat(route("TN-BANK", "TND", "200000", "SALARY", null).get("channelCode").asText()).isEqualTo("SIBTEL");
        assertThat(route("TN-BANK", "TND", "500000", "TREASURY_TRANSFER", null).get("channelCode").asText()).isEqualTo("SGMT");
        assertThat(route("TN-BANK", "EUR", "1000", "SUPPLIER", null).get("channelCode").asText()).isEqualTo("SWIFT");
        JsonNode blocked = route("TN-BANK", "EUR", "1000", "INTERCOMPANY", "EU-BANK");
        assertThat(blocked.get("executable").asBoolean()).isFalse();
        assertThat(blocked.get("channelCode").isNull()).isTrue();

        body(post("/api/v1/platform/parameters/SGMT_FLOOR_TND", "{\"value\":\"500\"}"));
        assertThat(route("TN-BANK", "TND", "1000", "SUPPLIER", null).get("channelCode").asText()).isEqualTo("SGMT");
        assertThat(route("EU-BANK", "EUR", "1000", "SUPPLIER", null).get("channelCode").asText()).isEqualTo("SEPA");
        assertThat(route("EU-BANK", "EUR", "2000000", "SUPPLIER", null).get("format").asText()).isEqualTo("pacs.008");
    }

    @Test
    void remittanceProducesOneEnvelopeAndMt940Replays() throws Exception {
        String supplier = """
                {"entityId":"EU-BANK","debitAccountId":"EU-TARGET-EUR","beneficiaryName":"Fournisseur",
                 "currency":"EUR","amount":1000,"valueDate":"2026-10-17","purpose":"SUPPLIER"}
                """;
        JsonNode payment = body(post("/api/v1/cash/payments", supplier));
        body(post("/api/v1/cash/payments/" + payment.get("id").asText() + "/submit", null));
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/cash/payments/" + payment.get("id").asText() + "/approve")
                .headers(headers("karim.validator", "VALIDATOR", "TN-BANK,EU-BANK"))).andExpect(status().isOk());

        JsonNode first = body(post("/api/v1/connectivity/remit?entityId=EU-BANK", null));
        assertThat(first.get("generated").asInt()).isEqualTo(1);
        assertThat(first.get("messages").get(0).get("messageFormat").asText()).isEqualTo("pain.001");
        assertThat(first.get("messages").get(0).get("payload").asText()).contains("sandbox-only");
        JsonNode second = body(post("/api/v1/connectivity/remit?entityId=EU-BANK", null));
        assertThat(second.get("generated").asInt()).isEqualTo(0);
        JsonNode messages = body(MockMvcRequestBuilders.get("/api/v1/connectivity/messages?entityId=EU-BANK")
                .headers(headers("sara.trader", "TREASURER", "TN-BANK,EU-BANK")));
        assertThat(messages.size()).isEqualTo(1);

        String statement = """
                {"batchId":"MT940-EU-1","payload":":20:STMT1\\n:25:EU-TARGET-EUR\\n:60F:C261017EUR4000000,00\\n:61:261017C10,00NTRFDEMO61\\n:86:Test\\n:62F:C261017EUR4000010,00"}
                """;
        JsonNode imported = body(post("/api/v1/connectivity/statements", statement));
        assertThat(imported.get("replayed").asBoolean()).isFalse();
        assertThat(imported.get("accepted").asInt()).isEqualTo(2);
        JsonNode replay = body(post("/api/v1/connectivity/statements", statement));
        assertThat(replay.get("replayed").asBoolean()).isTrue();
    }

    private JsonNode route(String entity, String currency, String amount, String purpose, String beneficiary) throws Exception {
        String beneficiaryField = beneficiary == null ? "" : ",\"beneficiaryEntityId\":\"" + beneficiary + "\"";
        String payload = "{\"entityId\":\"" + entity + "\",\"currency\":\"" + currency + "\",\"amount\":" + amount
                + ",\"purpose\":\"" + purpose + "\"" + beneficiaryField + "}";
        return body(post("/api/v1/connectivity/route", payload));
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

    private HttpHeaders headers(String actor, String roles, String scope) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Actor", actor);
        headers.add("X-Roles", roles);
        headers.add("X-Entity-Scope", scope);
        return headers;
    }
}
