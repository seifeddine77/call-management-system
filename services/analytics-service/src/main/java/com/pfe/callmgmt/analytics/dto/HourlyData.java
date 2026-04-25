package com.pfe.callmgmt.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HourlyData {
    private String label; // ex: "2025-01-15 14:00"
    private int count;
}
