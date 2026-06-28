package com.app.carimbai.config;

import com.app.carimbai.enums.CardStatus;
import com.app.carimbai.enums.StaffRole;
import com.app.carimbai.models.core.Location;
import com.app.carimbai.models.core.Merchant;
import com.app.carimbai.models.core.StaffUser;
import com.app.carimbai.models.core.StaffUserMerchant;
import com.app.carimbai.models.fidelity.Card;
import com.app.carimbai.models.fidelity.Customer;
import com.app.carimbai.models.fidelity.Program;
import com.app.carimbai.repositories.CardRepository;
import com.app.carimbai.repositories.CustomerRepository;
import com.app.carimbai.repositories.LocationRepository;
import com.app.carimbai.repositories.MerchantRepository;
import com.app.carimbai.repositories.ProgramRepository;
import com.app.carimbai.repositories.StaffUserMerchantRepository;
import com.app.carimbai.repositories.StaffUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Popula o banco com cenários típicos para smoke-test manual dos FIXes.
 * Roda só em profile {@code local} ou {@code stg}; é idempotente (não toca em
 * banco já populado). Senha padrão respeitada pela política do FIX-16 (10+ chars).
 *
 * <p>Senhas/PINs são fixos, conhecidos e óbvios — NÃO usar em prod.
 */
@Component
@Profile({"local", "stg"})
@Slf4j
public class DevDataSeeder implements CommandLineRunner {

    private static final String DEFAULT_PASSWORD = "senhasegura123";
    private static final String DEFAULT_PIN = "1234";

    private final MerchantRepository merchantRepo;
    private final LocationRepository locationRepo;
    private final ProgramRepository programRepo;
    private final StaffUserRepository staffRepo;
    private final StaffUserMerchantRepository staffMerchantRepo;
    private final CustomerRepository customerRepo;
    private final CardRepository cardRepo;
    private final BCryptPasswordEncoder encoder;

    public DevDataSeeder(
            MerchantRepository merchantRepo,
            LocationRepository locationRepo,
            ProgramRepository programRepo,
            StaffUserRepository staffRepo,
            StaffUserMerchantRepository staffMerchantRepo,
            CustomerRepository customerRepo,
            CardRepository cardRepo,
            BCryptPasswordEncoder encoder
    ) {
        this.merchantRepo = merchantRepo;
        this.locationRepo = locationRepo;
        this.programRepo = programRepo;
        this.staffRepo = staffRepo;
        this.staffMerchantRepo = staffMerchantRepo;
        this.customerRepo = customerRepo;
        this.cardRepo = cardRepo;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) throws Exception {

        if (merchantRepo.count() > 0) {
            log.info("DevDataSeeder: banco já populado — nada a fazer.");
            return;
        }

        // ---------- 2 MERCHANTS (cross-tenant test) ----------
        Merchant m1 = merchantRepo.save(buildMerchant("Restaurante Demo 1", "00.000.000/0001-00"));
        Merchant m2 = merchantRepo.save(buildMerchant("Restaurante Demo 2", "11.111.111/0001-11"));

        // ---------- LOCATIONS ----------
        Location loc1 = new Location();
        loc1.setName("Unidade Centro");
        loc1.setAddress("Rua Demo, 123");
        loc1.setMerchant(m1);
        locationRepo.save(loc1);

        Location loc2 = new Location();
        loc2.setName("Unidade Norte");
        loc2.setAddress("Av. Demo, 999");
        loc2.setMerchant(m2);
        locationRepo.save(loc2);

        // ---------- PROGRAMS ----------
        Program p1 = new Program();
        p1.setMerchant(m1);
        p1.setName("Cartão 10 Selos");
        p1.setRuleTotalStamps(10);
        p1.setRewardName("Almoço grátis");
        p1 = programRepo.save(p1);

        Program p2 = new Program();
        p2.setMerchant(m2);
        p2.setName("Cartão 5 Selos");
        p2.setRuleTotalStamps(5);
        p2.setRewardName("Sobremesa grátis");
        p2 = programRepo.save(p2);

        // ---------- STAFFS ----------
        // PLATFORM_ADMIN (FIX-03) — único que pode createMerchant e POST /api/customers.
        StaffUser platformAdmin = staffRepo.save(StaffUser.builder()
                .email("platform@demo.com")
                .passwordHash(encoder.encode(DEFAULT_PASSWORD))
                .active(true)
                .platformAdmin(true)
                .build());
        // PLATFORM_ADMIN ainda precisa de pelo menos 1 vínculo ativo para passar no FIX-08
        // (login exige no_merchant_link == false) — vinculamos ao merchant 1 como ADMIN.
        link(platformAdmin, m1, StaffRole.ADMIN, true);

        // ADMIN do merchant 1 (cross-tenant: NÃO consegue mexer no merchant 2).
        StaffUser admin1 = staffRepo.save(buildStaff("admin1@demo.com"));
        link(admin1, m1, StaffRole.ADMIN, true);

        // ADMIN do merchant 2 (espelho do admin1 para validar isolamento).
        StaffUser admin2 = staffRepo.save(buildStaff("admin2@demo.com"));
        link(admin2, m2, StaffRole.ADMIN, true);

        // CASHIER do merchant 1 com PIN definido (testa /redeem com FIX-23/FIX-04).
        StaffUser cashier1 = StaffUser.builder()
                .email("caixa1@demo.com")
                .passwordHash(encoder.encode(DEFAULT_PASSWORD))
                .pinHash(encoder.encode(DEFAULT_PIN))
                .active(true)
                .build();
        cashier1 = staffRepo.save(cashier1);
        link(cashier1, m1, StaffRole.CASHIER, true);

        // ---------- CUSTOMERS (FIX-02 Fase C — escopo de dono) ----------
        Customer c1 = customerRepo.save(buildCustomer("Lucas", "cliente1@demo.com", "11999999999"));
        Customer c2 = customerRepo.save(buildCustomer("Ana",   "cliente2@demo.com", "11988888888"));

        // ---------- CARDS ----------
        // Usa new Card() (não builder) — defaults dos campos (version=0L, status=ACTIVE)
        // dependem do init no construtor; @Builder do Lombok os ignora.

        // c1 em p1 — 5 selos (cartela em andamento → testa /qr e /stamp).
        Card card1 = new Card();
        card1.setProgram(p1);
        card1.setCustomer(c1);
        card1.setStampsCount(5);
        cardRepo.save(card1);

        // c2 em p1 — pronto pra resgate (FIX-23 / fluxo de redeem).
        Card card2 = new Card();
        card2.setProgram(p1);
        card2.setCustomer(c2);
        card2.setStampsCount(10);
        card2.setStatus(CardStatus.READY_TO_REDEEM);
        cardRepo.save(card2);

        // c1 em p2 (cross-merchant) — útil pra ver listagem do mesmo cliente em 2 merchants.
        Card card3 = new Card();
        card3.setProgram(p2);
        card3.setCustomer(c1);
        card3.setStampsCount(2);
        cardRepo.save(card3);

        printCredentials();
    }

