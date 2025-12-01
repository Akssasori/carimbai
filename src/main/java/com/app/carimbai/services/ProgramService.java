package com.app.carimbai.services;

import com.app.carimbai.dtos.admin.CreateProgramRequest;
import com.app.carimbai.models.core.Merchant;
import com.app.carimbai.models.fidelity.Program;
import com.app.carimbai.repositories.ProgramRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
                .ruleTotalStamps(request.ruleTotalStamps())
                .rewardName(request.rewardName())
                .expirationDays(request.expirationDays())
                .build();

        return programRepository.save(program);

    }

    public Program findById(Long programId) {
        return programRepository.findById(programId)
                .orElseThrow(() -> new IllegalArgumentException("Program not found with id: " + programId));
    }
}
