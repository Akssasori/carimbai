package com.app.carimbai.security;

import java.util.*;

public final class AuditDataMinimizer {
    private AuditDataMinimizer() {}
    private static final Set<String> KEYS=Set.of("staffId","actorStaffId","newStaffId","targetStaffId","customerId",
            "subjectId","subjectType","cardId","programId","merchantId","activeMerchantId","locationId","rewardId",
            "stamps","needed","rewardIssued","withRedeemQr","kind","reason","role","protocol","operation","section","dryRun","affectedRows");
    public static Map<String,Object> project(Map<String,Object> input) {
        Map<String,Object> result=new LinkedHashMap<>();
        if (input==null) return result;
        input.forEach((key,value)-> {
            if (!KEYS.contains(key) || value==null) return;
            if (value instanceof Number || value instanceof Boolean) result.put(key,value);
            else if (value instanceof String text && text.matches("[A-Za-z0-9_:/.-]{1,80}")) result.put(key,text);
        });
        return result;
    }
}
