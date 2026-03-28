package com.app.carimbai.services;

import com.app.carimbai.dtos.ProgramItemDto;
import com.app.carimbai.dtos.admin.CreateProgramRequest;
import com.app.carimbai.dtos.admin.UpdateProgramRequest;
import com.app.carimbai.models.core.Merchant;
import com.app.carimbai.models.fidelity.Program;
import com.app.carimbai.repositories.ProgramRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProgramService {

    private final ProgramRepository programRepository;
    private final MerchantService merchantService;

    public Program createProgram(Long merchantId, @Valid CreateProgramRequest request) {

        Merchant merchant = merchantService.findById(merchantId);

        var program = Program.builder()
                .merchant(merchant)
                .name(request.name())
                .ruleTotalStamps(request.ruleTotalStamps() != null ? request.ruleTotalStamps() : 10)
                .rewardName(request.rewardName())
                .expirationDays(request.expirationDays())
                .description(request.description())
                .startAt(request.startAt())
                .endAt(request.endAt())
                .category(request.category())
                .terms(request.terms())
                .imageUrl(request.imageUrl())
                .sortOrder(request.sortOrder() != null ? request.sortOrder() : 0)
                .active(true)
                .build();

        return programRepository.save(program);
    }

    public Program updateProgram(Long merchantId, Long programId, @Valid UpdateProgramRequest request) {
        Program program = programRepository.findById(programId)
                .orElseThrow(() -> new IllegalArgumentException("Program not found with id: " + programId));

        if (!program.getMerchant().getId().equals(merchantId)) {
            throw new IllegalArgumentException("Program does not belong to merchant " + merchantId);
        }

        if (request.name() != null) program.setName(request.name());
        if (request.ruleTotalStamps() != null) program.setRuleTotalStamps(request.ruleTotalStamps());
        if (request.rewardName() != null) program.setRewardName(request.rewardName());
        if (request.expirationDays() != null) program.setExpirationDays(request.expirationDays());
        if (request.description() != null) program.setDescription(request.description());
        if (request.active() != null) program.setActive(request.active());
        if (request.startAt() != null) program.setStartAt(request.startAt());
        if (request.endAt() != null) program.setEndAt(request.endAt());
        if (request.category() != null) program.setCategory(request.category());
        if (request.terms() != null) program.setTerms(request.terms());
        if (request.imageUrl() != null) program.setImageUrl(request.imageUrl());
        if (request.sortOrder() != null) program.setSortOrder(request.sortOrder());

        return programRepository.save(program);
    }

    public List<Program> findActiveByMerchantId(Long merchantId) {
        return programRepository.findByMerchantIdAndActiveTrue(merchantId);
    }

    public List<Program> findByMerchantId(Long merchantId) {
        return programRepository.findByMerchantId(merchantId);
    }

    public Program findById(Long programId) {
        return programRepository.findById(programId)
                .orElseThrow(() -> new IllegalArgumentException("Program not found with id: " + programId));
    }

    public List<Program> listPrograms(Long merchantId, boolean activeOnly) {

        return  activeOnly
                ? findActiveByMerchantId(merchantId)
                : findByMerchantId(merchantId);
    }
}
