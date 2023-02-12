package com.project.transaction;

import lombok.Data;

@Data

public class TransactionRequest {

    private String fromUser;
    private String toUser;

    private int amount;

    private String purpose;
}
