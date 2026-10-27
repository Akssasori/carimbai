package com.app.carimbai.services;

import com.app.carimbai.config.PrivacyProperties;
import com.app.carimbai.dtos.privacy.PrivacyDtos.*;
import com.app.carimbai.enums.*;
import com.app.carimbai.mappers.PrivacyMapper;
import com.app.carimbai.models.PrivacyRequest;
import com.app.carimbai.repositories.*;
import com.app.carimbai.security.PrivacyAccess;
import com.app.carimbai.security.PrivacyAccess.Subject;
import com.app.carimbai.security.audit.*;
import com.app.carimbai.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor
public class PrivacyService {
    public static final List<String> EXPORT_SECTIONS=List.of("profile","cards","stamps","rewards","sessions",
            "recovery","push","preferences","requests","audit","privacy-events");
    private final PrivacyAccess access;
    private final PrivacyDataRepository data;
    private final PrivacyRequestRepository requests;
    private final CustomerRepository customers;
    private final StaffUserRepository staff;
    private final PrivacyMapper mapper;
    private final PrivacyProperties settings;
    private final AuditSecurityService audit;

    @Transactional(readOnly=true)
    public Profile profile() {
        Subject subject=access.current();
        return subject.type()==AuditActorType.CUSTOMER
                ? mapper.profile(customers.findById(subject.id()).orElseThrow())
                : mapper.profile(staff.findById(subject.id()).orElseThrow());
    }
    @Transactional
    public Profile correct(UpdateProfile update) {
        Subject subject=access.current();
        if (subject.type()!=AuditActorType.CUSTOMER) throw new AccessDeniedException("Use a verified correction request for staff identity");
        var customer=customers.findForUpdate(subject.id()).orElseThrow();
        customer.setName(update.name().trim());
        customer.setPhone(update.phone()==null || update.phone().isBlank() ? null : update.phone().trim());
        customer.setProfileUpdatedAt(Instant.now());
        customers.saveAndFlush(customer);
        data.event(subject,null,subject.id(),"PROFILE_CORRECTED");
        audit.success(AuditEvent.PRIVACY_PROFILE_CORRECTED,Map.of("subjectType",subject.type().name(),"subjectId",subject.id()));
        return mapper.profile(customer);
    }
    @Transactional
    public RequestView open(OpenRequest input) {
        Subject subject=access.current();
        data.lockSubject(subject);
        if (input.scope()==PrivacyScope.MERCHANT) data.requireMerchantRelationship(subject,input.merchantId());
        else if (input.merchantId()!=null) throw new IllegalArgumentException("Account requests must not specify a merchant");
        if (data.openCount(subject)>=5) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Too many open privacy requests");
        var request=PrivacyRequest.builder().subjectType(subject.type()).subjectId(subject.id()).subjectKey(subject.key())
                .type(input.type()).scope(input.scope()).merchantId(input.merchantId()).details(input.details()).build();
        request=requests.saveAndFlush(request);
        data.event(subject,request.getId(),subject.id(),"REQUEST_RECEIVED");
        audit.success(AuditEvent.PRIVACY_REQUEST,Map.of("protocol",request.getId().toString(),"operation","RECEIVED"));
        return mapper.view(request);
    }
    @Transactional(readOnly=true)
    public List<RequestView> ownRequests(int page,int size) {
        Subject subject=access.current();
        return requests.findBySubjectTypeAndSubjectKeyOrderByCreatedAtDesc(subject.type(),subject.key(),PageRequest.of(page,size))
                .stream().map(mapper::view).toList();
    }
    @Transactional(readOnly=true)
    public RequestView ownRequest(UUID id) {
        Subject subject=access.current();
        var request=requests.findById(id).filter(r->r.getSubjectType()==subject.type() && r.getSubjectKey().equals(subject.key()))
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        return mapper.view(request);
    }
    @Transactional(readOnly=true)
    public ExportPage export(String section,long after,int limit) {
        if (!EXPORT_SECTIONS.contains(section)) throw new IllegalArgumentException("Unknown export section");
        Subject subject=access.current();
        var rows=data.export(subject,section,after,limit+1);
        boolean more=rows.size()>limit;
        List<Map<String,Object>> items=new ArrayList<>(rows.subList(0,Math.min(limit,rows.size())));
        Long next=more ? ((Number)items.getLast().get("id")).longValue() : null;
        audit.success(AuditEvent.PRIVACY_EXPORT,Map.of("subjectType",subject.type().name(),"subjectId",subject.id(),"section",section));
        return new ExportPage(section,items,next,List.of("CREDENTIALS_AND_SUBSCRIPTION_KEYS","OTHER_PEOPLES_DATA",
                "LOG_FILES_AND_EXTERNAL_PROVIDERS_REQUIRE_VERIFIED_REQUEST","SOCIAL_IDENTIFIERS_REQUIRE_VERIFIED_REQUEST"));
    }
    @Transactional(readOnly=true)
    public List<RequestView> queue(int page,int size) {
        access.requireOfficer();
        return requests.findAllByOrderByCreatedAtAsc(PageRequest.of(page,size)).stream().map(mapper::view).toList();
    }
    @Transactional(readOnly=true)
    public Map<String,Object> preview(UUID id) {
        access.requireOfficer();
        PrivacyRequest request=requests.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        Subject subject=subject(request);
        return Map.of("protocol",id,"scope",request.getScope(),"subjectType",subject.type(),"subjectKey",subject.key(),
                "affected",data.impact(subject,request.getMerchantId()),
                "preservationHold",data.hasHold(subject),"destructiveActionsEnabled",settings.isDestructiveActionsApproved()
                        && !settings.getDestructiveApprovalRef().isBlank(),"dryRun",true);
    }
    @Transactional
    public RequestView review(UUID id,Review input) {
        Long actor=access.requireOfficer();
        PrivacyRequest request=requests.findForUpdate(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (request.getStatus()==PrivacyRequestStatus.COMPLETED || request.getStatus()==PrivacyRequestStatus.REJECTED) {
            throw new IllegalStateException("Request is already closed");
        }
        if (input.action()==PrivacyAction.CLAIM) {
            if (request.getAssignedStaffId()!=null && !request.getAssignedStaffId().equals(actor)) throw new IllegalStateException("Request has another handler");
            request.setAssignedStaffId(actor);
            if (input.dueAt()!=null) {
                if (!input.dueAt().isAfter(Instant.now())) throw new IllegalArgumentException("Due date must be in the future");
                request.setDueAt(input.dueAt());
            }
            if (request.getStatus()==PrivacyRequestStatus.RECEIVED) request.setStatus(PrivacyRequestStatus.IN_REVIEW);
        } else {
            if (!actor.equals(request.getAssignedStaffId())) throw new AccessDeniedException("Claim this request before reviewing it");
            if (input.evidenceRef()==null || input.evidenceRef().isBlank() || input.response()==null || input.response().isBlank()) {
                throw new IllegalArgumentException("Evidence reference and public response are required");
            }
            switch(input.action()) {
                case RESTRICT,ERASE -> apply(request,input);
                case REJECT -> request.setStatus(PrivacyRequestStatus.REJECTED);
                case RESPOND -> { /* Keep open until completion is explicitly evidenced. */ }
                case COMPLETE -> {
                    if ((request.getType()==PrivacyRequestType.ERASURE || request.getType()==PrivacyRequestType.RESTRICTION)
                            && request.getStatus()!=PrivacyRequestStatus.APPLIED_LOCALLY) {
                        throw new IllegalStateException("Apply the reviewed action before confirming external completion");
                    }
                    request.setStatus(PrivacyRequestStatus.COMPLETED);
                }
                default -> throw new IllegalArgumentException("Unsupported action");
            }
            request.setResponse(input.response());
            request.setDecisionRef(input.evidenceRef());
            if (request.getStatus()==PrivacyRequestStatus.COMPLETED || request.getStatus()==PrivacyRequestStatus.REJECTED) request.setCompletedAt(Instant.now());
        }
        request.setUpdatedAt(Instant.now());
        requests.saveAndFlush(request);
        data.event(subject(request),id,actor,"REQUEST_"+input.action().name());
        audit.success(AuditEvent.PRIVACY_REQUEST,Map.of("protocol",id.toString(),"operation",input.action().name(),"actorStaffId",actor));
        return mapper.view(request);
    }
    private void apply(PrivacyRequest request,Review input) {
        if (!settings.isDestructiveActionsApproved() || settings.getDestructiveApprovalRef().isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Approved retention and rights policy required");
        }
        if (request.getStatus()!=PrivacyRequestStatus.IN_REVIEW) throw new IllegalStateException("Action already applied or not reviewed");
        boolean erase=input.action()==PrivacyAction.ERASE;
        if (request.getType()!=(erase ? PrivacyRequestType.ERASURE : PrivacyRequestType.RESTRICTION)) {
            throw new IllegalArgumentException("Action does not match the requested right");
        }
        String confirmation=(erase ? "ERASE_" : "RESTRICT_")+request.getScope().name();
        if (!confirmation.equals(input.confirmation())) throw new IllegalArgumentException("Explicit scope confirmation required");
        Subject subject=subject(request);
        data.lockSubject(subject);
        if (data.hasHold(subject)) throw new IllegalStateException("Preservation exception requires restricted review before this action");
        data.requireReplaceableStaff(subject);
        data.revokeAndRestrict(subject,request.getMerchantId());
        if (erase) {
            data.erase(subject,request.getMerchantId());
            if (request.getScope()==PrivacyScope.ACCOUNT) request.setDetails(null);
        }
        data.effect(subject,request.getId(),confirmation,request.getMerchantId());
        request.setStatus(PrivacyRequestStatus.APPLIED_LOCALLY);
    }
    @Transactional
    public UUID hold(Hold input) {
        Long actor=access.requireOfficer();
        Subject subject=data.findSubject(input.subjectType(),input.subjectKey());
        data.lockSubject(subject);
        UUID id=data.hold(subject,actor,input.reasonCode(),input.evidenceRef(),input.reviewAt());
        data.event(subject,null,actor,"PRESERVATION_HOLD_CREATED");
        return id;
    }
    @Transactional(readOnly=true) public List<Map<String,Object>> holds(int limit) { access.requireOfficer(); return data.holds(limit); }
    @Transactional public void releaseHold(UUID id,String evidenceRef) {
        Long actor=access.requireOfficer(); Subject subject=data.heldSubject(id); data.lockSubject(subject);
        data.releaseHold(id,actor,evidenceRef); data.event(subject,null,actor,"PRESERVATION_HOLD_RELEASED");
    }
    public Subject subject(PrivacyRequest request) { return new Subject(request.getSubjectType(),request.getSubjectId(),request.getSubjectKey()); }
}
