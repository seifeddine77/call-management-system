package com.pfe.callmgmt.decision.client;

import com.pfe.callmgmt.decision.dto.RulesEvaluateRequest;
import com.pfe.callmgmt.decision.dto.RulesEvaluateResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "rules-service")
public interface RulesServiceClient {

    @PostMapping("/api/rules/evaluate")
    RulesEvaluateResponse evaluate(@RequestBody RulesEvaluateRequest request);
}
