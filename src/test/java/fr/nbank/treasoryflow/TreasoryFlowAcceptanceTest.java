package fr.nbank.treasoryflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TreasoryFlowAcceptanceTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper json;

    @Test
    void tunisianRatioStaysReproducibleWhenTheRuleChanges() throws Exception {
        JsonNode first = run("TN-BANK", "TN_LIQUIDITY_STOCK");
        assertThat(decimal(first, "run", "result")).isEqualByComparingTo("1.066667");
        assertThat(first.get("run").get("ruleVersionId").asText()).isEqualTo("RULE-TN-LIQ-BCT-2014-14");
        assertThat(first.get("run").get("thresholdMet").asBoolean()).isTrue();
        assertThat(first.get("details").toString()).contains("COHERENCE_CASH_BCT");
        String runId = first.get("run").get("id").asText();

        JsonNode replay = body(post("/api/v1/regulatory/calculations/" + runId + "/replay", "sara.trader", "TREASURER", "TN-BANK,EU-BANK", null));
        assertThat(replay.get("matched").asBoolean()).isTrue();
        assertThat(decimal(replay, "replayedResult")).isEqualByComparingTo("1.066667");

        String draft = """
                {"ruleId":"TN-LIQ","jurisdiction":"TN","entityScope":"*","regulatoryReference":"Circulaire BCT n° 2014-14",
                 "ruleVersion":"BCT-2014-14-TEST","validFrom":"2026-10-17","formulaCode":"TN_LIQUIDITY_STOCK","testReference":"REC-TN-001",
                 "parameters":[
                   {"code":"WEIGHT","component":"CASH_BCT","value":0.5,"unit":"RATIO"},
                   {"code":"WEIGHT","component":"SOVEREIGN_TN","value":1,"unit":"RATIO"},
                   {"code":"WEIGHT","component":"IB_PLACEMENT_LE_1M","value":1,"unit":"RATIO"},
                   {"code":"WEIGHT","component":"DEMAND_DEPOSITS","value":1,"unit":"RATIO"},
                   {"code":"WEIGHT","component":"TERM_DEPOSITS_LE_1M","value":1,"unit":"RATIO"},
                   {"code":"MINIMUM","component":"*","value":1,"unit":"RATIO"}]}
                """;
        JsonNode created = body(post("/api/v1/regulatory/rules", "leila.compliance", "COMPLIANCE", "*", draft));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/regulatory/rules/" + created.get("id").asText() + "/publish")
                .headers(headers("leila.compliance", "COMPLIANCE", "*"))).andExpect(status().isConflict());
        body(post("/api/v1/regulatory/rules/" + created.get("id").asText() + "/publish", "admin", "ADMIN", "*", null));

        JsonNode stored = body(get("/api/v1/regulatory/calculations/" + runId, "nicolas.audit", "AUDITOR", "*", null));
        assertThat(decimal(stored, "run", "result")).isEqualByComparingTo("1.066667");
        assertThat(stored.get("run").get("ruleVersionId").asText()).isEqualTo("RULE-TN-LIQ-BCT-2014-14");

        JsonNode second = run("TN-BANK", "TN_LIQUIDITY_STOCK");
        assertThat(decimal(second, "run", "result")).isEqualByComparingTo("0.733333");
        assertThat(second.get("run").get("thresholdMet").asBoolean()).isFalse();
        assertThat(second.get("run").get("id").asText()).isNotEqualTo(runId);
    }

    @Test
    void europeanRatiosMatchTheReferenceAndDoNotApplyInTunisia() throws Exception {
        JsonNode lcr = run("EU-BANK", "EU_LCR");
        assertThat(decimal(lcr, "run", "result")).isEqualByComparingTo("5.545455");
        assertThat(decimal(lcr, "run", "threshold")).isEqualByComparingTo("1");
        JsonNode nsfr = run("EU-BANK", "EU_NSFR");
        assertThat(decimal(nsfr, "run", "result")).isEqualByComparingTo("1.442623");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/regulatory/calculations")
                .headers(headers("sara.trader", "TREASURER", "TN-BANK,EU-BANK"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"entityId\":\"TN-BANK\",\"formulaCode\":\"EU_LCR\",\"referenceDate\":\"2026-10-17\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/regulatory/calculations")
                .headers(headers("sara.trader", "TREASURER", "TN-BANK,EU-BANK"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"entityId\":\"EU-BANK\",\"formulaCode\":\"TN_LIQUIDITY_STOCK\",\"referenceDate\":\"2026-10-17\"}"))
                .andExpect(status().isConflict());
        JsonNode report = lcr.get("report");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/regulatory/reports/" + report.get("id").asText() + "/validate")
                .headers(headers("sara.trader", "TREASURER", "TN-BANK,EU-BANK"))).andExpect(status().isForbidden());
        JsonNode validated = body(post("/api/v1/regulatory/reports/" + report.get("id").asText() + "/validate", "leila.compliance", "COMPLIANCE", "*", null));
        assertThat(validated.get("status").asText()).isEqualTo("VALIDATED");
        assertThat(validated.get("inputSnapshotHash").asText()).isEqualTo(lcr.get("run").get("inputSnapshotHash").asText());
        JsonNode own = body(post("/api/v1/regulatory/calculations", "leila.compliance", "COMPLIANCE", "*",
                "{\"entityId\":\"EU-BANK\",\"formulaCode\":\"EU_NSFR\",\"referenceDate\":\"2026-10-17\"}"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/regulatory/reports/" + own.get("report").get("id").asText() + "/validate")
                .headers(headers("leila.compliance", "COMPLIANCE", "*"))).andExpect(status().isConflict());
    }

    @Test
    void consolidationRemovesIntercompanyAndBlocksRestrictedTransfers() throws Exception {
        JsonNode group = body(post("/api/v1/group/consolidations?scopeId=GRP-TN-EU&asOf=2026-10-17", "sara.trader", "TREASURER", "TN-BANK,EU-BANK", null));
        assertThat(decimal(group, "consolidatedAmount")).isEqualByComparingTo("155000000.00");
        assertThat(decimal(group, "eliminatedAmount")).isEqualByComparingTo("1000000.00");
        assertThat(decimal(group, "freelyTransferable")).isEqualByComparingTo("5000000.00");
        assertThat(decimal(group, "restricted")).isEqualByComparingTo("150000000.00");
        boolean rateKept = false;
        boolean restrictedNotTransferable = false;
        for (JsonNode line : group.get("lines")) {
            if ("TN-BANK".equals(line.get("entityId").asText()) && "TND".equals(line.get("originalCurrency").asText())) {
                assertThat(decimal(line, "rate")).isEqualByComparingTo("0.30000000");
                assertThat(line.get("rateSource").asText()).isEqualTo("BCT_REFERENCE");
                assertThat(line.get("freelyTransferable").asBoolean()).isFalse();
                rateKept = true;
                assertThat(line.get("transferReason").asText()).contains("n'est pas librement transférable");
                restrictedNotTransferable = true;
            }
            if (line.get("intercompany").asBoolean()) {
                assertThat(line.get("freelyTransferable").asBoolean()).isFalse();
            }
        }
        assertThat(rateKept).isTrue();
        assertThat(restrictedNotTransferable).isTrue();

        JsonNode recommendations = body(post("/api/v1/funding/analyse?scopeId=GRP-TN-EU&asOf=2026-10-17", "sara.trader", "TREASURER", "TN-BANK,EU-BANK", null));
        assertThat(recommendations).hasSize(1);
        JsonNode recommendation = recommendations.get(0);
        assertThat(recommendation.get("entityId").asText()).isEqualTo("EU-BANK");
        assertThat(decimal(recommendation, "deficit")).isEqualByComparingTo("2000000.00");
        assertThat(recommendation.get("proposedSourceEntityId").isNull()).isTrue();
        assertThat(recommendation.get("proposesRestrictedTransfer").asBoolean()).isFalse();
        assertThat(recommendation.get("blockedSources").asText()).contains("TN-BANK");
        assertThat(recommendation.get("explanation").asText()).contains("n'est pas proposée comme librement transférable");
    }

    @Test
    void fourEyesAndLimitsStopAMakerApprovingOrBreaching() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/deals").headers(headers("nicolas.audit", "AUDITOR", "*"))
                .contentType(MediaType.APPLICATION_JSON).content(deal("4000000.00", "REF-AUD")))
                .andExpect(status().isForbidden());
        JsonNode blocked = body(post("/api/v1/deals", "sara.trader", "TREASURER", "TN-BANK,EU-BANK", deal("4000000.00", "REF-BLOCK")));
        MvcResult submit = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/deals/" + blocked.get("id").asText() + "/submit")
                .headers(headers("sara.trader", "TREASURER", "TN-BANK,EU-BANK"))).andExpect(status().isConflict()).andReturn();
        assertThat(json.readTree(submit.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("blocked").asBoolean()).isTrue();

        JsonNode created = body(post("/api/v1/deals", "sara.trader", "TREASURER", "TN-BANK,EU-BANK", deal("1000.00", "REF-OK")));
        JsonNode pending = body(post("/api/v1/deals/" + created.get("id").asText() + "/submit", "sara.trader", "TREASURER", "TN-BANK,EU-BANK", null));
        assertThat(pending.get("deal").get("status").asText()).isEqualTo("PENDING_VALIDATION");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/deals/" + created.get("id").asText() + "/approve")
                .headers(headers("sara.trader", "TREASURER", "TN-BANK,EU-BANK"))).andExpect(status().isForbidden());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/deals/" + created.get("id").asText() + "/approve")
                .headers(headers("sara.trader", "VALIDATOR", "TN-BANK,EU-BANK"))).andExpect(status().isConflict());
        JsonNode approved = body(post("/api/v1/deals/" + created.get("id").asText() + "/approve", "karim.validator", "VALIDATOR", "TN-BANK,EU-BANK", null));
        assertThat(approved.get("status").asText()).isEqualTo("VALIDATED");
        assertThat(approved.get("approvedBy").asText()).isEqualTo("karim.validator");
    }

    @Test
    void replayingAnImportDoesNotDuplicateMovements() throws Exception {
        String payload = """
                {"sourceSystem":"CORE_BANKING","externalBatchId":"BATCH-REC-INT-001",
                 "balances":[{"externalId":"BAL-1","entityId":"TN-BANK","accountId":"TN-BCT-TND","currency":"TND","bookedAmount":500000000,"availableAmount":500000000,"asOf":"2026-10-17","sourceReference":"CORE-1"}],
                 "movements":[{"externalId":"MOV-REC-1","entityId":"TN-BANK","accountId":"TN-BCT-TND","currency":"TND","amount":10,"valueDate":"2026-10-17","bookingDate":"2026-10-17","reference":"NEW-1"}]}
                """;
        JsonNode first = body(post("/api/v1/imports/core-banking", "sara.trader", "TREASURER", "TN-BANK,EU-BANK", payload));
        assertThat(first.get("replayed").asBoolean()).isFalse();
        JsonNode again = body(post("/api/v1/imports/core-banking", "sara.trader", "TREASURER", "TN-BANK,EU-BANK", payload));
        assertThat(again.get("replayed").asBoolean()).isTrue();
        assertThat(again.get("id").asText()).isEqualTo(first.get("id").asText());
        JsonNode stored = body(get("/api/v1/imports/" + first.get("id").asText(), "sara.trader", "TREASURER", "TN-BANK,EU-BANK", null));
        assertThat(stored.get("movements")).hasSize(1);
        assertThat(stored.get("movements").get(0).get("externalId").asText()).isEqualTo("MOV-REC-1");
    }

    private JsonNode run(String entityId, String formula) throws Exception {
        return body(post("/api/v1/regulatory/calculations", "sara.trader", "TREASURER", "TN-BANK,EU-BANK",
                "{\"entityId\":\"" + entityId + "\",\"formulaCode\":\"" + formula + "\",\"referenceDate\":\"2026-10-17\"}"));
    }

    private String deal(String amount, String reference) {
        return "{\"entityId\":\"EU-BANK\",\"productCode\":\"IB_BORROW\",\"currency\":\"EUR\",\"amount\":" + amount
                + ",\"counterpartyId\":\"CP-BNPP\",\"reference\":\"" + reference
                + "\",\"tradeDate\":\"2026-10-17\",\"valueDate\":\"2026-10-17\",\"maturityDate\":\"2026-11-17\"}";
    }

    private JsonNode body(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder post(String path, String actor, String roles, String scope, String payload) {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).headers(headers(actor, roles, scope));
        if (payload != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(payload);
        }
        return request;
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder get(String path, String actor, String roles, String scope, String ignored) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).headers(headers(actor, roles, scope));
    }

    private org.springframework.http.HttpHeaders headers(String actor, String roles, String scope) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("X-Actor", actor);
        headers.add("X-Roles", roles);
        headers.add("X-Entity-Scope", scope);
        return headers;
    }

    private static BigDecimal decimal(JsonNode node, String... path) {
        JsonNode current = node;
        for (String field : path) {
            current = current.get(field);
        }
        return new BigDecimal(current.asText());
    }
}
