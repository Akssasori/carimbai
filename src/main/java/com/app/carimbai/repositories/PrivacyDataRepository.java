package com.app.carimbai.repositories;

import com.app.carimbai.enums.*;
import com.app.carimbai.security.PrivacyAccess.Subject;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/** Explicit column allowlists: privacy exports never serialize JPA entities or credentials. */
@Repository @RequiredArgsConstructor
public class PrivacyDataRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private Map<String,Object> params(Subject subject) {
        return Map.of("id",subject.id(),"key",subject.key(),"type",subject.type().name());
    }
    public void lockSubject(Subject subject) {
        String table=subject.type()==AuditActorType.CUSTOMER ? "fidelity.customers" : "core.staff_users";
        if (jdbc.queryForList("SELECT id FROM "+table+" WHERE id=:id AND privacy_key=:key FOR UPDATE",params(subject)).isEmpty()) {
            throw new AccessDeniedException("Subject identity mismatch");
        }
    }
    public Subject findSubject(AuditActorType type,UUID key) {
        if (type!=AuditActorType.CUSTOMER && type!=AuditActorType.STAFF) throw new IllegalArgumentException("Invalid subject type");
        String table=type==AuditActorType.CUSTOMER ? "fidelity.customers" : "core.staff_users";
        var rows=jdbc.queryForList("SELECT id FROM "+table+" WHERE privacy_key=:key",Map.of("key",key));
        if (rows.isEmpty()) throw new IllegalArgumentException("Subject not found");
        return new Subject(type,((Number)rows.getFirst().get("id")).longValue(),key);
    }
    public Optional<Subject> findSubjectById(AuditActorType type,Long id) {
        String table=type==AuditActorType.CUSTOMER ? "fidelity.customers" : "core.staff_users";
        var rows=jdbc.queryForList("SELECT privacy_key FROM "+table+" WHERE id=:id",Map.of("id",id));
        return rows.isEmpty() ? Optional.empty() : Optional.of(new Subject(type,id,(UUID)rows.getFirst().get("privacy_key")));
    }
    public void requireMerchantRelationship(Subject subject,Long merchantId) {
        if (merchantId==null) throw new IllegalArgumentException("Merchant is required for merchant scope");
        String sql=subject.type()==AuditActorType.CUSTOMER
                ? "SELECT EXISTS(SELECT 1 FROM fidelity.cards c JOIN fidelity.programs p ON p.id=c.program_id WHERE c.customer_id=:id AND p.merchant_id=:merchant)"
                : "SELECT EXISTS(SELECT 1 FROM core.staff_user_merchants WHERE staff_user_id=:id AND merchant_id=:merchant)";
        if (!Boolean.TRUE.equals(jdbc.queryForObject(sql,Map.of("id",subject.id(),"merchant",merchantId),Boolean.class))) {
            throw new AccessDeniedException("No relationship with the requested merchant");
        }
    }
    public long openCount(Subject subject) {
        return Objects.requireNonNull(jdbc.queryForObject("SELECT count(*) FROM ops.privacy_requests WHERE subject_type=:type AND subject_key=:key "
                +"AND status NOT IN ('COMPLETED','REJECTED')",params(subject),Long.class));
    }
    public boolean hasHold(Subject subject) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM ops.privacy_holds "
                +"WHERE subject_type=:type AND subject_key=:key AND active)",params(subject),Boolean.class));
    }
    public void event(Subject subject,UUID requestId,Long actor,String event) {
        Map<String,Object> args=new HashMap<>(params(subject));
        args.put("request",requestId); args.put("actor",actor); args.put("event",event);
        jdbc.update("INSERT INTO ops.privacy_events(request_id,subject_type,subject_key,actor_id,event) "
                +"VALUES (:request,:type,:key,:actor,:event)",args);
    }
    public UUID hold(Subject subject,Long actor,String reason,String evidence,Instant reviewAt) {
        UUID hold=UUID.randomUUID();
        jdbc.update("INSERT INTO ops.privacy_holds(id,subject_type,subject_key,reason_code,evidence_ref,created_by,review_at) "
                +"VALUES (:hold,:type,:key,:reason,:evidence,:actor,:review)",Map.of("hold",hold,"type",subject.type().name(),
                "key",subject.key(),"reason",reason,"evidence",evidence,"actor",actor,"review",Timestamp.from(reviewAt)));
        return hold;
    }
    public List<Map<String,Object>> holds(int limit) {
        return jdbc.queryForList("SELECT id,subject_type,subject_key,reason_code,evidence_ref,created_by,active,created_at,review_at "
                +"FROM ops.privacy_holds ORDER BY created_at DESC LIMIT :limit",Map.of("limit",limit));
    }
    public Subject heldSubject(UUID id) {
        var rows=jdbc.queryForList("SELECT subject_type,subject_key FROM ops.privacy_holds WHERE id=:id",Map.of("id",id));
        if (rows.isEmpty()) throw new IllegalArgumentException("Hold not found");
        return findSubject(AuditActorType.valueOf((String)rows.getFirst().get("subject_type")),(UUID)rows.getFirst().get("subject_key"));
    }
    public void releaseHold(UUID id,Long actor,String evidence) {
        if (jdbc.update("UPDATE ops.privacy_holds SET active=false,released_at=NOW(),released_by=:actor,release_ref=:evidence "
                +"WHERE id=:id AND active",Map.of("id",id,"actor",actor,"evidence",evidence))==0) {
            throw new IllegalArgumentException("Active hold not found");
        }
    }
    public void invalidateSessions(Subject subject) {
        String table=subject.type()==AuditActorType.CUSTOMER ? "fidelity.customers" : "core.staff_users";
        jdbc.update("UPDATE "+table+" SET token_version=token_version+1 WHERE id=:id AND privacy_key=:key",params(subject));
        if (subject.type()==AuditActorType.STAFF) {
            jdbc.update("UPDATE ops.refresh_tokens SET revoked_at=COALESCE(revoked_at,NOW()) WHERE staff_user_id=:id",params(subject));
            jdbc.update("UPDATE ops.password_reset_tokens SET used_at=COALESCE(used_at,NOW()) WHERE staff_user_id=:id",params(subject));
        }
    }
    public boolean claimReplay(String origin,long id,String digest) {
        int inserted=jdbc.update("INSERT INTO ops.privacy_replayed_effects(origin,effect_id,digest) VALUES (:origin,:id,:digest) "
                +"ON CONFLICT DO NOTHING",Map.of("origin",origin,"id",id,"digest",digest));
        if (inserted==0 && !digest.equals(jdbc.queryForObject("SELECT digest FROM ops.privacy_replayed_effects WHERE origin=:origin AND effect_id=:id",
                Map.of("origin",origin,"id",id),String.class))) throw new IllegalStateException("Conflicting journal entry");
        if (inserted>0) jdbc.queryForObject("SELECT setval(pg_get_serial_sequence('ops.privacy_effects','id'),"
                +"GREATEST(:id,(SELECT last_value FROM ops.privacy_effects_id_seq)),true)",Map.of("id",id),Long.class);
        return inserted>0;
    }
    public boolean pushUpdatedAfter(Subject subject,Instant at) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT COALESCE(push_updated_at>:at,false) FROM fidelity.customers WHERE id=:id AND privacy_key=:key",
                Map.of("id",subject.id(),"key",subject.key(),"at",Timestamp.from(at)),Boolean.class));
    }
    public void effect(Subject subject,UUID request,String action,Long merchant) {
        Map<String,Object> args=new HashMap<>(params(subject));
        args.put("request",request); args.put("action",action); args.put("merchant",merchant);
        jdbc.update("INSERT INTO ops.privacy_effects(request_id,subject_type,subject_id,subject_key,action,merchant_id) "
                +"VALUES (:request,:type,:id,:key,:action,:merchant)",args);
    }
    public List<Map<String,Object>> effects(long after,int limit) {
        return jdbc.queryForList("SELECT id,request_id,subject_type,subject_id,subject_key,action,merchant_id,occurred_at "
                +"FROM ops.privacy_effects WHERE id>:after ORDER BY id LIMIT :limit",Map.of("after",after,"limit",limit));
    }
    public List<Map<String,Object>> export(Subject subject,String section,long after,int limit) {
        boolean customer=subject.type()==AuditActorType.CUSTOMER;
        String sql=switch(section) {
            case "profile" -> customer
                    ? "SELECT id,name,email,phone,active,profile_updated_at FROM fidelity.customers WHERE id=:id AND privacy_key=:key"
                    : "SELECT id,email,active FROM core.staff_users WHERE id=:id AND privacy_key=:key";
            case "cards" -> customer
                    ? "SELECT c.id,p.name AS program,m.name AS merchant,c.stamps_count,c.status,c.created_at,c.expires_at "
                        +"FROM fidelity.cards c JOIN fidelity.programs p ON p.id=c.program_id JOIN core.merchants m ON m.id=p.merchant_id "
                        +"WHERE c.customer_id=:id"
                    : "SELECT id,merchant_id,role,active,is_default,created_at FROM core.staff_user_merchants WHERE staff_user_id=:id";
            case "stamps" -> customer
                    ? "SELECT s.id,s.card_id,s.when_at,s.source FROM fidelity.stamps s JOIN fidelity.cards c ON c.id=s.card_id WHERE c.customer_id=:id"
                    : "SELECT id,when_at,location_id,source,ip::text AS ip,user_agent FROM fidelity.stamps WHERE cashier_id=:id";
            case "rewards" -> customer
                    ? "SELECT r.id,r.card_id,r.issued_at,r.redeemed_at FROM fidelity.rewards r JOIN fidelity.cards c ON c.id=r.card_id WHERE c.customer_id=:id"
                    : "SELECT id,issued_at,redeemed_at,location_id FROM fidelity.rewards WHERE cashier_id=:id";
            case "sessions" -> customer ? "SELECT id,active,token_version FROM fidelity.customers WHERE id=:id AND privacy_key=:key"
                    : "SELECT id,merchant_id,issued_at,expires_at,revoked_at,ip::text AS ip,user_agent FROM ops.refresh_tokens WHERE staff_user_id=:id";
            case "recovery" -> customer ? "SELECT id FROM fidelity.customers WHERE false"
                    : "SELECT id,issued_at,expires_at,used_at,ip::text AS ip,user_agent FROM ops.password_reset_tokens WHERE staff_user_id=:id";
            case "push" -> customer ? "SELECT id,created_at,last_used_at FROM ops.push_subscriptions WHERE customer_id=:id"
                    : "SELECT id FROM core.staff_users WHERE false";
            case "preferences" -> "SELECT id,purpose,enabled,notice_version,occurred_at FROM ops.privacy_preferences WHERE subject_key=:key";
            case "requests" -> "SELECT id,type,scope,merchant_id,status,details,response,created_at,updated_at,completed_at "
                    +"FROM ops.privacy_requests WHERE subject_type=:type AND subject_key=:key";
            case "audit" -> "SELECT id,action,occurred_at,success FROM ops.audit_log WHERE actor_type=:type AND actor_id=:id";
            case "privacy-events" -> "SELECT id,request_id,event,occurred_at FROM ops.privacy_events WHERE subject_type=:type AND subject_key=:key";
            default -> throw new IllegalArgumentException("Unknown export section");
        };
        // UUID protocols use a separate authenticated request listing; cursor is numeric everywhere else.
        if (section.equals("requests")) {
            sql="SELECT row_number() OVER (ORDER BY created_at,id) AS id, id AS protocol,type,scope,merchant_id,status,details,response,created_at,updated_at,completed_at "
                    +"FROM ops.privacy_requests WHERE subject_type=:type AND subject_key=:key";
        }
        Map<String,Object> args=new HashMap<>(params(subject)); args.put("after",after); args.put("limit",limit);
        return jdbc.queryForList("SELECT * FROM ("+sql+") owned WHERE id>:after ORDER BY id LIMIT :limit",args);
    }
    public Map<String,Long> impact(Subject subject,Long merchant) {
        Map<String,Long> result=new LinkedHashMap<>();
        if (subject.type()==AuditActorType.CUSTOMER) {
            var args=new HashMap<>(params(subject)); args.put("merchant",merchant);
            String card="SELECT c.id FROM fidelity.cards c JOIN fidelity.programs p ON p.id=c.program_id "
                    +"WHERE c.customer_id=:id AND (CAST(:merchant AS bigint) IS NULL OR p.merchant_id=:merchant)";
            result.put("cards",jdbc.queryForObject("SELECT count(*) FROM ("+card+") x",args,Long.class));
            result.put("stamps",jdbc.queryForObject("SELECT count(*) FROM fidelity.stamps WHERE card_id IN ("+card+")",args,Long.class));
            result.put("rewards",jdbc.queryForObject("SELECT count(*) FROM fidelity.rewards WHERE card_id IN ("+card+")",args,Long.class));
        } else {
            result.put("merchantLinks",jdbc.queryForObject("SELECT count(*) FROM core.staff_user_merchants WHERE staff_user_id=:id",params(subject),Long.class));
        }
        return result;
    }
    public void revokeAndRestrict(Subject subject,Long merchant) {
        if (subject.type()==AuditActorType.CUSTOMER && merchant!=null) {
            jdbc.update("UPDATE fidelity.cards SET status='BLOCKED',version=version+1 WHERE customer_id=:id AND program_id "
                    +"IN (SELECT id FROM fidelity.programs WHERE merchant_id=:merchant)",Map.of("id",subject.id(),"merchant",merchant));
            return;
        }
        if (subject.type()==AuditActorType.STAFF && merchant!=null) throw new IllegalArgumentException("Staff membership changes require the merchant workflow");
        String table=subject.type()==AuditActorType.CUSTOMER ? "fidelity.customers" : "core.staff_users";
        jdbc.update("UPDATE "+table+" SET active=false,token_version=token_version+1 WHERE id=:id AND privacy_key=:key",params(subject));
        if (subject.type()==AuditActorType.CUSTOMER) disablePush(subject);
        else {
            jdbc.update("UPDATE ops.refresh_tokens SET revoked_at=COALESCE(revoked_at,NOW()) WHERE staff_user_id=:id",params(subject));
            jdbc.update("UPDATE ops.password_reset_tokens SET used_at=COALESCE(used_at,NOW()) WHERE staff_user_id=:id",params(subject));
        }
    }
    public void requireReplaceableStaff(Subject subject) {
        if (subject.type()!=AuditActorType.STAFF) return;
        jdbc.queryForList("SELECT m.id FROM core.merchants m JOIN core.staff_user_merchants l ON l.merchant_id=m.id "
                +"WHERE l.staff_user_id=:id ORDER BY m.id FOR UPDATE OF m",params(subject));
        var remaining=jdbc.queryForObject("SELECT count(*) FROM core.staff_user_merchants target WHERE target.staff_user_id=:id "
                +"AND target.active AND target.role='ADMIN' AND NOT EXISTS (SELECT 1 FROM core.staff_user_merchants backup "
                +"JOIN core.staff_users s ON s.id=backup.staff_user_id WHERE backup.merchant_id=target.merchant_id AND backup.active "
                +"AND backup.role='ADMIN' AND s.active AND s.id<>:id)",params(subject),Long.class);
        if (remaining!=null && remaining>0) throw new IllegalStateException("Replace the last active administrator before restricting this account");
    }
    public void erase(Subject subject,Long merchant) {
        if (subject.type()==AuditActorType.CUSTOMER) eraseCustomer(subject,merchant);
        else {
            if (merchant!=null) throw new IllegalArgumentException("Staff erasure must use account scope");
            jdbc.update("UPDATE fidelity.stamps SET cashier_id=NULL,ip=NULL,user_agent=NULL WHERE cashier_id=:id",params(subject));
            jdbc.update("UPDATE fidelity.rewards SET cashier_id=NULL WHERE cashier_id=:id",params(subject));
            // Self-referencing refresh chains are detached before deleting them.
            jdbc.update("UPDATE ops.refresh_tokens SET replaced_by_id=NULL WHERE staff_user_id=:id",params(subject));
            jdbc.update("DELETE FROM ops.refresh_tokens WHERE staff_user_id=:id",params(subject));
            jdbc.update("DELETE FROM ops.password_reset_tokens WHERE staff_user_id=:id",params(subject));
            jdbc.update("DELETE FROM core.staff_user_merchants WHERE staff_user_id=:id",params(subject));
            jdbc.update("UPDATE core.staff_users SET email=:email,password_hash='!ERASED',pin_hash=NULL,privacy_officer=false,platform_admin=false "
                    +"WHERE id=:id AND privacy_key=:key",Map.of("id",subject.id(),"key",subject.key(),"email","erased-"+subject.key()+"@invalid.example"));
            scrubActorAudit(subject);
        }
        if (merchant==null) {
            jdbc.update("UPDATE ops.privacy_requests SET details=NULL WHERE subject_type=:type AND subject_key=:key",params(subject));
        }
    }
    private void eraseCustomer(Subject subject,Long merchant) {
        Map<String,Object> args=new HashMap<>(params(subject)); args.put("merchant",merchant);
        String card="SELECT c.id FROM fidelity.cards c JOIN fidelity.programs p ON p.id=c.program_id WHERE c.customer_id=:id "
                +"AND (CAST(:merchant AS bigint) IS NULL OR p.merchant_id=:merchant)";
        jdbc.update("UPDATE ops.audit_log SET entity_id=NULL,details='{}'::jsonb,ip=NULL,user_agent=NULL WHERE "
                +"(entity_type='Card' AND entity_id IN ("+card+")) OR (entity_type='Stamp' AND entity_id IN "
                +"(SELECT id FROM fidelity.stamps WHERE card_id IN ("+card+")))",args);
        jdbc.update("DELETE FROM fidelity.stamps WHERE card_id IN ("+card+")",args);
        jdbc.update("DELETE FROM fidelity.rewards WHERE card_id IN ("+card+")",args);
        jdbc.update("DELETE FROM ops.stamp_tokens WHERE type IN ('CUSTOMER_QR','REDEEM_QR') AND id_ref IN ("+card+")",args);
        jdbc.update("DELETE FROM fidelity.cards WHERE id IN ("+card+")",args);
        if (merchant==null) {
            jdbc.update("DELETE FROM ops.stamp_tokens WHERE type='CUSTOMER_ENROLLMENT' AND id_ref=:id",params(subject));
            jdbc.update("UPDATE ops.audit_log SET entity_id=NULL,details='{}'::jsonb,ip=NULL,user_agent=NULL "
                    +"WHERE (entity_type='Customer' AND entity_id=:id) OR details->>'customerId'=:customerText",
                    Map.of("id",subject.id(),"customerText",subject.id().toString()));
            jdbc.update("UPDATE fidelity.customers SET name=NULL,email=NULL,phone=NULL,provider_id=NULL WHERE id=:id AND privacy_key=:key",params(subject));
            scrubActorAudit(subject);
        }
    }
    private void scrubActorAudit(Subject subject) {
        jdbc.update("UPDATE ops.audit_log SET actor_id=NULL,entity_id=NULL,details='{}'::jsonb,ip=NULL,user_agent=NULL "
                +"WHERE actor_type=:type AND actor_id=:id",params(subject));
    }
    public void disablePush(Subject subject) {
        if (subject.type()!=AuditActorType.CUSTOMER) throw new IllegalArgumentException("Customer preference required");
        jdbc.update("DELETE FROM ops.push_subscriptions WHERE customer_id=:id",params(subject));
        jdbc.update("UPDATE fidelity.customers SET push_enabled=false,push_updated_at=NOW() WHERE id=:id AND privacy_key=:key",params(subject));
        preference(subject,false,null);
    }
    public void preference(Subject subject,boolean enabled,String version) {
        Map<String,Object> args=new HashMap<>(params(subject)); args.put("enabled",enabled); args.put("version",version);
        jdbc.update("INSERT INTO ops.privacy_preferences(subject_key,purpose,enabled,notice_version) VALUES (:key,'LOYALTY_PUSH',:enabled,:version)",args);
    }
}
