package com.app.carimbai.integration;

import com.app.carimbai.dtos.login.ResetPasswordRequest;
import com.app.carimbai.repositories.RevokedTokenRepository;
import com.app.carimbai.repositories.StaffUserRepository;
import com.app.carimbai.security.TokenRevocationService;
import com.app.carimbai.services.*;
import com.app.carimbai.config.PrivacyProperties;
import com.app.carimbai.config.PrivacyProperties.RetentionCategory;
import com.app.carimbai.dtos.privacy.PrivacyDtos.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.mockito.Mockito.doReturn;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PostgresSecurityIntegrationTest {
    private static final String PASSWORD = "Test-only-password-10!";
    private static final EmbeddedPostgres PG = startPostgres();
    private static final AtomicInteger IPS = new AtomicInteger();
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired BCryptPasswordEncoder encoder;
    @Autowired AuthService auth;
    @Autowired PasswordResetTokenService reset;
    @Autowired StaffUserRepository staff;
    @Autowired JwtService jwt;
    @Autowired TokenRevocationService revocation;
    @Autowired RevokedTokenRepository revoked;
    @Autowired PrivacyProperties privacy;
    @Autowired PrivacyJournalService journal;
    @Autowired PushSubscriptionService pushes;
    @MockitoSpyBean PrivacyNoticeService notices;
    @LocalServerPort int port;
    private String ip;

    private static EmbeddedPostgres startPostgres() {
        try {
            var pg = EmbeddedPostgres.builder().setPort(0).setServerConfig("listen_addresses", "127.0.0.1").start();
            // Build the previous schema first; Spring applies the new migration on startup.
            Flyway.configure().dataSource(pg.getPostgresDatabase()).locations("classpath:db.migration")
                    .target("2026.30.06.13.00.00").load().migrate();
            var previous = new JdbcTemplate(pg.getPostgresDatabase());
            previous.update("INSERT INTO core.merchants(name) VALUES ('Previous schema store')");
            previous.update("INSERT INTO core.staff_users(email,password_hash) VALUES ('previous@example.test','test-only-placeholder')");
            previous.update("INSERT INTO ops.refresh_tokens(token_hash,staff_user_id,merchant_id,expires_at) "
                    + "VALUES (?,1,1,NOW()+interval '1 day'),(?,1,1,NOW()+interval '1 day')", "a".repeat(64),"b".repeat(64));
            return pg;
        } catch (IOException ex) { throw new ExceptionInInitializerError(ex); }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> PG.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    @AfterAll static void closeDatabase() throws IOException { PG.close(); }

    @BeforeAll static void previousRowsAreBackfilled(@Autowired JdbcTemplate db) {
        assertThat(db.queryForObject("SELECT token_version FROM core.staff_users WHERE email='previous@example.test'",Long.class)).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM ops.refresh_tokens WHERE family_id IS NOT NULL AND token_version=0",Integer.class)).isEqualTo(2);
        assertThat(db.queryForObject("SELECT count(DISTINCT family_id) FROM ops.refresh_tokens",Integer.class)).isEqualTo(2);
    }

    @BeforeEach void fixtures() {
        privacy.setDestructiveActionsApproved(false);
        privacy.setDestructiveApprovalRef("");
        privacy.setRestorationQuarantine(false);
        privacy.setRestoreDryRun(true);
        privacy.setRetentionEnabled(false);
        privacy.setRetentionDryRun(true);
        privacy.setRetentionApprovalRef("");
        privacy.setRetentionBatchSize(100);
        privacy.getRetentionDays().clear();
        privacy.setJournalOrigin("integration");
        privacy.setJournalSigningSecret("test-only-privacy-signing-key-at-least-32-characters");
        ip = "192.0.2." + IPS.incrementAndGet();
        db.execute("TRUNCATE ops.privacy_requests,ops.privacy_events,ops.privacy_holds,ops.privacy_effects,"
                + "ops.privacy_replayed_effects,ops.privacy_preferences RESTART IDENTITY CASCADE");
        db.execute("TRUNCATE core.merchants, core.staff_users, fidelity.customers, ops.revoked_tokens, "
                + "ops.stamp_tokens, ops.idempotency_keys RESTART IDENTITY CASCADE");
        db.update("INSERT INTO core.merchants(name) VALUES ('Store A'),('Store B')");
        String hash = encoder.encode(PASSWORD);
        db.update("INSERT INTO core.staff_users(email,password_hash,platform_admin) VALUES "
                + "('admin@example.test',?,false),('backup@example.test',?,true),('cashier@example.test',?,false)", hash,hash,hash);
        db.update("INSERT INTO core.staff_user_merchants(staff_user_id,merchant_id,role,is_default) VALUES "
                + "(1,1,'ADMIN',true),(1,2,'CASHIER',false),(2,1,'ADMIN',true),(3,1,'CASHIER',true),(3,2,'CASHIER',false)");
        db.update("INSERT INTO fidelity.customers(email,name,provider_id) VALUES "
                + "('customer-a@example.test','Customer A','google:owner-a'),('customer-b@example.test','Customer B','google:owner-b')");
        db.update("INSERT INTO fidelity.programs(merchant_id,name,reward_name) VALUES (1,'Program A','Gift A'),(2,'Program B','Gift B')");
        db.update("INSERT INTO core.locations(merchant_id,name) VALUES (1,'Unit A'),(2,'Unit B')");
    }

    private MockHttpServletRequestBuilder call(MockHttpServletRequestBuilder call) {
        return call.with(req -> { req.setRemoteAddr(ip); return req; });
    }
    private JsonNode postJson(String path, Object body, String token, int expected) throws Exception {
        var request = call(post(path)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        String text = http.perform(request).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return text.isBlank() ? json.nullNode() : json.readTree(text);
    }
    private JsonNode login(String email) throws Exception {
        return postJson("/api/auth/login", Map.of("email",email,"password",PASSWORD),null,200);
    }
    private String customerToken(long id) {
        return jwt.generateCustomerToken(com.app.carimbai.models.fidelity.Customer.builder().id(id).build());
    }
    private void getStatus(String path, String token, int expected) throws Exception {
        var request = call(get(path));
        if (token != null) request.header("Authorization", "Bearer " + token);
        http.perform(request).andExpect(status().is(expected));
    }

    @Test void migrationUpgradesPreviousSchema() {
        assertThat(db.queryForObject("SELECT token_version FROM core.staff_users WHERE id=1",Long.class)).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version='2026.31.10.25.00.00' AND success",Integer.class)).isOne();
    }

    @Test void realFiltersUseCurrentRolesMembershipAndTenant() throws Exception {
        String token = login("admin@example.test").path("token").asText();
        getStatus("/api/merchants/1/admin/programs",null,401);
        getStatus("/api/merchants/1/admin/programs",customerToken(1),403);
        getStatus("/api/merchants/1/admin/programs",token,200);
        getStatus("/api/merchants/2/admin/programs",token,403);
        db.update("UPDATE core.staff_user_merchants SET role='CASHIER' WHERE staff_user_id=1 AND merchant_id=1");
        getStatus("/api/merchants/1/admin/programs",token,403);
        getStatus("/api/staff/dashboard/metrics",token,200);
        db.update("UPDATE core.staff_user_merchants SET active=false WHERE staff_user_id=1 AND merchant_id=1");
        getStatus("/api/staff/dashboard/metrics",token,401);
    }

    @Test void inactiveUserAndMerchantCannotUseExistingTokens() throws Exception {
        String token = login("admin@example.test").path("token").asText();
        db.update("UPDATE core.staff_users SET active=false WHERE id=1");
        getStatus("/api/staff/dashboard/metrics",token,401);
        db.update("UPDATE core.staff_users SET active=true WHERE id=1");
        db.update("UPDATE core.merchants SET active=false WHERE id=1");
        getStatus("/api/staff/dashboard/metrics",token,401);
    }

    @Test void platformAuthorityIsGlobalAndRevalidated() throws Exception {
        String admin = login("admin@example.test").path("token").asText();
        postJson("/api/merchants",Map.of("name","Store C"),admin,403);
        String platform = login("backup@example.test").path("token").asText();
        postJson("/api/merchants",Map.of("name","Store C"),platform,201);
        db.update("UPDATE core.staff_users SET platform_admin=false WHERE id=2");
        postJson("/api/merchants",Map.of("name","Store D"),platform,403);
    }

    @Test void counterCannotReadOrModifyExistingGlobalCustomer() throws Exception {
        String token = login("cashier@example.test").path("token").asText();
        postJson("/api/customers/login-or-register",Map.of("email","customer-b@example.test","name","Attacker"),token,403);
        postJson("/api/customers/login-or-register",Map.of("email","new@example.test","providerId","google:owner-b"),token,400);
        JsonNode created = postJson("/api/customers/login-or-register",Map.of("email","new@example.test","name","New"),token,201);
        assertThat(created.size()).isOne();
        assertThat(created.has("customerId")).isTrue();
        assertThat(db.queryForObject("SELECT provider_id FROM fidelity.customers WHERE id=2",String.class)).isEqualTo("google:owner-b");
        assertThat(db.queryForObject("SELECT name FROM fidelity.customers WHERE id=2",String.class)).isEqualTo("Customer B");
    }

    @Test void enrollmentNeedsAuthenticSingleUseProof() throws Exception {
        String token = login("admin@example.test").path("token").asText();
        String customer = customerToken(1);
        getStatus("/api/customers/me/enrollment-qr",token,403);
        JsonNode qr = json.readTree(http.perform(call(get("/api/customers/me/enrollment-qr"))
                .header("Authorization","Bearer " + customer)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var proof = Map.of("nonce",qr.path("nonce").asText(),"exp",qr.path("exp").asLong(),"sig",qr.path("sig").asText());
        postJson("/api/cards",Map.of("programId",1,"customerId",1),token,400);
        postJson("/api/cards",Map.of("programId",1,"customerId",2,"enrollmentProof",proof),token,400);
        postJson("/api/cards",Map.of("programId",2,"customerId",1,"enrollmentProof",proof),token,403);
        postJson("/api/cards",Map.of("programId",1,"customerId",1,"enrollmentProof",proof),token,201);
        postJson("/api/cards",Map.of("programId",1,"customerId",1,"enrollmentProof",proof),token,409);
        getStatus("/api/cards/customer/1",customer,200);
        getStatus("/api/cards/customer/2",customer,403);
    }

    @Test void logoutIsIdempotentPersistentAndRevokesRefreshFamily() throws Exception {
        JsonNode session = login("admin@example.test");
        String token = session.path("token").asText();
        var body = Map.of("refreshToken",session.path("refreshToken").asText());
        postJson("/api/auth/logout",body,token,204);
        postJson("/api/auth/logout",body,token,204);
        getStatus("/api/staff/dashboard/metrics",token,401);
        postJson("/api/auth/refresh",body,null,401);
        assertThat(new TokenRevocationService(revoked).isRevoked(jwt.extractJti(token))).isTrue();
        assertThat(db.queryForObject("SELECT count(*) FROM ops.revoked_tokens",Integer.class)).isOne();
        String customer = customerToken(1);
        postJson("/api/auth/logout",Map.of(),customer,204);
        getStatus("/api/cards/customer/1",customer,401);
        postJson("/api/auth/logout",Map.of(),"not-a-token",204);
        postJson("/api/auth/logout",Map.of(),null,204);
        db.update("INSERT INTO ops.revoked_tokens VALUES ('expired', NOW() - interval '1 second')");
        revocation.deleteExpired();
        assertThat(revoked.existsById("expired")).isFalse();
        assertThat(revoked.existsById(jwt.extractJti(customer))).isTrue();
    }

    @Test void legacyStaffSessionWithoutRefreshCanLogout() throws Exception {
        var user = staff.findById(1L).orElseThrow();
        var link = com.app.carimbai.models.core.StaffUserMerchant.builder().staffUser(user)
                .merchant(com.app.carimbai.models.core.Merchant.builder().id(1L).build())
                .role(com.app.carimbai.enums.StaffRole.ADMIN).build();
        String token = jwt.generateToken(user, link);
        getStatus("/api/staff/dashboard/metrics",token,200);
        postJson("/api/auth/logout",Map.of(),token,204);
        getStatus("/api/staff/dashboard/metrics",token,401);
        assertThat(db.queryForObject("SELECT count(*) FROM ops.refresh_tokens",Integer.class)).isZero();
    }

    @Test void refreshReplayRevokesSuccessorAndAccess() throws Exception {
        JsonNode session = login("admin@example.test");
        var old = Map.of("refreshToken",session.path("refreshToken").asText());
        JsonNode next = postJson("/api/auth/refresh",old,null,200);
        getStatus("/api/staff/dashboard/metrics",next.path("token").asText(),200);
        postJson("/api/auth/refresh",old,null,401);
        getStatus("/api/staff/dashboard/metrics",next.path("token").asText(),401);
        postJson("/api/auth/refresh",Map.of("refreshToken",next.path("refreshToken").asText()),null,401);
    }

    @Test void merchantSwitchInvalidatesOldSessionAndUsesNewRole() throws Exception {
        JsonNode session = login("admin@example.test");
        JsonNode next = postJson("/api/auth/switch-merchant",Map.of("merchantId",2),session.path("token").asText(),200);
        assertThat(next.path("role").asText()).isEqualTo("CASHIER");
        getStatus("/api/staff/dashboard/metrics",session.path("token").asText(),401);
        getStatus("/api/staff/dashboard/metrics",next.path("token").asText(),200);
        getStatus("/api/merchants/2/staff-users",next.path("token").asText(),403);
    }

    @Test void resetReplacesPasswordOnceAndInvalidatesAllEarlierCredentials() throws Exception {
        JsonNode session = login("admin@example.test");
        String raw = reset.issue(staff.findById(1L).orElseThrow()).rawToken();
        var body = Map.of("token",raw,"newPassword","Replacement-password-20!");
        postJson("/api/auth/reset-password",body,null,204);
        postJson("/api/auth/reset-password",body,null,401);
        getStatus("/api/staff/dashboard/metrics",session.path("token").asText(),401);
        postJson("/api/auth/refresh",Map.of("refreshToken",session.path("refreshToken").asText()),null,401);
        postJson("/api/auth/login",Map.of("email","admin@example.test","password",PASSWORD),null,401);
        postJson("/api/auth/login",Map.of("email","admin@example.test","password","Replacement-password-20!"),null,200);
    }

    @Test void concurrentRefreshAndResetHaveOneSuccessfulConsumer() throws Exception {
        String rawRefresh = login("admin@example.test").path("refreshToken").asText();
        assertThat(race(() -> auth.refresh(rawRefresh),RefreshTokenService.RefreshTokenInvalidException.class)).containsExactlyInAnyOrder(true,false);
        String raw = reset.issue(staff.findById(1L).orElseThrow()).rawToken();
        assertThat(race(() -> { auth.resetPassword(new ResetPasswordRequest(raw,"Concurrent-password-30!")); return null; },
                PasswordResetTokenService.PasswordResetTokenInvalidException.class)).containsExactlyInAnyOrder(true,false);
        assertThat(staff.findById(1L).orElseThrow().getTokenVersion()).isEqualTo(1);
    }

    private List<Boolean> race(Callable<?> operation, Class<? extends RuntimeException> expected) throws Exception {
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> task = () -> {
                ready.countDown();
                if (!start.await(10,TimeUnit.SECONDS)) throw new AssertionError("Race did not start");
                try { operation.call(); return true; }
                catch (RuntimeException ex) { if (expected.isInstance(ex)) return false; throw ex; }
            };
            var first = pool.submit(task); var second = pool.submit(task);
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue(); start.countDown();
            return List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));
        }
    }

    @Test void adminUpdatesAreTenantScopedAndPreserveOtherLinks() throws Exception {
        String token = login("admin@example.test").path("token").asText();
        getStatus("/api/merchants/1/locations",token,200);
        getStatus("/api/merchants/1/staff-users",token,200);
        http.perform(call(put("/api/merchants/1/locations/2")).header("Authorization","Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Stolen\"}")).andExpect(status().isForbidden());
        http.perform(call(patch("/api/merchants/1/staff-users/3")).header("Authorization","Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}")).andExpect(status().isOk());
        assertThat(db.queryForObject("SELECT active FROM core.staff_user_merchants WHERE staff_user_id=3 AND merchant_id=2",Boolean.class)).isTrue();
        http.perform(call(get("/api/merchants/1/staff-users")).header("Authorization","Bearer " + token))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("passwordHash"))));
    }

    @Test void pushRoutesValidateOwnershipDestinationAndRoles() throws Exception {
        byte[] publicKey = new byte[65]; publicKey[0]=4;
        var keys = Map.of("p256dh",Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey),
                "auth",Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]));
        var body = Map.of("customerId",1,"endpoint","https://fcm.googleapis.com/fcm/send/test-only","keys",keys);
        String customer = customerToken(1);
        postJson("/api/notifications/subscribe",body,customer,409);
        postJson("/api/notifications/subscribe",body,null,401);
        postJson("/api/notifications/subscribe",body,login("cashier@example.test").path("token").asText(),403);
        postJson("/api/notifications/subscribe",Map.of("customerId",2,"endpoint",body.get("endpoint"),"keys",keys),customer,403);
        postJson("/api/notifications/subscribe",Map.of("endpoint","https://127.0.0.1/internal","keys",keys),customer,400);
        getStatus("/api/staff/dashboard/metrics",customer,403);
        getStatus("/api/not-a-public-route",customer,403);
        getStatus("/api/notifications/vapid-public-key",null,503);
        http.perform(options("/api/notifications/subscribe").header("Origin","http://localhost:5173")
                .header("Access-Control-Request-Method","POST")).andExpect(status().isOk());
    }

    @Test void genericRecoveryAndMalformedOrOversizedJsonAreHandled() throws Exception {
        postJson("/api/auth/forgot-password",Map.of("email","admin@example.test"),null,204);
        postJson("/api/auth/forgot-password",Map.of("email","absent@example.test"),null,204);
        http.perform(call(post("/api/auth/login")).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
        String oversized = "{\"email\":\"" + "a".repeat(262144) + "\"}";
        http.perform(call(post("/api/auth/forgot-password")).contentType(MediaType.APPLICATION_JSON).content(oversized))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test void chunkedRequestsAreLimitedOnRealHttpServer() throws Exception {
        byte[] bytes = ("{\"email\":\"" + "a".repeat(262144) + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try (HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/forgot-password"))
                    .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(bytes))).build();
            assertThat(client.send(request,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(413);
        }
    }

    private String officer() throws Exception {
        db.update("UPDATE core.staff_users SET privacy_officer=true WHERE id=2");
        return login("backup@example.test").path("token").asText();
    }
    private String request(String token,String type,String scope,Long merchant) throws Exception {
        Map<String,Object> body=new HashMap<>(Map.of("type",type,"scope",scope,"details","Test-only request"));
        if (merchant!=null) body.put("merchantId",merchant);
        return postJson("/api/privacy/me/requests",body,token,201).path("id").asText();
    }
    private JsonNode review(String protocol,String token,String action,String confirmation,int expected) throws Exception {
        return postJson("/api/privacy/admin/requests/"+protocol+"/review",
                Map.of("action",action,"confirmation",confirmation,"response","Test-only reviewed response","evidenceRef","TEST-ONLY-01"),token,expected);
    }
    private JsonNode read(String path,String token) throws Exception {
        return json.readTree(http.perform(call(get(path)).header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store")))
                .andReturn().getResponse().getContentAsString());
    }
    private void approveDestructiveForTest() {
        privacy.setDestructiveActionsApproved(true); privacy.setDestructiveApprovalRef("TEST-ONLY-NOT-A-REAL-POLICY");
    }
    private void seedCards() {
        db.update("INSERT INTO fidelity.cards(program_id,customer_id) VALUES (1,1),(2,1),(1,2)");
        db.update("INSERT INTO fidelity.stamps(card_id,source,cashier_id) VALUES (1,'A',3),(2,'A',3),(3,'A',3)");
        db.update("INSERT INTO fidelity.rewards(card_id,cashier_id) VALUES (1,3),(2,3),(3,3)");
    }
    private void seedPush() {
        db.update("INSERT INTO ops.push_subscriptions(customer_id,endpoint,p256dh,auth) VALUES "
                +"(1,'https://fcm.googleapis.com/one','test-key','test-auth'),(2,'https://fcm.googleapis.com/two','other-key','other-auth')");
    }

    @Test void privacyMigrationDoesNotGrantOfficersOrAssumePushConsent() {
        assertThat(db.queryForObject("SELECT count(*) FROM core.staff_users WHERE privacy_officer",Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM fidelity.customers WHERE active AND NOT push_enabled AND token_version=0 AND privacy_key IS NOT NULL",Integer.class)).isEqualTo(2);
        assertThat(db.queryForObject("SELECT count(DISTINCT privacy_key) FROM fidelity.customers",Integer.class)).isEqualTo(2);
    }
    @Test void publicNoticeRemainsDraftAndOptionalActivationFailsClosed() throws Exception {
        http.perform(get("/api/privacy/notice")).andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(false)).andExpect(jsonPath("$.contactEmail").value("diniz3003@gmail.com"))
                .andExpect(jsonPath("$.controllerDocument").value(org.hamcrest.Matchers.containsString("FICTICIO")));
        http.perform(call(put("/api/notifications/preferences")).header("Authorization","Bearer "+customerToken(1))
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true,\"noticeVersion\":\"draft-2026-10-26\"}"))
                .andExpect(status().isConflict());
        assertThat(pushes.canSend(1L)).isFalse();
    }
    @Test void privacyQueueRequiresExplicitCurrentOfficerNotMerchantOrPlatformAdmin() throws Exception {
        getStatus("/api/privacy/admin/requests",null,401);
        getStatus("/api/privacy/admin/requests",customerToken(1),403);
        getStatus("/api/privacy/admin/requests",login("admin@example.test").path("token").asText(),403);
        String platform=login("backup@example.test").path("token").asText();
        getStatus("/api/privacy/admin/requests",platform,403);
        db.update("UPDATE core.staff_users SET privacy_officer=true WHERE id=2");
        getStatus("/api/privacy/admin/requests",platform,200);
        db.update("UPDATE core.staff_users SET privacy_officer=false WHERE id=2");
        getStatus("/api/privacy/admin/requests",platform,403);
    }
    @Test void protocolsAreOwnedByTheAuthenticatedSubjectAndCannotBeReadByAnother() throws Exception {
        String protocol=request(customerToken(1),"ACCESS","ACCOUNT",null);
        assertThat(UUID.fromString(protocol)).isNotNull();
        getStatus("/api/privacy/me/requests/"+protocol,customerToken(2),404);
        getStatus("/api/privacy/me/requests/"+protocol,login("admin@example.test").path("token").asText(),404);
        assertThat(read("/api/privacy/me/requests",customerToken(1)).size()).isOne();
        assertThat(read("/api/privacy/me/requests",customerToken(2)).size()).isZero();
        postJson("/api/privacy/me/requests",Map.of("type","ERASURE","scope","MERCHANT","merchantId",2),customerToken(1),403);
        postJson("/api/privacy/me/requests",Map.of("type","ERASURE","scope","ACCOUNT","merchantId",2),customerToken(1),400);
    }
    @Test void exportIsBoundedOwnedAndRedactsCredentialsAndOtherContacts() throws Exception {
        seedCards(); seedPush();
        String owner=customerToken(1);
        JsonNode first=read("/api/privacy/me/export?section=cards&limit=1",owner);
        assertThat(first.path("items").size()).isOne();
        assertThat(first.path("nextAfterId").isNumber()).isTrue();
        JsonNode second=read("/api/privacy/me/export?section=cards&limit=1&after="+first.path("nextAfterId").asLong(),owner);
        assertThat(second.path("items").size()).isOne();
        assertThat(second.path("nextAfterId").isNull()).isTrue();
        for (String section:PrivacyService.EXPORT_SECTIONS) {
            String exported=read("/api/privacy/me/export?section="+section,owner).toString();
            assertThat(exported).doesNotContain("owner-b","customer-b@example.test","provider_id","token_hash","p256dh","test-auth","other-key");
        }
        getStatus("/api/privacy/me/export?section=profile&limit=101",owner,400);
        getStatus("/api/privacy/me/export?section=not-a-table",owner,400);
        getStatus("/api/privacy/me/export?section=profile",null,401);
    }
    @Test void correctionOnlyChangesOwnNameAndPhoneAndRejectsIdentityOverposting() throws Exception {
        String owner=customerToken(1);
        http.perform(call(patch("/api/privacy/me/profile")).header("Authorization","Bearer "+owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Corrected\",\"phone\":null}")).andExpect(status().isOk());
        assertThat(db.queryForObject("SELECT name FROM fidelity.customers WHERE id=1",String.class)).isEqualTo("Corrected");
        assertThat(db.queryForObject("SELECT name FROM fidelity.customers WHERE id=2",String.class)).isEqualTo("Customer B");
        http.perform(call(patch("/api/privacy/me/profile")).header("Authorization","Bearer "+owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Wrong\",\"email\":\"other@example.test\"}")).andExpect(status().isBadRequest());
        assertThat(db.queryForObject("SELECT provider_id FROM fidelity.customers WHERE id=1",String.class)).isEqualTo("google:owner-a");
        assertThat(db.queryForObject("SELECT profile_updated_at IS NOT NULL FROM fidelity.customers WHERE id=1",Boolean.class)).isTrue();
    }
    @Test void destructiveRightsRequireClaimPolicyAndExplicitScopeConfirmation() throws Exception {
        seedCards();
        String protocol=request(customerToken(1),"ERASURE","ACCOUNT",null), handler=officer();
        review(protocol,handler,"ERASE","ERASE_ACCOUNT",403);
        review(protocol,handler,"CLAIM","",200);
        review(protocol,handler,"ERASE","ERASE_ACCOUNT",503);
        assertThat(db.queryForObject("SELECT count(*) FROM fidelity.cards",Integer.class)).isEqualTo(3);
        approveDestructiveForTest();
        review(protocol,handler,"ERASE","ERASE_MERCHANT",400);
        assertThat(db.queryForObject("SELECT active FROM fidelity.customers WHERE id=1",Boolean.class)).isTrue();
    }
    @Test void globalErasureRemovesDirectIdentityAndBusinessLinksButLeavesExplicitTombstone() throws Exception {
        seedCards(); seedPush();
        String owner=customerToken(1), handler=officer(), protocol=request(owner,"ERASURE","ACCOUNT",null);
        review(protocol,handler,"CLAIM","",200); approveDestructiveForTest();
        assertThat(review(protocol,handler,"ERASE","ERASE_ACCOUNT",200).path("status").asText()).isEqualTo("APPLIED_LOCALLY");
        getStatus("/api/privacy/me/profile",owner,401);
        assertThat(db.queryForObject("SELECT active FROM fidelity.customers WHERE id=1",Boolean.class)).isFalse();
        assertThat(db.queryForObject("SELECT name IS NULL AND email IS NULL AND provider_id IS NULL FROM fidelity.customers WHERE id=1",Boolean.class)).isTrue();
        assertThat(db.queryForObject("SELECT count(*) FROM fidelity.cards WHERE customer_id=1",Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM fidelity.stamps",Integer.class)).isOne();
        assertThat(db.queryForObject("SELECT count(*) FROM fidelity.rewards",Integer.class)).isOne();
        assertThat(db.queryForObject("SELECT count(*) FROM ops.push_subscriptions WHERE customer_id=1",Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM ops.privacy_effects WHERE action='ERASE_ACCOUNT'",Integer.class)).isOne();
        assertThat(review(protocol,handler,"CLAIM","",200).path("status").asText()).isEqualTo("APPLIED_LOCALLY");
        review(protocol,handler,"ERASE","ERASE_ACCOUNT",409);
        assertThat(review(protocol,handler,"COMPLETE","",200).path("status").asText()).isEqualTo("COMPLETED");
    }
    @Test void merchantErasurePreservesOtherStoresAndGlobalProfile() throws Exception {
        seedCards();
        String owner=customerToken(1), protocol=request(owner,"ERASURE","MERCHANT",1L), handler=officer();
        review(protocol,handler,"CLAIM","",200); approveDestructiveForTest();
        review(protocol,handler,"ERASE","ERASE_MERCHANT",200);
        getStatus("/api/privacy/me/profile",owner,200);
        assertThat(db.queryForObject("SELECT name FROM fidelity.customers WHERE id=1",String.class)).isEqualTo("Customer A");
        assertThat(db.queryForObject("SELECT count(*) FROM fidelity.cards WHERE customer_id=1 AND program_id=2",Integer.class)).isOne();
        assertThat(db.queryForObject("SELECT count(*) FROM fidelity.cards WHERE customer_id=1 AND program_id=1",Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM fidelity.cards WHERE customer_id=2",Integer.class)).isOne();
    }
    @Test void preservationHoldBlocksErasureUntilExplicitEvidencedRelease() throws Exception {
        String protocol=request(customerToken(1),"ERASURE","ACCOUNT",null), handler=officer();
        UUID key=db.queryForObject("SELECT privacy_key FROM fidelity.customers WHERE id=1",UUID.class);
        String hold=postJson("/api/privacy/admin/holds",Map.of("subjectType","CUSTOMER","subjectKey",key,
                "reasonCode","LEGAL_REVIEW","evidenceRef","TEST-ONLY-HOLD","reviewAt",java.time.Instant.now().plusSeconds(86400).toString()),handler,200).path("id").asText();
        review(protocol,handler,"CLAIM","",200); approveDestructiveForTest();
        review(protocol,handler,"ERASE","ERASE_ACCOUNT",409);
        postJson("/api/privacy/admin/holds/"+hold+"/release",Map.of(),handler,400);
        postJson("/api/privacy/admin/holds/"+hold+"/release",Map.of("evidenceRef","TEST-ONLY-RELEASE"),handler,204);
        review(protocol,handler,"ERASE","ERASE_ACCOUNT",200);
        assertThat(db.queryForObject("SELECT released_by FROM ops.privacy_holds WHERE id=?",Long.class,UUID.fromString(hold))).isEqualTo(2L);
    }
    @Test void restrictionCannotRemoveLastActiveMerchantAdministrator() throws Exception {
        String admin=login("admin@example.test").path("token").asText();
        String protocol=request(admin,"RESTRICTION","ACCOUNT",null), handler=officer();
        db.update("UPDATE core.staff_user_merchants SET active=false WHERE staff_user_id=2 AND merchant_id=1");
        // The officer needs an active membership to authenticate, even for privacy work.
        db.update("INSERT INTO core.staff_user_merchants(staff_user_id,merchant_id,role,is_default) VALUES(2,2,'CASHIER',true)");
        handler=login("backup@example.test").path("token").asText();
        review(protocol,handler,"CLAIM","",200); approveDestructiveForTest();
        review(protocol,handler,"RESTRICT","RESTRICT_ACCOUNT",409);
        assertThat(db.queryForObject("SELECT active FROM core.staff_users WHERE id=1",Boolean.class)).isTrue();
    }
    @Test void oldPushRowsDoNotConstituteConsentAndWithdrawalOnlyAffectsOwner() throws Exception {
        seedPush();
        assertThat(pushes.listByCustomer(1L)).isEmpty();
        http.perform(call(delete("/api/notifications/subscriptions")).header("Authorization","Bearer "+customerToken(1))).andExpect(status().isNoContent());
        assertThat(db.queryForObject("SELECT count(*) FROM ops.push_subscriptions WHERE customer_id=1",Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM ops.push_subscriptions WHERE customer_id=2",Integer.class)).isOne();
        assertThat(db.queryForObject("SELECT enabled FROM ops.privacy_preferences ORDER BY id DESC LIMIT 1",Boolean.class)).isFalse();
        assertThat(db.queryForObject("SELECT action FROM ops.privacy_effects ORDER BY id DESC LIMIT 1",String.class)).isEqualTo("DISABLE_PUSH");
    }
    @Test void optionalPushRequiresPublishedVersionAndQueuedDeliveryChecksWithdrawal() throws Exception {
        doReturn(new Notice(true,"test-v1","TEST-ONLY","TEST-ONLY","test@example.test","TEST-ONLY",List.of())).when(notices).current();
        seedPush(); String owner=customerToken(1);
        http.perform(call(put("/api/notifications/preferences")).header("Authorization","Bearer "+owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true,\"noticeVersion\":\"old-version\"}")).andExpect(status().isConflict());
        http.perform(call(put("/api/notifications/preferences")).header("Authorization","Bearer "+owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true,\"noticeVersion\":\"test-v1\"}")).andExpect(status().isOk());
        assertThat(pushes.canSend(1L)).isTrue();
        assertThat(pushes.canSend(2L)).isFalse();
        http.perform(call(delete("/api/notifications/subscriptions")).header("Authorization","Bearer "+owner)).andExpect(status().isNoContent());
        assertThat(pushes.canSend(1L)).isFalse();
    }
    @Test void deviceUnsubscribeIsIdempotentAndDoesNotRemoveOtherCustomersEndpoint() throws Exception {
        seedPush(); String owner=customerToken(1);
        postJson("/api/notifications/unsubscribe-device",Map.of("endpoint","https://fcm.googleapis.com/two"),owner,204);
        assertThat(db.queryForObject("SELECT count(*) FROM ops.push_subscriptions",Integer.class)).isEqualTo(2);
        postJson("/api/notifications/unsubscribe-device",Map.of("endpoint","https://fcm.googleapis.com/one"),owner,204);
        postJson("/api/notifications/unsubscribe-device",Map.of("endpoint","https://fcm.googleapis.com/one"),owner,204);
        assertThat(db.queryForObject("SELECT count(*) FROM ops.push_subscriptions",Integer.class)).isOne();
    }
    @Test void undefinedRetentionPoliciesRemainDryAndExecutionRequiresSeparateApproval() throws Exception {
        String handler=officer();
        JsonNode preview=postJson("/api/privacy/admin/retention/preview",Map.of(),handler,200);
        assertThat(preview.size()).isEqualTo(RetentionCategory.values().length);
        preview.forEach(row->assertThat(row.path("status").asText()).isEqualTo("POLICY_UNDEFINED"));
        postJson("/api/privacy/admin/retention/run",Map.of(),handler,503);
    }
    @Test void retentionBatchesOnlyExpiredRowsAndPreservesHeldSubjects() throws Exception {
        seedPush();
        db.update("UPDATE ops.push_subscriptions SET last_used_at=NOW()-interval '10 days'");
        String handler=officer();
        privacy.getRetentionDays().put(RetentionCategory.PUSH_SUBSCRIPTIONS,1);
        privacy.setRetentionBatchSize(1);
        JsonNode preview=postJson("/api/privacy/admin/retention/preview",Map.of(),handler,200);
        assertThat(preview.toString()).contains("\"eligible\":2","\"processed\":0");
        assertThat(db.queryForObject("SELECT count(*) FROM ops.push_subscriptions",Integer.class)).isEqualTo(2);
        UUID key=db.queryForObject("SELECT privacy_key FROM fidelity.customers WHERE id=2",UUID.class);
        postJson("/api/privacy/admin/holds",Map.of("subjectType","CUSTOMER","subjectKey",key,"reasonCode","LEGAL_REVIEW",
                "evidenceRef","TEST-ONLY","reviewAt",java.time.Instant.now().plusSeconds(86400).toString()),handler,200);
        privacy.setRetentionEnabled(true); privacy.setRetentionDryRun(false); privacy.setRetentionApprovalRef("TEST-ONLY");
        postJson("/api/privacy/admin/retention/run",Map.of(),handler,200);
        assertThat(db.queryForObject("SELECT count(*) FROM ops.push_subscriptions WHERE customer_id=1",Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM ops.push_subscriptions WHERE customer_id=2",Integer.class)).isOne();
        postJson("/api/privacy/admin/retention/run",Map.of(),handler,200);
        assertThat(db.queryForObject("SELECT count(*) FROM ops.push_subscriptions",Integer.class)).isOne();
    }
    @Test void restorationJournalIsSignedAndRejectsTamperingBeforeMutatingRestoredData() throws Exception {
        String owner=customerToken(1);
        postJson("/api/auth/logout",Map.of(),owner,204);
        JsonNode entries=read("/api/privacy/admin/restoration-journal",officer());
        JournalEntry entry=json.treeToValue(entries.get(0),JournalEntry.class);
        assertThat(entry.signature()).isNotBlank();
        privacy.setRestorationQuarantine(true);
        assertThat(journal.replayOffline(entry,true)).isFalse();
        assertThatThrownBy(()->journal.replayOffline(new JournalEntry(entry.origin(),entry.id(),entry.subjectType(),
                2,entry.subjectKey(),entry.action(),entry.merchantId(),entry.occurredAt(),entry.signature()),true)).isInstanceOf(IllegalArgumentException.class);
        assertThat(db.queryForObject("SELECT token_version FROM fidelity.customers WHERE id=1",Long.class)).isZero();
        assertThatThrownBy(()->journal.replayOffline(entry,false)).isInstanceOf(IllegalStateException.class);
        approveDestructiveForTest(); privacy.setRestoreDryRun(false);
        assertThat(journal.replayOffline(entry,false)).isTrue();
        assertThat(journal.replayOffline(entry,false)).isFalse();
        assertThat(db.queryForObject("SELECT token_version FROM fidelity.customers WHERE id=1",Long.class)).isOne();
        db.execute("TRUNCATE ops.revoked_tokens");
        privacy.setRestorationQuarantine(false);
        getStatus("/api/privacy/me/profile",owner,401);
    }
    @Test void restoredDatabaseReappliesExternalWithdrawalAndDoesNotResurrectPush() throws Exception {
        seedPush(); String owner=customerToken(1);
        http.perform(call(delete("/api/notifications/subscriptions")).header("Authorization","Bearer "+owner)).andExpect(status().isNoContent());
        JournalEntry entry=json.treeToValue(read("/api/privacy/admin/restoration-journal",officer()).get(0),JournalEntry.class);
        // Simulate a snapshot that predates the withdrawal.
        db.update("UPDATE fidelity.customers SET push_enabled=true,push_updated_at=NOW()-interval '1 day' WHERE id=1");
        db.update("INSERT INTO ops.push_subscriptions(customer_id,endpoint,p256dh,auth) VALUES(1,'https://fcm.googleapis.com/stale','stale','stale')");
        privacy.setRestorationQuarantine(true); approveDestructiveForTest(); privacy.setRestoreDryRun(false);
        assertThat(journal.replayOffline(entry,false)).isTrue();
        assertThat(db.queryForObject("SELECT push_enabled FROM fidelity.customers WHERE id=1",Boolean.class)).isFalse();
        assertThat(db.queryForObject("SELECT count(*) FROM ops.push_subscriptions WHERE customer_id=1",Integer.class)).isZero();
    }
    @Test void quarantineRejectsLoginAndNormalApiTrafficButAllowsHealthCheck() throws Exception {
        privacy.setRestorationQuarantine(true);
        try {
            postJson("/api/auth/login",Map.of("email","admin@example.test","password",PASSWORD),null,503);
            getStatus("/api/privacy/notice",null,503);
            getStatus("/api/cards/customer/1",customerToken(1),503);
            getStatus("/actuator/health",null,200);
        } finally { privacy.setRestorationQuarantine(false); }
    }

    @Test void staffErasureDetachesRefreshChainsAndCashierIdentityWithoutDeletingCustomers() throws Exception {
        seedCards();
        JsonNode original=login("cashier@example.test");
        JsonNode rotated=postJson("/api/auth/refresh",Map.of("refreshToken",original.path("refreshToken").asText()),null,200);
        String token=rotated.path("token").asText();
        reset.issue(staff.findById(3L).orElseThrow());
        String protocol=request(token,"ERASURE","ACCOUNT",null), handler=officer();
        review(protocol,handler,"CLAIM","",200); approveDestructiveForTest();
        review(protocol,handler,"ERASE","ERASE_ACCOUNT",200);
        getStatus("/api/privacy/me/profile",token,401);
        assertThat(db.queryForObject("SELECT count(*) FROM ops.refresh_tokens WHERE staff_user_id=3",Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM ops.password_reset_tokens WHERE staff_user_id=3",Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM core.staff_user_merchants WHERE staff_user_id=3",Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM fidelity.stamps WHERE cashier_id IS NULL",Integer.class)).isEqualTo(3);
        assertThat(db.queryForObject("SELECT count(*) FROM fidelity.cards",Integer.class)).isEqualTo(3);
        assertThat(db.queryForObject("SELECT email FROM core.staff_users WHERE id=3",String.class)).endsWith("@invalid.example");
    }
    @Test void everyRetentionCategoryExecutesAgainstRealPostgresWithoutDeletingReferencedQrTokens() throws Exception {
        seedCards(); seedPush(); String handler=officer();
        login("admin@example.test");
        db.update("UPDATE ops.refresh_tokens SET expires_at=NOW()-interval '10 days' WHERE staff_user_id=1");
        reset.issue(staff.findById(1L).orElseThrow());
        db.update("UPDATE ops.password_reset_tokens SET expires_at=NOW()-interval '10 days'");
        db.update("INSERT INTO ops.idempotency_keys(key,created_at) VALUES('old-test',NOW()-interval '10 days')");
        db.update("INSERT INTO ops.stamp_tokens(type,id_ref,nonce,exp_at,signature) VALUES"
                +"('CUSTOMER_QR',1,gen_random_uuid(),NOW()-interval '10 days','test-only'),"
                +"('CUSTOMER_QR',2,gen_random_uuid(),NOW()-interval '10 days','test-only')");
        db.update("UPDATE fidelity.stamps SET token_id=1,when_at=NOW()-interval '10 days',ip='192.0.2.1',user_agent='test' WHERE id=1");
        db.update("UPDATE ops.push_subscriptions SET last_used_at=NOW()-interval '10 days'");
        db.update("INSERT INTO ops.audit_log(action,actor_type,occurred_at,success,ip,user_agent) VALUES"
                +"('PUSH_NOTIFICATION_TRIGGERED','SYSTEM',NOW()-interval '10 days',true,'192.0.2.1','test')");
        String protocol=request(customerToken(1),"ACCESS","ACCOUNT",null);
        review(protocol,handler,"CLAIM","",200); review(protocol,handler,"COMPLETE","",200);
        db.update("UPDATE ops.privacy_requests SET completed_at=NOW()-interval '10 days' WHERE id=?",UUID.fromString(protocol));
        for (var category:RetentionCategory.values()) privacy.getRetentionDays().put(category,1);
        JsonNode preview=postJson("/api/privacy/admin/retention/preview",Map.of(),handler,200);
        assertThat(preview.size()).isEqualTo(RetentionCategory.values().length);
        privacy.setRetentionEnabled(true); privacy.setRetentionDryRun(false); privacy.setRetentionApprovalRef("TEST-ONLY");
        postJson("/api/privacy/admin/retention/run",Map.of(),handler,200);
        assertThat(db.queryForObject("SELECT count(*) FROM ops.stamp_tokens",Integer.class)).isOne();
        assertThat(db.queryForObject("SELECT token_id FROM fidelity.stamps WHERE id=1",Long.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT ip IS NULL AND user_agent IS NULL FROM fidelity.stamps WHERE id=1",Boolean.class)).isTrue();
        assertThat(db.queryForObject("SELECT count(*) FROM ops.idempotency_keys",Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM ops.password_reset_tokens",Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM ops.refresh_tokens",Integer.class)).isEqualTo(1); // Officer session is still valid.
        assertThat(db.queryForObject("SELECT details IS NULL AND response IS NULL FROM ops.privacy_requests WHERE id=?",Boolean.class,UUID.fromString(protocol))).isTrue();
    }
    @Test void offlineRestoreProfileStartsWithoutWebOrBootstrapAgainstDisposablePostgres(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        java.nio.file.Path file=java.nio.file.Files.createFile(directory.resolve("empty.ndjson"));
        try (var context=new org.springframework.boot.builder.SpringApplicationBuilder(com.app.carimbai.CarimbaiApplication.class)
                .web(org.springframework.boot.WebApplicationType.NONE).run(
                        "--spring.profiles.active=test,privacy-restore",
                        "--spring.datasource.url="+PG.getJdbcUrl("postgres","postgres"),
                        "--spring.datasource.username=postgres","--spring.datasource.password=postgres",
                        "--carimbai.privacy.restoration-quarantine=true",
                        "--carimbai.privacy.restore-journal-path="+file,
                        "--carimbai.privacy.restore-expected-entries=0","--carimbai.privacy.restore-expected-last-id=0")) {
            assertThat(context.isActive()).isFalse();
        }
        assertThat(db.queryForObject("SELECT count(*) FROM core.staff_users",Integer.class)).isEqualTo(3);
    }

    private void exactKeys(JsonNode value,String... keys) {
        assertThat(value.size()).isEqualTo(keys.length);
        for (String key:keys) assertThat(value.has(key)).as("JSON field %s",key).isTrue();
    }

    @Test void boot4PreservesLoginRefreshCardQrAndPrivacyJsonContracts() throws Exception {
        seedCards();
        JsonNode session=login("admin@example.test");
        exactKeys(session,"token","refreshToken","staffId","merchantId","role","email","merchants");
        assertThat(session.path("staffId").isIntegralNumber()).isTrue();
        assertThat(session.path("merchants").isArray()).isTrue();
        JsonNode refreshed=postJson("/api/auth/refresh",Map.of("refreshToken",session.path("refreshToken").asText()),null,200);
        exactKeys(refreshed,"token","refreshToken");
        String owner=customerToken(1);
        JsonNode cards=json.readTree(http.perform(call(get("/api/cards/customer/1")).header("Authorization","Bearer "+owner))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        exactKeys(cards,"cards");
        exactKeys(cards.path("cards").get(0),"cardId","programId","programName","merchantName","rewardName","stampsCount","stampsNeeded","status","hasReward");
        JsonNode qr=json.readTree(http.perform(call(get("/api/qr/1")).header("Authorization","Bearer "+owner))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        exactKeys(qr,"type","idRef","nonce","exp","sig");
        assertThat(qr.path("exp").isIntegralNumber()).isTrue();
        assertThat(qr.path("nonce").asText()).matches("[0-9a-f-]{36}");
        JsonNode opened=postJson("/api/privacy/me/requests",Map.of("type","ACCESS","scope","ACCOUNT"),owner,201);
        exactKeys(opened,"id","type","scope","merchantId","status","details","response","createdAt","updatedAt","dueAt","completedAt");
        assertThat(java.time.Instant.parse(opened.path("createdAt").asText())).isBefore(java.time.Instant.now().plusSeconds(1));
        assertThat(opened.path("completedAt").isNull()).isTrue();
        JsonNode error=postJson("/api/auth/login",Map.of("email","absent@example.test","password",PASSWORD),null,401);
        exactKeys(error,"error"); assertThat(error.path("error").asText()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test void boot4NativeJsonbAndFullStampRedeemFlowRemainCompatible() throws Exception {
        db.update("INSERT INTO fidelity.cards(program_id,customer_id) VALUES(1,1)");
        db.update("UPDATE fidelity.programs SET rule_total_stamps=1 WHERE id=1");
        String operator=login("admin@example.test").path("token").asText(), owner=customerToken(1);
        http.perform(call(put("/api/merchants/1/locations/1")).header("Authorization","Bearer "+operator)
                .contentType(MediaType.APPLICATION_JSON).content("{\"flags\":{\"requirePinOnRedeem\":false,\"enableScanA\":true,\"enableScanB\":false}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.flags.requirePinOnRedeem").value(false));
        assertThat(db.queryForObject("SELECT jsonb_typeof(flags) FROM core.locations WHERE id=1",String.class)).isEqualTo("object");
        assertThat(db.queryForObject("SELECT flags->>'requirePinOnRedeem' FROM core.locations WHERE id=1",String.class)).isEqualTo("false");
        JsonNode qr=json.readTree(http.perform(call(get("/api/qr/1")).header("Authorization","Bearer "+owner))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var payload=Map.of("type","CUSTOMER_QR","payload",Map.of("cardId",1,"nonce",qr.path("nonce").asText(),"exp",qr.path("exp").asLong(),"sig",qr.path("sig").asText()));
        String idem=UUID.randomUUID().toString();
        byte[] body=json.writeValueAsBytes(payload);
        String stamp=http.perform(call(post("/api/stamp")).header("Authorization","Bearer "+operator)
                .header("Idempotency-Key",idem).header("X-Location-Id","1").header("User-Agent","test-only")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode stamped=json.readTree(stamp);
        exactKeys(stamped,"ok","cardId","stamps","needed","rewardIssued");
        assertThat(stamped.path("stamps").asInt()).isEqualTo(1); assertThat(stamped.path("rewardIssued").asBoolean()).isTrue();
        http.perform(call(post("/api/stamp")).header("Authorization","Bearer "+operator).header("Idempotency-Key",idem)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        assertThat(db.queryForObject("SELECT jsonb_typeof(details) FROM ops.audit_log WHERE action='STAMP_APPLIED'",String.class)).isEqualTo("object");
        assertThat(db.queryForObject("SELECT (details->>'stamps')::int FROM ops.audit_log WHERE action='STAMP_APPLIED'",Integer.class)).isEqualTo(1);
        JsonNode redeemQr=json.readTree(http.perform(call(get("/api/cards/1/redeem-qr")).header("Authorization","Bearer "+owner))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode redeemed=postJson("/api/redeem",Map.of("cardId",1,"locationId",1,"redeemQr",
                Map.of("cardId",1,"nonce",redeemQr.path("nonce").asText(),"exp",redeemQr.path("exp").asLong(),"sig",redeemQr.path("sig").asText())),operator,200);
        exactKeys(redeemed,"ok","rewardId","cardId","stampsAfter");
        assertThat(redeemed.path("stampsAfter").asInt()).isZero();
        assertThat(db.queryForObject("SELECT status FROM fidelity.cards WHERE id=1",String.class)).isEqualTo("ACTIVE");
        assertThat(db.queryForObject("SELECT count(*) FROM fidelity.stamps",Integer.class)).isOne();
        assertThat(db.queryForObject("SELECT count(*) FROM fidelity.rewards",Integer.class)).isOne();
    }

    @Test void boot4MalformedNestedAndUtf8JsonRemainBoundedAndPrivate() throws Exception {
        String operator=login("admin@example.test").path("token").asText();
        String nested="{\"name\":\"private-test\",\"email\":\"new@example.test\",\"providerId\":"+"[".repeat(1100)+"0"+"]".repeat(1100)+"}";
        http.perform(call(post("/api/customers/login-or-register")).header("Authorization","Bearer "+operator)
                .contentType(MediaType.APPLICATION_JSON).content(nested)).andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-test"))));
        byte[] invalidUtf8=new byte[]{'{','"','e','m','a','i','l','"',':','"',(byte)0xc3,0x28,'"','}'};
        http.perform(call(post("/api/auth/forgot-password")).contentType(MediaType.APPLICATION_JSON).content(invalidUtf8))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
        http.perform(call(patch("/api/privacy/me/profile")).header("Authorization","Bearer "+customerToken(1))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("name","Jo\u00e3o de teste"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Jo\u00e3o de teste"));
    }

    @Test void boot4SpringdocDescribesTheExistingApiWithoutExposingSecrets() throws Exception {
        String document=http.perform(call(get("/v3/api-docs"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode schema=json.readTree(document);
        assertThat(schema.path("paths").has("/api/auth/login")).isTrue();
        assertThat(schema.path("paths").has("/api/privacy/me/export")).isTrue();
        assertThat(schema.path("components").path("schemas").path("LoginResponse").path("properties").has("refreshToken")).isTrue();
        assertThat(document).doesNotContain("test-only-signing-key-at-least","test-only-hmac-key-not");
    }

    @Test void boot4AnotherInstanceRejectsPersistentlyRevokedCredentials() throws Exception {
        String revokedCustomer=customerToken(1);
        postJson("/api/auth/logout",Map.of(),revokedCustomer,204);
        try (var second=new org.springframework.boot.builder.SpringApplicationBuilder(com.app.carimbai.CarimbaiApplication.class)
                .run("--spring.profiles.active=test","--server.port=0",
                        "--spring.datasource.url="+PG.getJdbcUrl("postgres","postgres"),"--spring.datasource.username=postgres",
                        "--spring.datasource.password=postgres","--carimbai.bootstrap.admin.email=","--carimbai.bootstrap.admin.password=")) {
            int secondPort=((org.springframework.boot.web.server.context.WebServerApplicationContext)second).getWebServer().getPort();
            try (HttpClient client=HttpClient.newHttpClient()) {
                var rejected=HttpRequest.newBuilder(URI.create("http://localhost:"+secondPort+"/api/privacy/me/profile"))
                        .header("Authorization","Bearer "+revokedCustomer).GET().build();
                assertThat(client.send(rejected,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
                var accepted=HttpRequest.newBuilder(URI.create("http://localhost:"+secondPort+"/api/privacy/me/profile"))
                        .header("Authorization","Bearer "+customerToken(2)).GET().build();
                assertThat(client.send(accepted,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
            }
        }
    }

    @Test void boot4ConcurrentUseOfOneQrCannotCreateTwoStamps() throws Exception {
        db.update("INSERT INTO fidelity.cards(program_id,customer_id) VALUES(1,1)");
        String operator=login("admin@example.test").path("token").asText();
        JsonNode qr=json.readTree(http.perform(call(get("/api/qr/1")).header("Authorization","Bearer "+customerToken(1)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        byte[] body=json.writeValueAsBytes(Map.of("type","CUSTOMER_QR","payload",Map.of("cardId",1,"nonce",qr.path("nonce").asText(),
                "exp",qr.path("exp").asLong(),"sig",qr.path("sig").asText())));
        CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        try (var pool=Executors.newFixedThreadPool(2)) {
            Callable<Integer> consume=()-> {
                ready.countDown();
                if (!start.await(10,TimeUnit.SECONDS)) throw new AssertionError("QR race did not start");
                return http.perform(call(post("/api/stamp")).header("Authorization","Bearer "+operator)
                        .header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(body))
                        .andReturn().getResponse().getStatus();
            };
            var first=pool.submit(consume); var second=pool.submit(consume);
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue(); start.countDown();
            var statuses=List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));
            assertThat(statuses.stream().filter(code->code==200).count()).isOne();
            assertThat(statuses.stream().filter(code->code!=200).findFirst().orElseThrow()).isIn(409,429);
        }
        assertThat(db.queryForObject("SELECT count(*) FROM fidelity.stamps",Integer.class)).isOne();
        assertThat(db.queryForObject("SELECT count(*) FROM ops.stamp_tokens",Integer.class)).isOne();
        assertThat(db.queryForObject("SELECT stamps_count FROM fidelity.cards WHERE id=1",Integer.class)).isEqualTo(1);
    }

    @Test void boot4ProductionProfileKeepsDocsSqlAndCorsRestricted(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path logs) throws Exception {
        String previousLogDir=System.getProperty("LOG_DIR");
        System.setProperty("LOG_DIR",logs.toString());
        try (var production=new org.springframework.boot.builder.SpringApplicationBuilder(com.app.carimbai.CarimbaiApplication.class)
                .run("--spring.profiles.active=test,prod","--server.port=0",
                        "--spring.datasource.url="+PG.getJdbcUrl("postgres","postgres"),"--spring.datasource.username=postgres",
                        "--spring.datasource.password=postgres","--spring.datasource.hikari.data-source-properties.sslmode=disable",
                        "--carimbai.bootstrap.admin.email=","--carimbai.bootstrap.admin.password=")) {
            // TLS is disabled only for this disposable local PostgreSQL, never in production configuration.
            assertThat(production.getEnvironment().getProperty("springdoc.api-docs.enabled",Boolean.class)).isFalse();
            assertThat(production.getEnvironment().getProperty("springdoc.swagger-ui.enabled",Boolean.class)).isFalse();
            var entityManager=production.getBean(jakarta.persistence.EntityManagerFactory.class);
            assertThat(production.getEnvironment().getProperty("spring.jpa.show-sql",Boolean.class)).isFalse();
            var factory=entityManager.unwrap(org.hibernate.engine.spi.SessionFactoryImplementor.class);
            assertThat(factory.getJdbcServices().getSqlStatementLogger().isLogToStdout()).isFalse();
            assertThat(logs.resolve("application.log")).isRegularFile();
            assertThat(logs.resolve("audit.log")).isRegularFile();
            int productionPort=((org.springframework.boot.web.server.context.WebServerApplicationContext)production).getWebServer().getPort();
            try (HttpClient client=HttpClient.newHttpClient()) {
                for (String path:List.of("/v3/api-docs","/swagger-ui/index.html")) {
                    var request=HttpRequest.newBuilder(URI.create("http://localhost:"+productionPort+path)).GET().build();
                    assertThat(client.send(request,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(404);
                }
                for (String origin:List.of("https://carimbai-app.vercel.app","http://localhost:5173")) {
                    var request=HttpRequest.newBuilder(URI.create("http://localhost:"+productionPort+"/api/cards/customer/1"))
                            .header("Origin",origin).header("Access-Control-Request-Method","GET")
                            .header("Access-Control-Request-Headers","Authorization").method("OPTIONS",HttpRequest.BodyPublishers.noBody()).build();
                    assertThat(client.send(request,HttpResponse.BodyHandlers.ofString()).statusCode())
                            .isEqualTo(origin.startsWith("https:") ? 200 : 403);
                }
            }
        } finally {
            // Release test file handles before JUnit cleans the temporary directory on Windows.
            var logging=(ch.qos.logback.classic.LoggerContext)org.slf4j.LoggerFactory.getILoggerFactory();
            var applicationLog=logging.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).getAppender("APP_FILE");
            var auditLog=logging.getLogger("audit").getAppender("AUDIT_FILE");
            if (applicationLog!=null) applicationLog.stop();
            if (auditLog!=null) auditLog.stop();
            if (previousLogDir==null) System.clearProperty("LOG_DIR");
            else System.setProperty("LOG_DIR",previousLogDir);
        }
    }
}
