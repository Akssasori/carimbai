package com.app.carimbai.services;

import com.app.carimbai.dtos.staff.DashboardMetricsResponse;
import com.app.carimbai.dtos.staff.RecentRewardItem;
import com.app.carimbai.dtos.staff.RecentStampItem;
import com.app.carimbai.models.fidelity.Reward;
import com.app.carimbai.models.fidelity.Stamp;
import com.app.carimbai.repositories.CardRepository;
import com.app.carimbai.repositories.RewardRepository;
import com.app.carimbai.repositories.StampRepository;
import com.app.carimbai.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StaffDashboardService {

    private final StampRepository stampRepo;
    private final RewardRepository rewardRepo;
    private final CardRepository cardRepo;

    @Value("${carimbai.timezone:America/Sao_Paulo}")
    private String timezone;

    @Value("${carimbai.stamps-needed:10}")
    private Integer defaultStampsNeeded;

    @Transactional(readOnly = true)
    public DashboardMetricsResponse getMetrics() {
        Long merchantId = SecurityUtils.getActiveMerchantId();
        OffsetDateTime startOfDay = startOfTodayInConfiguredZone();

        long stampsToday = stampRepo.countByMerchantSince(merchantId, startOfDay);
        long rewardsToday = rewardRepo.countByMerchantSince(merchantId, startOfDay);
        long totalCustomers = cardRepo.countDistinctCustomersByMerchant(merchantId);

        return new DashboardMetricsResponse(
                stampsToday,
                rewardsToday,
                totalCustomers,
                OffsetDateTime.now()
        );
    }

    @Transactional(readOnly = true)
    public List<RecentStampItem> getRecentStamps(int limit) {
        Long merchantId = SecurityUtils.getActiveMerchantId();
        int safeLimit = clampLimit(limit);

        List<Stamp> stamps = stampRepo.findRecentByMerchant(merchantId, PageRequest.of(0, safeLimit));

        return stamps.stream().map(this::toStampItem).toList();
    }

    @Transactional(readOnly = true)
    public List<RecentRewardItem> getRecentRewards(int limit) {
        Long merchantId = SecurityUtils.getActiveMerchantId();
        int safeLimit = clampLimit(limit);

        List<Reward> rewards = rewardRepo.findRecentByMerchant(merchantId, PageRequest.of(0, safeLimit));

        return rewards.stream().map(this::toRewardItem).toList();
    }

    private RecentStampItem toStampItem(Stamp s) {
        var card = s.getCard();
        var program = card.getProgram();
        var customer = card.getCustomer();
        Integer stampsNeeded = program.getRuleTotalStamps() != null
                ? program.getRuleTotalStamps()
                : defaultStampsNeeded;

        return new RecentStampItem(
                s.getId(),
                card.getId(),
                customer.getId(),
                customer.getName(),
                program.getName(),
                card.getStampsCount(),
                stampsNeeded,
                s.getCashier() != null ? s.getCashier().getEmail() : null,
                s.getLocation() != null ? s.getLocation().getName() : null,
                s.getWhenAt()
        );
    }

    private RecentRewardItem toRewardItem(Reward r) {
        var card = r.getCard();
        var program = card.getProgram();
        var customer = card.getCustomer();

        return new RecentRewardItem(
                r.getId(),
                card.getId(),
                customer.getId(),
                customer.getName(),
                program.getName(),
                program.getRewardName(),
                r.getCashier() != null ? r.getCashier().getEmail() : null,
                r.getLocation() != null ? r.getLocation().getName() : null,
                r.getIssuedAt()
        );
    }

    private OffsetDateTime startOfTodayInConfiguredZone() {
        ZoneId zone = ZoneId.of(timezone);
        return LocalDate.now(zone).atStartOfDay(zone).toOffsetDateTime();
    }

    private int clampLimit(int requested) {
        if (requested <= 0) return 10;
        return Math.min(requested, 50);
    }
}