    private Merchant buildMerchant(String name, String document) {
        return Merchant.builder().name(name).document(document).active(true).build();
    }

    private StaffUser buildStaff(String email) {
        return StaffUser.builder()
                .email(email)
                .passwordHash(encoder.encode(DEFAULT_PASSWORD))
                .active(true)
                .build();
    }

    private Customer buildCustomer(String name, String email, String phone) {
        Customer c = new Customer();
        c.setName(name);
        c.setEmail(email);
        c.setPhone(phone);
        return c;
    }

    private void link(StaffUser staff, Merchant merchant, StaffRole role, boolean isDefault) {
        staffMerchantRepo.save(StaffUserMerchant.builder()
                .staffUser(staff)
                .merchant(merchant)
                .role(role)
                .active(true)
                .isDefault(isDefault)
                .build());
    }

    private void printCredentials() {
        log.info("""

                ┌──────────────────────────── DEV DATA SEEDED ────────────────────────────┐
                │ Senha padrão de staff:  {}                                  │
                │ PIN padrão do caixa:    {}                                              │
                │                                                                          │
                │ STAFF                                                                    │
                │   • platform@demo.com  → PLATFORM_ADMIN + ADMIN do merchant 1            │
                │                          (único que pode POST /api/merchants /customers) │
                │   • admin1@demo.com    → ADMIN do merchant 1                             │
                │   • admin2@demo.com    → ADMIN do merchant 2  (para cross-tenant)        │
                │   • caixa1@demo.com    → CASHIER do merchant 1 (PIN: {})              │
                │                                                                          │
                │ CUSTOMERS (sem auth — onboarding em prod é social-login)                 │
                │   • cliente1@demo.com  (Lucas)  → card em p1 (5/10), card em p2 (2/5)    │
                │   • cliente2@demo.com  (Ana)    → card em p1 (10/10, READY_TO_REDEEM)    │
                │                                                                          │
                │ Endpoints chave:                                                         │
                │   POST /api/auth/login       (staff)                                     │
                │   POST /api/customers/social-login   (customer — exige token social)    │
                │   GET  /api/cards/customer/{}   (ROLE_CUSTOMER, posse — SEC-001)         │
                │   POST /api/redeem           (cashier — exige PIN)                       │
                └──────────────────────────────────────────────────────────────────────────┘
                """,
                DEFAULT_PASSWORD, DEFAULT_PIN, DEFAULT_PIN, "{id}");
    }
}
