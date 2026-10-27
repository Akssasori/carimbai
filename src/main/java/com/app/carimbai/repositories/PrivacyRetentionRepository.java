package com.app.carimbai.repositories;

import com.app.carimbai.config.PrivacyProperties.RetentionCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository @RequiredArgsConstructor
public class PrivacyRetentionRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private record Plan(String table,String condition,String update) {}
    private Plan plan(RetentionCategory category) {
        // Mixed-subject forensic rows are conservatively preserved while any hold exists.
        String noHold=" AND NOT EXISTS(SELECT 1 FROM ops.privacy_holds WHERE active)";
        return switch(category) {
            case REFRESH_TOKENS -> new Plan("ops.refresh_tokens t","expires_at<:cutoff AND NOT EXISTS "
                    +"(SELECT 1 FROM ops.refresh_tokens predecessor WHERE predecessor.replaced_by_id=t.id)"
                    +" AND NOT EXISTS(SELECT 1 FROM ops.privacy_holds h JOIN core.staff_users s ON s.privacy_key=h.subject_key "
                    +"WHERE h.active AND h.subject_type='STAFF' AND s.id=t.staff_user_id)",null);
            case RESET_TOKENS -> new Plan("ops.password_reset_tokens t","expires_at<:cutoff AND NOT EXISTS "
                    +"(SELECT 1 FROM ops.privacy_holds h JOIN core.staff_users s ON s.privacy_key=h.subject_key "
                    +"WHERE h.active AND h.subject_type='STAFF' AND s.id=t.staff_user_id)",null);
            case QR_TOKENS -> new Plan("ops.stamp_tokens t","exp_at<:cutoff AND NOT EXISTS "
                    +"(SELECT 1 FROM fidelity.stamps s WHERE s.token_id=t.id)"+noHold,null);
            case IDEMPOTENCY_KEYS -> new Plan("ops.idempotency_keys","created_at<:cutoff"+noHold,null);
            case AUDIT_NETWORK_METADATA -> new Plan("ops.audit_log","occurred_at<:cutoff AND (ip IS NOT NULL OR user_agent IS NOT NULL)"+noHold,
                    "ip=NULL,user_agent=NULL");
            case STAMP_NETWORK_METADATA -> new Plan("fidelity.stamps","when_at<:cutoff AND (ip IS NOT NULL OR user_agent IS NOT NULL)"+noHold,
                    "ip=NULL,user_agent=NULL");
            case AUDIT_EVENTS -> new Plan("ops.audit_log","occurred_at<:cutoff"+noHold,null);
            case PUSH_SUBSCRIPTIONS -> new Plan("ops.push_subscriptions t","last_used_at<:cutoff AND NOT EXISTS "
                    +"(SELECT 1 FROM ops.privacy_holds h JOIN fidelity.customers c ON c.privacy_key=h.subject_key "
                    +"WHERE h.active AND h.subject_type='CUSTOMER' AND c.id=t.customer_id)",null);
            case CLOSED_REQUEST_CONTENT -> new Plan("ops.privacy_requests t","completed_at<:cutoff AND status IN ('COMPLETED','REJECTED') "
                    +"AND (details IS NOT NULL OR response IS NOT NULL) AND NOT EXISTS(SELECT 1 FROM ops.privacy_holds h "
                    +"WHERE h.active AND h.subject_type=t.subject_type AND h.subject_key=t.subject_key)","details=NULL,response=NULL");
        };
    }
    public Map<String,Object> batch(RetentionCategory category,Instant cutoff,int limit,boolean dryRun) {
        // Serialize destructive batches against creation/release of preservation holds.
        if (!dryRun) jdbc.getJdbcTemplate().execute("LOCK TABLE ops.privacy_holds IN SHARE MODE");
        Plan plan=plan(category);
        Map<String,Object> params=new HashMap<>(); params.put("cutoff",Timestamp.from(cutoff)); params.put("limit",limit);
        long eligible=Objects.requireNonNull(jdbc.queryForObject("SELECT count(*) FROM "+plan.table()+" WHERE "+plan.condition(),params,Long.class));
        List<?> ids=jdbc.queryForList("SELECT id FROM "+plan.table()+" WHERE "+plan.condition()+" ORDER BY id LIMIT :limit"
                +(dryRun ? "" : " FOR UPDATE SKIP LOCKED"),params).stream().map(row->row.get("id")).toList();
        int processed=0;
        if (!dryRun && !ids.isEmpty()) {
            params.put("ids",ids);
            String baseTable=plan.table().split(" ",2)[0];
            String sql=plan.update()==null ? "DELETE FROM "+baseTable : "UPDATE "+baseTable+" SET "+plan.update();
            processed=jdbc.update(sql+" WHERE id IN (:ids)",params);
        }
        return Map.of("category",category.name(),"eligible",eligible,"selected",ids.size(),"processed",processed,"cutoff",cutoff,"dryRun",dryRun);
    }
}
