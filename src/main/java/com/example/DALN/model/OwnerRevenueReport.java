
package com.example.DALN.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OwnerRevenueReport {

    private User owner;

    private Double grossRevenue = 0.0;
    private Double commissionAmount = 0.0;
    private Double payoutAmount = 0.0;

    private boolean transferCreated;
    private Long settlementId;
    private String settlementStatus;

    public OwnerRevenueReport(User owner) {
        this.owner = owner;
    }
}
