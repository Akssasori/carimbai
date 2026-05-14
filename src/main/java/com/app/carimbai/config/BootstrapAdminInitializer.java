package com.app.carimbai.config;

import com.app.carimbai.enums.StaffRole;
import com.app.carimbai.models.core.Merchant;
import com.app.carimbai.models.core.StaffUser;
import com.app.carimbai.models.core.StaffUserMerchant;
import com.app.carimbai.repositories.MerchantRepository;
import com.app.carimbai.repositories.StaffUserMerchantRepository;
import com.app.carimbai.repositories.StaffUserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cria o primeiro admin do sistema na inicializacao, se as variaveis de ambiente
 * CARIMBAI_BOOTSTRAP_ADMIN_EMAIL e CARIMBAI_BOOTSTRAP_ADMIN_PASSWORD estiverem definidas.
 *
 * Comportamento idempotente: se ja existe staff com o email configurado, nao faz nada.
 *
 * Sobre o merchant:
 * - Se CARIMBAI_BOOTSTRAP_MERCHANT_ID estiver definida, o admin sera linkado ao merchant
 *   ja existente com aquele ID.
 * - Caso contrario, cria um merchant com nome de CARIMBAI_BOOTSTRAP_MERCHANT_NAME
 *   (default "Default Merchant").
 *
 * Resolve o chicken-and-egg de prod: como POST /api/staff-users exige ADMIN logado,
 * sem este bootstrap o unico jeito de criar o primeiro admin seria INSERT manual no banco.
 */
@Component
@RequiredArgsConstructor
public class BootstrapAdminInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final StaffUserRepository staffRepo;
    private final StaffUserMerchantRepository staffMerchantRepo;
    private final MerchantRepository merchantRepo;
    private final BCryptPasswordEncoder encoder;

    @Value("${carimbai.bootstrap.admin.email:}")
    private String bootstrapAdminEmail;

    @Value("${carimbai.bootstrap.admin.password:}")
    private String bootstrapAdminPassword;

    @Value("${carimbai.bootstrap.merchant.id:}")
    private String bootstrapMerchantId;

    @Value("${carimbai.bootstrap.merchant.name:Default Merchant}")
    private String bootstrapMerchantName;

    @Override
    @Transactional
    public void run(String... args) {
        if (isBlank(bootstrapAdminEmail) || isBlank(bootstrapAdminPassword)) {
            log.debug("Bootstrap admin nao configurado (CARIMBAI_BOOTSTRAP_ADMIN_EMAIL/PASSWORD vazios). Pulando.");
            return;
        }

        if (staffRepo.findByEmail(bootstrapAdminEmail).isPresent()) {
            log.info("Bootstrap: admin {} ja existe, nao faz nada.", bootstrapAdminEmail);
            return;
        }

        Merchant merchant = resolveOrCreateMerchant();

        StaffUser admin = StaffUser.builder()
                .email(bootstrapAdminEmail)
                .passwordHash(encoder.encode(bootstrapAdminPassword))
                .active(true)
                .build();
        admin = staffRepo.save(admin);

        StaffUserMerchant link = StaffUserMerchant.builder()
                .staffUser(admin)
                .merchant(merchant)
                .role(StaffRole.ADMIN)
                .active(true)
                .isDefault(true)
                .build();
        staffMerchantRepo.save(link);

        log.warn("Bootstrap admin criado: email={}, staffId={}, merchantId={}, merchantName={}. " +
                        "Recomenda-se rotacionar a senha no primeiro login.",
                bootstrapAdminEmail, admin.getId(), merchant.getId(), merchant.getName());
    }

    private Merchant resolveOrCreateMerchant() {
        if (!isBlank(bootstrapMerchantId)) {
            Long id;
            try {
                id = Long.parseLong(bootstrapMerchantId.trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException(
                        "CARIMBAI_BOOTSTRAP_MERCHANT_ID nao e um Long valido: " + bootstrapMerchantId);
            }
            return merchantRepo.findById(id)
                    .orElseThrow(() -> new IllegalStateException(
                            "CARIMBAI_BOOTSTRAP_MERCHANT_ID=" + id + " nao foi encontrado no banco"));
        }

        Merchant m = Merchant.builder()
                .name(bootstrapMerchantName)
                .active(true)
                .build();
        m = merchantRepo.save(m);
        log.info("Bootstrap: criado merchant id={} name={}", m.getId(), m.getName());
        return m;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
