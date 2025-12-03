package com.app.carimbai.config;

import com.app.carimbai.enums.StaffRole;
import com.app.carimbai.models.core.Location;
import com.app.carimbai.models.core.Merchant;
import com.app.carimbai.models.core.StaffUser;
import com.app.carimbai.models.fidelity.Card;
import com.app.carimbai.models.fidelity.Customer;
import com.app.carimbai.models.fidelity.Program;
import com.app.carimbai.repositories.CardRepository;
import com.app.carimbai.repositories.CustomerRepository;
import com.app.carimbai.repositories.LocationRepository;
import com.app.carimbai.repositories.MerchantRepository;
import com.app.carimbai.repositories.ProgramRepository;
import com.app.carimbai.repositories.StaffUserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;


@Component
@Profile("dev") // só roda com profile dev
public class DevDataSeeder implements CommandLineRunner {

    private final MerchantRepository merchantRepo;
    private final LocationRepository locationRepo;
    private final ProgramRepository programRepo;
    private final StaffUserRepository staffRepo;
    private final CustomerRepository customerRepo;
    private final CardRepository cardRepo;
    private final BCryptPasswordEncoder encoder;

    public DevDataSeeder(
            MerchantRepository merchantRepo,
            LocationRepository locationRepo,
            ProgramRepository programRepo,
            StaffUserRepository staffRepo,
            CustomerRepository customerRepo,
            CardRepository cardRepo,
            BCryptPasswordEncoder encoder
    ) {
        this.merchantRepo = merchantRepo;
        this.locationRepo = locationRepo;
        this.programRepo = programRepo;
        this.staffRepo = staffRepo;
        this.customerRepo = customerRepo;
        this.cardRepo = cardRepo;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) throws Exception {

        if (merchantRepo.count() > 0) {
            return; // já tem dados, não faz nada
        }

        // MERCHANT
        Merchant m = new Merchant();
        m.setName("Restaurante Demo");
        m.setDocument("00.000.000/0001-00");
        m = merchantRepo.save(m);

        // LOCATION
        Location loc = new Location();
        loc.setName("Unidade Centro");
        loc.setAddress("Rua Demo, 123");
        loc.setMerchant(m);
        locationRepo.save(loc);

        // PROGRAM
        Program p = new Program();
        p.setMerchant(m);
        p.setName("Cartão 10 Selos");
        p.setRuleTotalStamps(10);
        p.setRewardName("Almoço grátis");
        p = programRepo.save(p);

        // STAFF (CASHIER)
        StaffUser cashier = new StaffUser();
        cashier.setMerchant(m);
        cashier.setEmail("caixa@demo.com");
        cashier.setPasswordHash(encoder.encode("senhasegura123")); // senha de demo
        cashier.setRole(StaffRole.CASHIER);
        cashier.setActive(true);
        staffRepo.save(cashier);

        // CUSTOMER
        Customer c = new Customer();
        c.setName("Lucas");
        c.setEmail("cliente@demo.com");
        c.setPhone("11999999999");
        c = customerRepo.save(c);

        // CARD
        Card card = new Card();
        card.setProgram(p);
        card.setCustomer(c);
        card.setStampsCount(5); // começa com 5 selos, se quiser
        cardRepo.save(card);

        System.out.println("✅ DevDataSeeder: dados de demo criados.");

    }
}
